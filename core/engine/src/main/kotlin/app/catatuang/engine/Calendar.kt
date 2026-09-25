package app.catatuang.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** R-17: pembulatan ke bawah ke kelipatan 1.000. */
fun floor1000(x: Long): Long = Math.floorDiv(x, 1000L) * 1000L

/** Pembulatan ke atas ke kelipatan 1.000 (hanya saran hemat R-14 butir 4). */
fun ceil1000(x: Long): Long = -Math.floorDiv(-x, 1000L) * 1000L

fun saturdaysOf(ym: YearMonth): List<LocalDate> =
    (1..ym.lengthOfMonth()).map { ym.atDay(it) }.filter { it.dayOfWeek == DayOfWeek.SATURDAY }

/** S(M). */
fun countSaturdays(ym: YearMonth): Int = saturdaysOf(ym).size

/** R-35: jendela akhir pekan Jumat 00:00 – Minggu 23:59, milik bulan si Sabtu, tertutup Senin 00:00. */
data class WeekendWindow(val saturday: LocalDate) {
    val friday: LocalDate get() = saturday.minusDays(1)
    val sunday: LocalDate get() = saturday.plusDays(1)
    val owner: YearMonth get() = YearMonth.from(saturday)
    val closesOn: LocalDate get() = saturday.plusDays(2)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(friday) && !date.isAfter(sunday)

    /** Tertutup bila hari Minggu-nya sudah ditutup (hari terakhir yang tertutup ≥ Minggu). */
    fun isClosedBy(lastClosedDay: LocalDate): Boolean = !lastClosedDay.isBefore(sunday)
}

fun weekendWindowOf(date: LocalDate): WeekendWindow? = when (date.dayOfWeek) {
    DayOfWeek.FRIDAY -> WeekendWindow(date.plusDays(1))
    DayOfWeek.SATURDAY -> WeekendWindow(date)
    DayOfWeek.SUNDAY -> WeekendWindow(date.minusDays(1))
    else -> null
}

/** R-02: tanggal terima ≥ 20 → bulan berikutnya; < 20 → bulan berjalan. */
fun defaultTargetMonth(received: LocalDate): YearMonth =
    if (received.dayOfMonth >= 20) YearMonth.from(received).plusMonths(1) else YearMonth.from(received)

/** "Tutup buku · n hari lagi": selisih hari ke tanggal 1 bulan berikutnya (28 Sep → 3). */
fun daysUntilClosing(today: LocalDate): Long =
    ChronoUnit.DAYS.between(today, YearMonth.from(today).plusMonths(1).atDay(1))

fun YearMonth.lastDay(): LocalDate = atEndOfMonth()

/**
 * R-08: bulan akuntansi. Transaksi Tutup Buku → bulan yang ditutup; transaksi Transport di dalam jendela
 * akhir pekan → bulan si Sabtu; SALARY → bulan target; selain itu bulan tanggalnya.
 */
fun accountingMonth(tx: Tx, isWeekendCategory: (Long) -> Boolean): YearMonth {
    tx.closingOf?.let { return it }
    return when (tx.type) {
        TxType.SALARY -> tx.refYearMonth ?: defaultTargetMonth(tx.date)
        TxType.SAVING_DEPOSIT -> if (tx.routine && tx.refYearMonth != null) tx.refYearMonth else YearMonth.from(tx.date)
        TxType.EXPENSE, TxType.REFUND, TxType.CORRECTION -> {
            val cat = tx.categoryId
            val window = weekendWindowOf(tx.date)
            if (cat != null && window != null && isWeekendCategory(cat)) window.owner else YearMonth.from(tx.date)
        }
        else -> YearMonth.from(tx.date)
    }
}

fun accountingMonth(tx: Tx, categories: List<Category>): YearMonth =
    accountingMonth(tx) { id -> categories.any { it.id == id && it.weekendMode } }

/** R-64 + R-08: transaksi terkunci jika bulan akuntansinya sudah Tutup Buku. */
fun isLocked(tx: Tx, categories: List<Category>, closedMonths: Set<YearMonth>): Boolean =
    accountingMonth(tx, categories) in closedMonths
