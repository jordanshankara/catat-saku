package app.catatuang.engine

import java.time.LocalDate
import java.time.YearMonth

/** Tanggal alokasi aktif: tanggal 1 untuk gaji cepat (R-03), tanggal input untuk gaji telat (R-04). */
fun salaryActivationDate(received: LocalDate, target: YearMonth): LocalDate = maxOf(received, target.atDay(1))

/**
 * R-07: setoran Nabung rutin yang dibuat bersama gaji, bertanggal saat alokasi aktif.
 * Null jika alokasi tidak punya pos TABUNGAN atau nominalnya 0.
 */
fun routineDepositFor(
    id: Long,
    received: LocalDate,
    target: YearMonth,
    allocation: List<AllocationLine>,
    categories: List<Category>,
    createdAt: Long = 0,
): Tx? {
    val amount = allocation.filter { line -> categories.any { it.id == line.categoryId && it.kind == CategoryKind.SAVING } }
        .sumOf { it.monthlyAmount }
    if (amount <= 0) return null
    return Tx(
        id = id,
        date = salaryActivationDate(received, target),
        type = TxType.SAVING_DEPOSIT,
        amount = amount,
        pot = Pot.TABUNGAN,
        routine = true,
        refYearMonth = target,
        createdAt = createdAt,
    )
}

data class FixedObligationSeed(val month: YearMonth, val categoryId: Long, val estimate: Long, val dueDate: LocalDate)

/** R-40: kewajiban pos TETAP bulan [month] dari alokasinya (atau template bila gaji belum masuk). */
fun fixedObligationsFor(month: YearMonth, allocation: List<AllocationLine>, categories: List<Category>): List<FixedObligationSeed> =
    allocation.mapNotNull { line ->
        val cat = categories.firstOrNull { it.id == line.categoryId && it.kind == CategoryKind.FIXED } ?: return@mapNotNull null
        val day = (cat.dueDay ?: 1).coerceIn(1, month.lengthOfMonth())
        FixedObligationSeed(month, cat.id, line.monthlyAmount, month.atDay(day))
    }
