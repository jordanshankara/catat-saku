package app.catatuang.notify

import app.catatuang.engine.Defaults
import app.catatuang.engine.LedgerInput
import app.catatuang.engine.LedgerState
import app.catatuang.engine.TxType
import app.catatuang.engine.dailySavingAdvice
import app.catatuang.engine.hasSalary
import app.catatuang.engine.isFiveDaysBeforeMonthEnd
import app.catatuang.engine.pendingClosing
import app.catatuang.feature.fixed.unpaidFixed
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/* Isi notifikasi bab 9 — logika murni (tanpa android.*) supaya bisa dites. */

enum class Channel(val id: String, val label: String) {
    HARIAN("harian", "Harian"),
    PENGINGAT("pengingat", "Pengingat"),
    TUTUP_BUKU("tutup_buku", "Tutup Buku"),
}

/** Jadwal harian yang memicu pemeriksaan (bab 9). DAILY = jam notifikasi (default 22:00). */
enum class Slot(val hour: Int?) { DAILY(null), AT_0700(7), AT_0900(9), AT_1200(12), AT_2300(23) }

/** Tombol di notifikasi: buka app ke [route], atau aksi langsung [broadcast] tanpa membuka app. */
data class NotifAction(val label: String, val route: String? = null, val broadcast: String? = null)

data class NotifSpec(
    val id: Int,
    val channel: Channel,
    val title: String,
    val text: String,
    /** Rute saat notifikasi diketuk: home, picker, input/{id}, salary, closing, pay/{id}, report. */
    val route: String,
    val actions: List<NotifAction> = emptyList(),
)

const val ACTION_DAY_DONE = "app.catatuang.DAY_DONE"
const val ACTION_CHECKLIST_DONE = "app.catatuang.CHECKLIST_DONE"
const val PUBLIC_TEXT = "Catat Uang · ada info baru"

object NotifIds {
    const val DAILY = 1
    const val CLOSING = 2
    const val SALARY = 3
    const val CADANGAN = 4
    const val CHECKLIST = 5
    fun fixed(categoryId: Long) = 100 + categoryId.toInt()
}

/** Tanggal pengingat gajian: 29, atau hari terakhir bila bulannya lebih pendek (Februari). */
fun salaryReminderDay(ym: YearMonth): Int = minOf(29, ym.lengthOfMonth())

private fun dailyStatus(name: String, jatah: Long, used: Long): String = when {
    used < jatah -> "$name hemat ${rp(jatah - used)}"
    used == jatah -> "$name pas ${rp(jatah)}"
    else -> "$name lebih ${rp(used - jatah)}"
}

/**
 * Notifikasi harian (22:00): preview hari ini (R-21), hutang (+R-27), Saku Sisa; Minggu ditambah ringkasan
 * minggu. Null bila hari sudah ditandai beres (R-66).
 */
fun dailyNotification(input: LedgerInput, state: LedgerState, today: LocalDate, doneMarked: Boolean): NotifSpec? {
    if (doneMarked) return null
    val cats = input.categories
    val hasRecord = input.transactions.any { it.date == today && (it.type == TxType.EXPENSE || it.type == TxType.REFUND) }
    val lines = mutableListOf<String>()
    val title: String
    if (!hasRecord) {
        title = "Belum ada catatan hari ini."
        lines += "Catat sekarang biar jatah besok akurat."
        // R-27 tetap diingatkan walau belum ada catatan.
        state.daily.filter { app.catatuang.engine.isLargeDebt(it.projectedHutang, it.jatah) }.forEach { d ->
            lines += "Hutang ${cats.first { it.id == d.categoryId }.name.lowercase()} ${rp(d.projectedHutang)} — lebih dari 3× jatah, pertimbangkan Mode Darurat"
        }
    } else {
        title = "Catatan hari ini"
        lines += state.daily.joinToString(" · ") { d -> dailyStatus(cats.first { it.id == d.categoryId }.name, d.jatah, d.used) }
        state.daily.filter { it.projectedHutang > 0 }.forEach { d ->
            val name = cats.first { it.id == d.categoryId }.name.lowercase()
            val warn = if (app.catatuang.engine.isLargeDebt(d.projectedHutang, d.jatah)) " — lebih dari 3× jatah, pertimbangkan Mode Darurat" else ""
            lines += "Hutang $name ${rp(d.projectedHutang)}$warn"
        }
        lines += "Saku Sisa ${rp(state.projectedSakuSisa)}"
    }
    if (today.dayOfWeek == DayOfWeek.SUNDAY) {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val spent = input.transactions.filter { !it.date.isBefore(monday) && !it.date.isAfter(today) }.sumOf {
            when (it.type) {
                TxType.EXPENSE -> it.amount
                TxType.REFUND -> -it.amount
                else -> 0
            }
        }
        lines += "Minggu ini keluar ${rp(spent)}."
    }
    // Android hanya menampilkan 3 tombol (D-52): Jumat–Minggu tombol tengah Transport, hari lain Lainnya.
    val weekend = today.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return NotifSpec(
        id = NotifIds.DAILY,
        channel = Channel.HARIAN,
        title = title,
        text = lines.joinToString("\n"),
        route = if (today.dayOfWeek == DayOfWeek.SUNDAY) "report" else "picker",
        actions = listOf(
            NotifAction("Makan", route = "input/${Defaults.ID_MAKAN}"),
            if (weekend) NotifAction("Transport", route = "input/${Defaults.ID_TRANSPORT}") else NotifAction("Lainnya", route = "picker"),
            NotifAction("Hari ini beres", broadcast = ACTION_DAY_DONE),
        ),
    )
}

/** Isi pemeriksaan jam 07:00 / 09:00 / 12:00 (bab 9). */
fun reminderNotifications(
    slot: Slot,
    input: LedgerInput,
    state: LedgerState,
    today: LocalDate,
    checklist: Pair<String?, Int>,
    checklistSince: LocalDate?,
): List<NotifSpec> = when (slot) {
    Slot.DAILY, Slot.AT_2300 -> emptyList()
    Slot.AT_0700 -> {
        val pending = pendingClosing(state)
        if (today.dayOfMonth == 1 && pending != null) {
            listOf(NotifSpec(NotifIds.CLOSING, Channel.TUTUP_BUKU, "${monthName(pending.month)} selesai. Yuk tutup buku.", "Cek verdict dan pindahkan sisa Saku Sisa.", "closing"))
        } else emptyList()
    }
    Slot.AT_0900 -> buildList {
        // R-41: jatuh tempo pos TETAP (juga saat gaji belum masuk).
        unpaidFixed(input.categories, state, today).filter { it.dueDate == today }.forEach { f ->
            add(NotifSpec(NotifIds.fixed(f.categoryId), Channel.PENGINGAT, "${f.name} jatuh tempo. Bayar berapa?", "Estimasi ${rp(f.estimate)}", "pay/${f.categoryId}"))
        }
        // R-14 butir 4.
        val target = state.targetCadangan ?: 0
        val kurang = target - state.cadanganTerkumpul
        if (isFiveDaysBeforeMonthEnd(today) && target > 0 && kurang > 0) {
            add(NotifSpec(NotifIds.CADANGAN, Channel.PENGINGAT, "Cadangan belum terkumpul",
                "Kurang ${rp(kurang)}, hemat ±${rp(dailySavingAdvice(kurang, today))}/hari biar tabungan aman", "home"))
        }
        // R-57: checklist transfer, mulai esok hari, maks 3×.
        val (month, count) = checklist
        if (month != null && count < 3 && (checklistSince == null || checklistSince.isBefore(today))) {
            val amount = input.transactions.filter { it.routine && it.refYearMonth?.toString() == month }.sumOf { it.amount }
            if (amount > 0) add(NotifSpec(NotifIds.CHECKLIST, Channel.PENGINGAT, "Udah transfer ${rp(amount)} ke tabungan?",
                "Centang kalau uangnya sudah dipindah ke rekening tabungan.", "home",
                listOf(NotifAction("Tandai selesai", broadcast = ACTION_CHECKLIST_DONE))))
        }
    }
    Slot.AT_1200 -> {
        val next = YearMonth.from(today).plusMonths(1)
        if (today.dayOfMonth == salaryReminderDay(YearMonth.from(today)) && !hasSalary(input, next)) {
            listOf(NotifSpec(NotifIds.SALARY, Channel.PENGINGAT, "Gaji sudah masuk?", "Catat gaji ${monthName(next)} supaya split-nya siap tanggal 1.", "salary"))
        } else emptyList()
    }
}
