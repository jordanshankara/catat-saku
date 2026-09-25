package app.catatuang.engine

import java.time.LocalDate
import java.time.YearMonth

/** Fallback chip nominal cepat (8.2), per key pos. */
val QUICK_AMOUNT_FALLBACK: Map<String, List<Long>> = mapOf(
    "makan" to listOf(10_000, 15_000, 20_000, 25_000),
    "buah" to listOf(5_000, 10_000),
    "transport" to listOf(60_000, 120_000),
    "protein" to listOf(93_000, 100_000),
    "lain" to listOf(10_000, 20_000, 50_000, 100_000),
)

/**
 * 8.2: 4 nominal paling sering untuk pos itu dalam 60 hari terakhir (seri: yang terbaru menang),
 * diurutkan naik. Jika belum ada data, pakai fallback.
 */
fun quickAmounts(transactions: List<Tx>, category: Category, today: LocalDate): List<Long> {
    val from = today.minusDays(59)
    val recent = transactions.filter {
        it.type == TxType.EXPENSE && it.categoryId == category.id && !it.date.isBefore(from) && !it.date.isAfter(today)
    }
    if (recent.isEmpty()) return QUICK_AMOUNT_FALLBACK[category.key] ?: listOf(10_000, 20_000, 50_000, 100_000)
    val lastSeen = recent.groupBy { it.amount }.mapValues { (_, l) -> l.maxOf { t -> t.date.toEpochDay() * 1_000_000_000L + t.createdAt % 1_000_000_000L } }
    return recent.groupingBy { it.amount }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenByDescending { lastSeen[it.key] })
        .take(4).map { it.key }.sorted()
}

/** R-65: nominal pengeluaran terbaru pos itu (maks 20, terbaru dulu) untuk [isSuspiciousAmount]. */
fun recentAmounts(transactions: List<Tx>, categoryId: Long): List<Long> =
    transactions.filter { it.type == TxType.EXPENSE && it.categoryId == categoryId }
        .sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.createdAt })
        .take(20).map { it.amount }

/** 8.3: statistik bulan pos HARIAN dari hari-hari yang sudah tertutup. */
data class DailyMonthStats(val savedDays: Int, val overDays: Int, val toSaku: Long)

fun dailyMonthStats(state: LedgerState, categoryId: Long, month: YearMonth = state.currentMonth): DailyMonthStats {
    val days = state.dailyLog.filter { it.categoryId == categoryId && YearMonth.from(it.date) == month }
    return DailyMonthStats(
        savedDays = days.count { it.delta > 0 },
        overDays = days.count { it.delta < 0 },
        toSaku = days.sumOf { it.toSaku },
    )
}

data class DayUsage(val date: LocalDate, val jatah: Long, val used: Long) {
    val over: Long get() = maxOf(0, used - jatah)
}

/** 8.3: pemakaian 7 hari terakhir (termasuk hari ini). Hari sebelum tanggal mulai dilewati. */
fun lastSevenDays(state: LedgerState, categoryId: Long): List<DayUsage> {
    val closed = state.dailyLog.filter { it.categoryId == categoryId }.associateBy { it.date }
    val today = state.daily.firstOrNull { it.categoryId == categoryId }
    return (6 downTo 0).mapNotNull { back ->
        val date = state.today.minusDays(back.toLong())
        when {
            back == 0 && today != null -> DayUsage(date, today.jatah, today.used)
            else -> closed[date]?.let { DayUsage(date, it.jatah, it.used) }
        }
    }
}

/** R-61: bulan yang boleh dipilih di date picker — sejak tanggal mulai s/d hari ini, kecuali bulan tertutup. */
fun selectableDateRange(start: LocalDate, today: LocalDate, closedMonths: Set<YearMonth>): List<LocalDate> =
    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
        .filter { YearMonth.from(it) !in closedMonths }.toList()
