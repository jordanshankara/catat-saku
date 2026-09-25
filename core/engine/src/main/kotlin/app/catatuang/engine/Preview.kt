package app.catatuang.engine

import java.time.LocalDate
import java.time.YearMonth

enum class ImpactWarning {
    /** HARIAN: melebihi jatah hari itu → hutang bertambah. */
    DAILY_OVER,
    /** STOK: melebihi budget → diambil dari Saku Sisa (R-31). */
    STOCK_OVER,
    /** Transport di luar jendela (R-38). */
    EXTRA_TRIP,
    /** Transport di dalam jendela melebihi jatah akhir pekan. */
    WEEKEND_OVER,
    /** R-51 kuning: membuat Sisa bebas < 0. */
    USES_RESERVE,
    /** R-51 merah: membuat Saku Sisa < 0. */
    SAKU_NEGATIVE,
    /** R-27. */
    LARGE_DEBT,
    /** R-04: talangan melebihi semua kantong. */
    TALANGAN_EXHAUSTED,
}

data class Impact(
    val month: YearMonth,
    val sakuBefore: Long,
    val sakuAfter: Long,
    val sisaBebasBefore: Long,
    val sisaBebasAfter: Long,
    /** HARIAN: terpakai & jatah hari itu setelah transaksi. */
    val dailyUsedAfter: Long?,
    val dailyJatah: Long?,
    /** Hutang pos HARIAN jika hari itu ditutup (preview). */
    val debtAfter: Long?,
    val stockUsedAfter: Long?,
    val stockBudget: Long?,
    /** Kelebihan budget STOK yang baru diambil dari Saku Sisa oleh transaksi ini. */
    val stockOverBy: Long,
    val weekendUsedAfter: Long?,
    val weekendAllowance: Long?,
    val talanganAfter: Talangan?,
    val warnings: Set<ImpactWarning>,
    /** R-59: nominal "Pakai Tabungan (Rencana)" dan apakah Tabungan cukup. */
    val useSavingsAmount: Long,
    val savingsCanCover: Boolean,
)

/**
 * 7.2 `previewImpact`: dampak [draft] terhadap ledger sebelum disimpan, dihitung dengan memutar ulang
 * ledger lengkap (sehingga konsisten dengan R-60).
 */
fun previewImpact(input: LedgerInput, today: LocalDate, draft: Tx): Impact {
    val before = computeLedger(input, today)
    val after = computeLedger(input.copy(transactions = input.transactions + draft), today)
    val month = accountingMonth(draft, input.categories)
    val mb = before.months[month] ?: before.current!!
    val ma = after.months[month] ?: after.current!!
    val cat = draft.categoryId?.let { id -> input.categories.firstOrNull { it.id == id } }
    val warnings = mutableSetOf<ImpactWarning>()

    var dailyUsed: Long? = null
    var dailyJatah: Long? = null
    var debtAfter: Long? = null
    var stockUsed: Long? = null
    var stockBudget: Long? = null
    var stockOverBy = 0L
    var weekendUsed: Long? = null
    var weekendAllowance: Long? = null

    when {
        cat?.kind == CategoryKind.DAILY -> {
            val jatah = ma.allocation.firstOrNull { it.categoryId == cat.id }?.dailyAmount ?: 0
            val used = if (draft.date == today) {
                after.daily.firstOrNull { it.categoryId == cat.id }?.used ?: 0
            } else {
                after.dailyLog.lastOrNull { it.categoryId == cat.id && it.date == draft.date }?.used ?: 0
            }
            val projected = computeLedgerProjectedDebt(after, cat.id)
            dailyUsed = used; dailyJatah = jatah; debtAfter = projected
            if (used > jatah && draft.type == TxType.EXPENSE) warnings += ImpactWarning.DAILY_OVER
            if (isLargeDebt(projected, jatah)) warnings += ImpactWarning.LARGE_DEBT
        }
        cat?.kind == CategoryKind.STOCK && cat.weekendMode -> {
            val w = weekendWindowOf(draft.date)
            val info = w?.let { win -> ma.windows.firstOrNull { it.window.saturday == win.saturday } }
            if (info == null) {
                if (draft.type == TxType.EXPENSE) warnings += ImpactWarning.EXTRA_TRIP
            } else {
                weekendUsed = info.used; weekendAllowance = ma.weekendAllowance
                if (info.funded && info.used > ma.weekendAllowance) warnings += ImpactWarning.WEEKEND_OVER
            }
        }
        cat?.kind == CategoryKind.STOCK -> {
            val sa = ma.stock.firstOrNull { it.categoryId == cat.id }
            val sb = mb.stock.firstOrNull { it.categoryId == cat.id }
            stockUsed = sa?.used; stockBudget = sa?.budget
            stockOverBy = maxOf(0, (sa?.used ?: 0) - (sa?.budget ?: 0)) - maxOf(0, (sb?.used ?: 0) - (sb?.budget ?: 0))
            if (stockOverBy > 0) warnings += ImpactWarning.STOCK_OVER
        }
    }

    val sakuDropped = ma.sakuSisa < mb.sakuSisa
    val bebasDropped = ma.sisaBebas < mb.sisaBebas
    if (ma.funded && bebasDropped && ma.sisaBebas < 0) warnings += ImpactWarning.USES_RESERVE
    if (ma.funded && sakuDropped && ma.sakuSisa < 0) warnings += ImpactWarning.SAKU_NEGATIVE
    if ((after.talangan?.shortfall ?: 0) > 0) warnings += ImpactWarning.TALANGAN_EXHAUSTED

    val useSavings = if (ImpactWarning.USES_RESERVE in warnings) -ma.sisaBebas else 0
    return Impact(
        month = month,
        sakuBefore = mb.sakuSisa,
        sakuAfter = ma.sakuSisa,
        sisaBebasBefore = mb.sisaBebas,
        sisaBebasAfter = ma.sisaBebas,
        dailyUsedAfter = dailyUsed,
        dailyJatah = dailyJatah,
        debtAfter = debtAfter,
        stockUsedAfter = stockUsed,
        stockBudget = stockBudget,
        stockOverBy = stockOverBy,
        weekendUsedAfter = weekendUsed,
        weekendAllowance = weekendAllowance,
        talanganAfter = after.talangan,
        warnings = warnings,
        useSavingsAmount = useSavings,
        savingsCanCover = useSavings > 0 && after.tabunganShown >= useSavings,
    )
}

private fun computeLedgerProjectedDebt(state: LedgerState, categoryId: Long): Long =
    state.daily.firstOrNull { it.categoryId == categoryId }?.projectedHutang ?: (state.hutang[categoryId] ?: 0)
