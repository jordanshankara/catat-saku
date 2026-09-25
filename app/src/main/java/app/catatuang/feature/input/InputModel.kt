package app.catatuang.feature.input

import app.catatuang.engine.Category
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.Impact
import app.catatuang.engine.ImpactWarning
import app.catatuang.engine.Slot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.rpShort
import app.catatuang.ui.format.shortDate
import java.time.LocalDate

/** Batas digit nominal (Rp 999.999.999.999). */
private const val MAX_DIGITS = 12

fun appendDigits(current: Long, digits: String): Long {
    val s = (if (current == 0L) "" else current.toString()) + digits
    val trimmed = s.trimStart('0')
    if (trimmed.isEmpty()) return 0
    return if (trimmed.length > MAX_DIGITS) current else trimmed.toLong()
}

fun backspace(current: Long): Long = current / 10

fun expenseDraft(category: Category, amount: Long, date: LocalDate, slot: Slot?, note: String?): Tx = Tx(
    id = Long.MIN_VALUE,
    date = date,
    type = TxType.EXPENSE,
    amount = amount,
    categoryId = category.id,
    slot = if (category.hasSlots) slot else null,
    note = note?.trim()?.ifEmpty { null },
    createdAt = Long.MAX_VALUE,
)

fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hari ini"
    today.minusDays(1) -> "Kemarin"
    else -> shortDate(date)
}

/** 8.2 kotak preview dampak: baris pesan + nada; urut dari yang paling penting. */
fun previewLines(category: Category, impact: Impact, date: LocalDate, today: LocalDate): List<Pair<String, Tone>> {
    val lines = mutableListOf<Pair<String, Tone>>()
    val w = impact.warnings
    when {
        category.kind == CategoryKind.DAILY -> {
            val jatah = impact.dailyJatah ?: 0
            val used = impact.dailyUsedAfter ?: 0
            val debt = impact.debtAfter ?: 0
            val name = category.name.lowercase()
            if (used > jatah) {
                lines += "Lebih ${rp(used - jatah)} dari jatah — hutang $name jadi ${rp(debt)}" to Tone.WARNING
            } else {
                val whenText = if (date == today) "hari ini" else shortDate(date)
                val tail = if (debt > 0) " · hutang $name ${rp(debt)}" else ""
                lines += "Sisa jatah $whenText jadi ${rp(jatah - used)}$tail" to Tone.SUCCESS
            }
            if (ImpactWarning.LARGE_DEBT in w) lines += "Hutang $name sudah lebih dari 3× jatah — pertimbangkan Mode Darurat" to Tone.DANGER
        }
        category.weekendMode -> {
            if (ImpactWarning.EXTRA_TRIP in w) {
                lines += "Trip tambahan — diambil dari Saku Sisa" to Tone.WARNING
            } else {
                val used = impact.weekendUsedAfter ?: 0
                val j = impact.weekendAllowance ?: 0
                val tone = if (ImpactWarning.WEEKEND_OVER in w) Tone.WARNING else Tone.SUCCESS
                lines += "Akhir pekan ini jadi ${rp(used)} / ${rp(j)}" to tone
            }
        }
        category.kind == CategoryKind.STOCK -> {
            if (impact.stockOverBy > 0) {
                lines += "Melebihi budget ${rp(impact.stockOverBy)} — diambil dari Saku Sisa" to Tone.WARNING
            } else {
                lines += "Terpakai jadi ${rp(impact.stockUsedAfter ?: 0)} / ${rp(impact.stockBudget ?: 0)}" to Tone.SUCCESS
            }
        }
    }
    if (ImpactWarning.SAKU_NEGATIVE in w) {
        lines += "Saku Sisa minus — akhir bulan ditutup dari Tabungan, lalu Dana Darurat" to Tone.DANGER
    } else if (ImpactWarning.USES_RESERVE in w) {
        lines += "Ini memakai cadangan akhir bulan" to Tone.WARNING
    }
    if (ImpactWarning.TALANGAN_EXHAUSTED in w) {
        val short = impact.talanganAfter?.shortfall ?: 0
        lines += "Dana talangan habis, kurang ${rp(short)}" to Tone.DANGER
    }
    return lines
}

/** R-62 kartu feedback: "Makan +Rp 25.000 · Hari ini 60rb / 50rb · Hutang Rp 25.000". */
fun feedbackText(category: Category, amount: Long, impact: Impact, date: LocalDate, today: LocalDate): Pair<String, Tone> {
    val head = "${category.name} +${rp(amount)}"
    return when {
        category.kind == CategoryKind.DAILY -> {
            val used = impact.dailyUsedAfter ?: 0
            val jatah = impact.dailyJatah ?: 0
            val day = if (date == today) "Hari ini" else dateLabel(date, today)
            val usage = "$day ${rpShort(used).removePrefix("Rp ")} / ${rpShort(jatah).removePrefix("Rp ")}"
            val debt = impact.debtAfter ?: 0
            val tone = if (used > jatah) Tone.DANGER else Tone.SUCCESS
            listOfNotNull(head, usage, debt.takeIf { it > 0 }?.let { "Hutang ${rp(it)}" }).joinToString(" · ") to tone
        }
        category.weekendMode && impact.weekendUsedAfter != null ->
            "$head · Akhir pekan ${rpShort(impact.weekendUsedAfter!!).removePrefix("Rp ")} / ${rpShort(impact.weekendAllowance ?: 0).removePrefix("Rp ")}" to
                (if (ImpactWarning.WEEKEND_OVER in impact.warnings) Tone.WARNING else Tone.SUCCESS)
        category.weekendMode -> "$head · Trip tambahan · Saku Sisa ${rp(impact.sakuAfter)}" to Tone.WARNING
        else -> {
            val tone = if (impact.stockOverBy > 0) Tone.WARNING else Tone.SUCCESS
            "$head · Terpakai ${rpShort(impact.stockUsedAfter ?: 0).removePrefix("Rp ")} / ${rpShort(impact.stockBudget ?: 0).removePrefix("Rp ")}" to tone
        }
    }
}

/** Tone paling berat dari daftar pesan (untuk warna kotak preview). */
fun worstTone(lines: List<Pair<String, Tone>>): Tone = when {
    lines.any { it.second == Tone.DANGER } -> Tone.DANGER
    lines.any { it.second == Tone.WARNING } -> Tone.WARNING
    else -> Tone.SUCCESS
}
