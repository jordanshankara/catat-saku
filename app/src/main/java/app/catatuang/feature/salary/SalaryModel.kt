package app.catatuang.feature.salary

import app.catatuang.engine.AllocationLine
import app.catatuang.engine.Category
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.ReserveReason
import app.catatuang.engine.SplitLine
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.weekendAllowance
import app.catatuang.ui.format.dayMonth
import app.catatuang.ui.format.digits
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.rpShort
import java.time.LocalDate
import java.time.YearMonth

enum class SalaryStep { NOMINAL, ADJUST, PREVIEW, CHECKLIST }

/** R-02: pilihan bulan target = bulan terima & bulan berikutnya, yang sudah dimulai app dan belum ditutup. */
fun targetOptions(received: LocalDate, startMonth: YearMonth, closed: Set<YearMonth>): List<YearMonth> =
    listOf(YearMonth.from(received), YearMonth.from(received).plusMonths(1)).filter { it >= startMonth && it !in closed }

/** Gaji terakhir yang dicatat, untuk isian awal langkah 1 (8.6). */
fun lastSalaryAmount(transactions: List<Tx>, template: Long): Long =
    transactions.filter { it.type == TxType.SALARY && it.incomeKind == null }.maxByOrNull { it.date }?.amount ?: template

/** Rumus terlihat di preview split: "Makan · 50.000 × 31 hari = 1.550.000". */
fun splitLineText(line: SplitLine, category: Category): String = when {
    line.kind == CategoryKind.DAILY -> "${category.name} · ${digits(line.dailyAmount ?: 0)} × ${line.days} hari = ${digits(line.total)}"
    category.weekendMode -> "${category.name} · ${digits(line.total)} (${digits(weekendAllowance(line.total))} × 4 akhir pekan)"
    line.kind == CategoryKind.FIXED -> "${category.name} · ${digits(line.total)} (tetap)"
    line.kind == CategoryKind.SAVING -> "${category.name} · ${digits(line.total)} → Tabungan"
    else -> "${category.name} · ${digits(line.total)}"
}

/** Rincian penyebab Target Cadangan untuk kartu kuning (R-14 butir 1). */
fun reasonText(reason: ReserveReason, categories: List<Category>): String = when (reason) {
    is ReserveReason.ExtraDays -> {
        val names = categories.filter { it.kind == CategoryKind.DAILY }.sortedBy { it.sortOrder }.joinToString(" & ") { it.name }
        "${reason.days} hari: +${rpShort(reason.amount).removePrefix("Rp ")} ($names)"
    }
    is ReserveReason.ExtraSaturdays -> "${reason.saturdays} kali Sabtu: +${rpShort(reason.amount).removePrefix("Rp ")}"
    is ReserveReason.SalaryAndCarry ->
        if (reason.amount > 0) "Sisa gaji & bawaan: −${rpShort(reason.amount).removePrefix("Rp ")}"
        else "Gaji di bawah kebutuhan standar: +${rpShort(-reason.amount).removePrefix("Rp ")}"
}

/** Label baris isian Penyesuaian. */
fun allocationLabel(line: AllocationLine, category: Category): String = when (category.kind) {
    CategoryKind.DAILY -> "${category.name} · per hari"
    CategoryKind.FIXED -> "${category.name} · tetap"
    CategoryKind.SAVING -> "${category.name} · ke Tabungan"
    CategoryKind.STOCK -> if (category.weekendMode) "${category.name} · per bulan (÷ 4 akhir pekan)" else "${category.name} · per bulan"
}

fun AllocationLine.value(kind: CategoryKind): Long = if (kind == CategoryKind.DAILY) dailyAmount ?: 0 else monthlyAmount

fun AllocationLine.withValue(kind: CategoryKind, v: Long): AllocationLine =
    if (kind == CategoryKind.DAILY) copy(dailyAmount = v) else copy(monthlyAmount = v)

/** 8.6 langkah 4: feedback "Gaji Oktober tersimpan · aktif 1 Okt" / "Talangan Rp X dikembalikan". */
fun salaryFeedback(target: YearMonth, activation: LocalDate, reference: YearMonth, talanganBack: Long): String =
    if (talanganBack > 0) "Talangan ${rp(talanganBack)} dikembalikan · Gaji ${monthName(target, reference)} tersimpan"
    else "Gaji ${monthName(target, reference)} tersimpan · aktif ${dayMonth(activation)}"
