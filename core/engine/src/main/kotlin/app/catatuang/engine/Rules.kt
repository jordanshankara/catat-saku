package app.catatuang.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** R-26: slot default dari jam input; tanggal "kemarin" → Malam. Jajan hanya manual. */
fun defaultSlot(time: LocalTime, isYesterday: Boolean = false): Slot = when {
    isYesterday -> Slot.MALAM
    time < LocalTime.of(10, 0) -> Slot.SARAPAN
    time < LocalTime.of(15, 0) -> Slot.SIANG
    else -> Slot.MALAM
}

/** R-61: input 00:00–04:59 wajib ditanya "hari ini atau kemarin?" (default Kemarin). */
fun needsDayQuestion(time: LocalTime): Boolean = time < LocalTime.of(5, 0)

data class SuspicionCheck(val suspicious: Boolean, val median: Long?)

/**
 * R-65: nominal > 3× median 20 transaksi terakhir pos itu (minimal 5 data) → minta konfirmasi.
 * [recentAmounts] diurutkan terbaru dulu.
 */
fun isSuspiciousAmount(amount: Long, recentAmounts: List<Long>): SuspicionCheck {
    val sample = recentAmounts.take(20)
    if (sample.size < 5) return SuspicionCheck(false, null)
    val sorted = sample.sorted()
    val mid = sorted.size / 2
    val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    return SuspicionCheck(amount > 3 * median, median)
}

/** Pembagian kekurangan menurut urutan penutup Saku Sisa → Tabungan → Dana Darurat (R-58). */
data class Cover(
    val fromSaku: Long,
    val fromTabungan: Long,
    val fromDanaDarurat: Long,
    val uncovered: Long,
) {
    val fromPots: Long get() = fromTabungan + fromDanaDarurat
}

/**
 * R-58. Saku Sisa dipakai lebih dulu sebatas saldonya yang positif (Saku tidak boleh jadi minus karena
 * aksi ini, R-23). Dana Darurat hanya disentuh bila Tabungan habis.
 */
fun coverShortfall(amount: Long, saku: Long, tabungan: Long, danaDarurat: Long): Cover {
    var rest = maxOf(0L, amount)
    val s = minOf(rest, maxOf(0L, saku)); rest -= s
    val t = minOf(rest, maxOf(0L, tabungan)); rest -= t
    val d = minOf(rest, maxOf(0L, danaDarurat)); rest -= d
    return Cover(s, t, d, rest)
}

/** Opsi langkah 6 Tutup Buku untuk Saku Sisa akhir (R-72, 6.10). */
enum class ClosingOption { TUTUP_WAJIB, ISI_DANA_DARURAT, SEMUA_KE_TABUNGAN, BAWA_KE_BULAN_DEPAN, SPLIT }

fun closingOptions(sakuAkhir: Long, danaDarurat: Long, emergencyTarget: Long): List<ClosingOption> = when {
    sakuAkhir < 0 -> listOf(ClosingOption.TUTUP_WAJIB)
    sakuAkhir == 0L -> emptyList()
    danaDarurat < emergencyTarget -> listOf(
        ClosingOption.ISI_DANA_DARURAT, ClosingOption.SEMUA_KE_TABUNGAN,
        ClosingOption.BAWA_KE_BULAN_DEPAN, ClosingOption.SPLIT,
    )
    else -> listOf(
        ClosingOption.SEMUA_KE_TABUNGAN, ClosingOption.BAWA_KE_BULAN_DEPAN,
        ClosingOption.SPLIT, ClosingOption.ISI_DANA_DARURAT,
    )
}

data class Reconciliation(val type: TxType, val amount: Long)

/** 6.10 langkah 3: selisih kurang → UNRECORDED, selisih lebih → SURPLUS_FOUND. */
fun reconcile(computedCash: Long, actualCash: Long): Reconciliation? {
    val diff = actualCash - computedCash
    return when {
        diff < 0 -> Reconciliation(TxType.UNRECORDED, -diff)
        diff > 0 -> Reconciliation(TxType.SURPLUS_FOUND, diff)
        else -> null
    }
}

/** R-14 butir 4: "hemat ±Rp Y/hari", Y = kurang ÷ sisa hari (termasuk hari ini), dibulatkan ke atas ke ribuan. */
fun dailySavingAdvice(shortfall: Long, today: LocalDate): Long {
    if (shortfall <= 0) return 0
    val daysLeft = ChronoUnit.DAYS.between(today, YearMonth.from(today).lastDay()) + 1
    return ceil1000(-Math.floorDiv(-shortfall, daysLeft))
}

/** H-5 akhir bulan (bab 9). */
fun isFiveDaysBeforeMonthEnd(date: LocalDate): Boolean = date == YearMonth.from(date).lastDay().minusDays(5)

/**
 * 8.3: "Tahan di Rp X sampai akhir hari" dengan X = jatah − hutang, hanya jika terpakai hari ini ≤ X.
 * Mengembalikan X atau null bila kalimat tidak ditampilkan.
 */
fun holdTarget(jatah: Long, hutang: Long, usedToday: Long): Long? {
    if (hutang <= 0) return null
    val x = jatah - hutang
    return if (x >= 0 && usedToday <= x) x else null
}

/** 6.8: tujuan default pemasukan. */
fun defaultIncomeDestination(kind: IncomeKind): Pair<Destination, Pot?> = when (kind) {
    IncomeKind.THR_BONUS -> Destination.POT to Pot.TABUNGAN
    else -> Destination.SAKU to null
}

enum class StockStatus { NORMAL, WASPADA, LEBIH }

/** R-34. */
fun stockStatus(used: Long, budget: Long): StockStatus = when {
    used > budget -> StockStatus.LEBIH
    budget > 0 && used * 10 >= budget * 8 -> StockStatus.WASPADA
    budget == 0L && used > 0 -> StockStatus.LEBIH
    else -> StockStatus.NORMAL
}

enum class SafetyStatus { AMAN, WASPADA, MINUS }

/** R-16. */
fun safetyStatus(sisaBebas: Long, threshold: Long): SafetyStatus = when {
    sisaBebas < 0 -> SafetyStatus.MINUS
    sisaBebas < threshold -> SafetyStatus.WASPADA
    else -> SafetyStatus.AMAN
}

/** R-27: hutang besar jika > 3× jatah hariannya. */
fun isLargeDebt(hutang: Long, jatah: Long): Boolean = jatah > 0 && hutang > 3 * jatah
