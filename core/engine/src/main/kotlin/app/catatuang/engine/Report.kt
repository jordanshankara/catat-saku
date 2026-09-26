package app.catatuang.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/* Laporan (8.10) & tabel export (bab 10). Semua angka dari data transaksi + computeLedger. */

/** Nilai pemakaian pos dari satu transaksi: pengeluaran/bayar +, pengembalian −, koreksi ±; lainnya 0. */
fun spendOf(tx: Tx): Long = when (tx.type) {
    TxType.EXPENSE, TxType.FIXED_PAYMENT -> tx.amount
    TxType.REFUND -> -tx.amount
    TxType.CORRECTION -> tx.signedAmount ?: tx.amount
    else -> 0
}

/** Pemakaian per pos untuk transaksi bertanggal [from]..[to]. */
fun spendByDate(transactions: List<Tx>, from: LocalDate, to: LocalDate): Map<Long, Long> =
    transactions.filter { it.categoryId != null && !it.date.isBefore(from) && !it.date.isAfter(to) && spendOf(it) != 0L }
        .groupBy { it.categoryId!! }.mapValues { (_, l) -> l.sumOf(::spendOf) }.filterValues { it != 0L }

/** Pemakaian per pos untuk bulan akuntansi [month] (R-08). */
fun spendByMonth(input: LedgerInput, month: YearMonth): Map<Long, Long> =
    input.transactions.filter { it.categoryId != null && accountingMonth(it, input.categories) == month && spendOf(it) != 0L }
        .groupBy { it.categoryId!! }.mapValues { (_, l) -> l.sumOf(::spendOf) }.filterValues { it != 0L }

fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

data class WeekendResult(val saturday: LocalDate, val index: Int, val funded: Boolean, val allowance: Long, val used: Long, val closed: Boolean, val toSaku: Long?)

data class WeeklyReport(
    val start: LocalDate,
    val end: LocalDate,
    val perCategory: Map<Long, Long>,
    val total: Long,
    val previousTotal: Long,
    /** Perubahan total vs minggu lalu dalam persen (null bila minggu lalu 0). */
    val changePercent: Int?,
    /** Per pos HARIAN: pemakaian harian Senin s/d min(Minggu, hari ini). */
    val daily: Map<Long, List<DayUsage>>,
    val weekends: List<WeekendResult>,
    /** Hari dengan total pengeluaran terbesar. */
    val worstDay: Pair<LocalDate, Long>?,
    val debtStart: Long,
    val debtEnd: Long,
    /** Perubahan Saku Sisa minggu ini; null bila minggunya melewati pergantian bulan. */
    val sakuChange: Long?,
)

private fun dailyTotals(transactions: List<Tx>, from: LocalDate, to: LocalDate): Map<LocalDate, Long> =
    transactions.filter { !it.date.isBefore(from) && !it.date.isAfter(to) && it.categoryId != null }
        .groupBy { it.date }.mapValues { (_, l) -> l.sumOf(::spendOf) }

/** 8.10 Mingguan (Senin–Minggu) untuk minggu yang memuat [anyDay]. */
fun weeklyReport(input: LedgerInput, state: LedgerState, anyDay: LocalDate): WeeklyReport {
    val start = weekStart(anyDay)
    val end = start.plusDays(6)
    val today = state.today
    val until = minOf(end, today)
    val per = spendByDate(input.transactions, start, until)
    val total = per.values.sum()
    val prev = spendByDate(input.transactions, start.minusDays(7), start.minusDays(1)).values.sum()
    val closed = state.dailyLog.groupBy { it.categoryId }
    val daily = input.categories.filter { it.kind == CategoryKind.DAILY }.associate { cat ->
        val log = closed[cat.id].orEmpty().associateBy { it.date }
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }.mapNotNull { d ->
            if (d == today) state.daily.firstOrNull { it.categoryId == cat.id }?.let { DayUsage(d, it.jatah, it.used) }
            else log[d]?.let { DayUsage(d, it.jatah, it.used) }
        }.toList()
        cat.id to days
    }.filterValues { it.isNotEmpty() }
    val weekends = state.months.values.flatMap { m ->
        m.windows.filter { !it.window.saturday.isBefore(start) && !it.window.saturday.isAfter(end) }.map { w ->
            val log = state.weekendLog.firstOrNull { it.window.saturday == w.window.saturday }
            WeekendResult(w.window.saturday, w.index, w.funded, m.weekendAllowance, w.used, w.closed, log?.toSaku)
        }
    }
    val worst = dailyTotals(input.transactions, start, until).filterValues { it > 0 }.maxByOrNull { it.value }?.toPair()
    fun debtAt(date: LocalDate): Long = state.dailyLog.filter { it.date == date }.sumOf { it.debtAfter }
    val debtStart = debtAt(start.minusDays(1))
    val debtEnd = if (until >= today) state.daily.sumOf { it.projectedHutang } else debtAt(until)
    val month = YearMonth.from(start)
    val sakuChange = if (YearMonth.from(until) != month || start.isAfter(today)) null else {
        val before = computeLedger(input, start).months[month]?.sakuSisa
        val after = if (until >= today) state.months[month]?.sakuSisa else computeLedger(input, until.plusDays(1)).months[month]?.sakuSisa
        if (before != null && after != null) after - before else null
    }
    return WeeklyReport(
        start, end, per, total, prev,
        if (prev > 0) Math.round((total - prev) * 100.0 / prev).toInt() else null,
        daily, weekends.sortedBy { it.saturday }, worst, debtStart, debtEnd, sakuChange,
    )
}

/** Satu baris "budget vs realisasi" (8.10 Bulanan, sheet Per Kategori). */
data class BudgetRow(val categoryId: Long, val kind: CategoryKind, val budget: Long, val used: Long) {
    val remaining: Long get() = budget - used
    val status: StockStatus get() = stockStatus(used, budget)
}

data class MonthlyReport(
    val month: YearMonth,
    val summary: MonthSummary,
    /** Bulan berjalan / belum ditutup: verdict "sementara". */
    val provisional: Boolean,
    val perCategory: Map<Long, Long>,
    val rows: List<BudgetRow>,
    val dayStats: Map<Long, DailyMonthStats>,
    val reconciled: Long?,
)

/** 8.10 Bulanan. */
fun monthlyReport(input: LedgerInput, state: LedgerState, month: YearMonth): MonthlyReport? {
    val m = state.months[month] ?: return null
    val per = spendByMonth(input, month)
    val cats = input.categories.associateBy { it.id }
    val dailyDays = if (m.isOnboarding) month.lengthOfMonth() - input.onboarding.startDate.dayOfMonth + 1 else month.lengthOfMonth()
    val rows = m.allocation.mapNotNull { line ->
        val cat = cats[line.categoryId] ?: return@mapNotNull null
        val used = per[cat.id] ?: 0
        when (cat.kind) {
            CategoryKind.DAILY -> BudgetRow(cat.id, cat.kind, (line.dailyAmount ?: 0) * dailyDays, used)
            CategoryKind.STOCK -> {
                val budget = if (cat.weekendMode) m.transportBudget else m.stock.firstOrNull { it.categoryId == cat.id }?.budget ?: line.monthlyAmount
                BudgetRow(cat.id, cat.kind, budget, used)
            }
            CategoryKind.FIXED -> BudgetRow(cat.id, cat.kind, line.monthlyAmount, used)
            CategoryKind.SAVING -> BudgetRow(cat.id, cat.kind, line.monthlyAmount, m.nabungRutin)
        }
    }
    val stats = input.categories.filter { it.kind == CategoryKind.DAILY }.associate { it.id to dailyMonthStats(state, it.id, month) }
    return MonthlyReport(month, m, !m.closed, per, rows, stats, reconciledDiff(input, month))
}

// ---------- Tabel export (bab 10) ----------

/** Sel tabel export: teks atau angka (nominal ditulis sebagai angka di Excel). */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Num(val value: Long) : Cell
}

data class Sheet(val name: String, val rows: List<List<Cell>>)

private fun t(s: String) = Cell.Text(s)
private fun n(v: Long) = Cell.Num(v)

fun txTypeLabel(tx: Tx): String = when (tx.type) {
    TxType.SALARY -> if (tx.incomeKind != null) "Gaji tambahan" else "Gaji"
    TxType.INCOME -> "Pemasukan"
    TxType.REFUND -> "Pengembalian"
    TxType.EXPENSE -> "Pengeluaran"
    TxType.FIXED_PAYMENT -> "Bayar tagihan"
    TxType.SAVING_DEPOSIT -> if (tx.routine) "Nabung rutin" else "Setor kantong"
    TxType.SAVING_WITHDRAW -> "Ambil kantong"
    TxType.DEBT_PAYOFF -> "Lunasi hutang"
    TxType.CARRY_OVER -> "Bawa ke bulan depan"
    TxType.UNRECORDED -> "Tidak tercatat"
    TxType.SURPLUS_FOUND -> "Selisih lebih"
    TxType.CORRECTION -> "Koreksi"
}

/** Nominal bertanda dari sudut uang pegangan (+ masuk, − keluar; perpindahan internal tetap positif). */
fun signedForExport(tx: Tx): Long = when (tx.type) {
    TxType.EXPENSE, TxType.FIXED_PAYMENT, TxType.SAVING_DEPOSIT, TxType.UNRECORDED -> -tx.amount
    TxType.CORRECTION -> -(tx.signedAmount ?: tx.amount)
    else -> tx.amount
}

data class ExportRange(val label: String, val from: LocalDate, val to: LocalDate, val month: YearMonth?)

/** Lima sheet bab 10. [range.month] diisi untuk export "Bulan" (verdict, budget, cadangan). */
fun exportSheets(input: LedgerInput, state: LedgerState, range: ExportRange): List<Sheet> {
    val cats = input.categories.associateBy { it.id }
    fun name(id: Long?) = id?.let { cats[it]?.name } ?: ""
    val txs = input.transactions.filter {
        if (range.month != null) recordMonth(it, input.categories) == range.month
        else !it.date.isBefore(range.from) && !it.date.isAfter(range.to)
    }.sortedWith(compareBy<Tx>({ it.date }, { it.createdAt }))
    val monthly = range.month?.let { monthlyReport(input, state, it) }
    val spend = monthly?.perCategory ?: spendByDate(input.transactions, range.from, range.to)
    val pemasukan = txs.filter { it.type == TxType.SALARY || (it.type == TxType.INCOME) || it.type == TxType.SURPLUS_FOUND }.sumOf { it.amount }

    val ringkasan = buildList {
        add(listOf(t("Catat Uang"), t(range.label)))
        add(listOf(t("Periode"), t("${range.from} s/d ${range.to}")))
        add(listOf(t("Pemasukan"), n(monthly?.summary?.let { (it.salary ?: 0) + it.gajiTambahan + it.pemasukan } ?: pemasukan)))
        add(listOf(t("Pengeluaran"), n(monthly?.summary?.pengeluaranRiil ?: spend.values.sum())))
        val tabIn = txs.filter { it.pot == Pot.TABUNGAN && (it.type == TxType.SAVING_DEPOSIT || it.type == TxType.INCOME) }.sumOf { it.amount }
        val tabOut = txs.filter { it.pot == Pot.TABUNGAN && it.type == TxType.SAVING_WITHDRAW }.sumOf { it.amount }
        val ddIn = txs.filter { it.pot == Pot.DANA_DARURAT && (it.type == TxType.SAVING_DEPOSIT || it.type == TxType.INCOME) }.sumOf { it.amount }
        val ddOut = txs.filter { it.pot == Pot.DANA_DARURAT && it.type == TxType.SAVING_WITHDRAW }.sumOf { it.amount }
        add(listOf(t("Tabungan masuk"), n(tabIn)))
        add(listOf(t("Tabungan keluar"), n(tabOut)))
        add(listOf(t("Saldo Tabungan sekarang"), n(state.tabungan)))
        add(listOf(t("Dana Darurat masuk"), n(ddIn)))
        add(listOf(t("Dana Darurat terpakai"), n(ddOut)))
        add(listOf(t("Saldo Dana Darurat sekarang"), n(state.danaDarurat)))
        add(listOf(t("Hutang harian sekarang"), n(state.totalHutang)))
        add(listOf(t("Saku Sisa sekarang"), n(state.sakuSisa)))
        monthly?.let { r ->
            val v = r.summary.verdict
            val label = when (v.kind) {
                VerdictKind.TANPA_GAJI -> "TANPA GAJI"
                VerdictKind.BONCOS -> "BONCOS"
                VerdictKind.PAS_PASAN -> "PAS-PASAN"
                VerdictKind.BERHASIL_NABUNG -> "BERHASIL NABUNG"
            } + if (r.provisional) " (sementara)" else ""
            add(listOf(t("Verdict"), t(label)))
            if (v.amount > 0) add(listOf(t("Nominal verdict"), n(v.amount)))
            r.summary.targetCadangan?.let { add(listOf(t("Target Cadangan"), n(it))); add(listOf(t("Cadangan terkumpul"), n(r.summary.cadanganTerkumpul))) }
            add(listOf(t("Saku Sisa akhir"), n(r.summary.sakuSebelumDistribusi)))
            add(listOf(t("Hutang dibawa"), n(r.summary.hutangDibawa)))
        }
    }
    val transaksi = listOf(listOf(t("Tanggal"), t("Jenis"), t("Pos"), t("Slot"), t("Nominal"), t("Catatan"))) +
        txs.map { tx ->
            listOf(
                t(tx.date.toString()), t(txTypeLabel(tx)), t(name(tx.categoryId)),
                t(tx.slot?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""), n(signedForExport(tx)), t(tx.note ?: ""),
            )
        }
    val perKategori = listOf(listOf(t("Pos"), t("Budget"), t("Terpakai"), t("Sisa"), t("Status"))) +
        if (monthly != null) monthly.rows.map { r ->
            val status = when (r.status) {
                StockStatus.NORMAL -> "normal"
                StockStatus.WASPADA -> "waspada"
                StockStatus.LEBIH -> "lebih"
            }
            listOf(t(name(r.categoryId)), n(r.budget), n(r.used), n(r.remaining), t(status))
        } else spend.entries.sortedBy { cats[it.key]?.sortOrder ?: 0 }.map { (id, v) -> listOf(t(name(id)), t(""), n(v), t(""), t("")) }
    val harian = listOf(listOf(t("Tanggal"), t("Pos"), t("Jatah"), t("Terpakai"), t("Selisih"), t("Hutang akhir hari"), t("Ke Saku Sisa"))) +
        state.dailyLog.filter { !it.date.isBefore(range.from) && !it.date.isAfter(range.to) }.sortedWith(compareBy({ it.date }, { cats[it.categoryId]?.sortOrder ?: 0 }))
            .map { listOf(t(it.date.toString()), t(name(it.categoryId)), n(it.jatah), n(it.used), n(it.delta), n(it.debtAfter), n(it.toSaku)) }
    val akhirPekan = listOf(listOf(t("Sabtu"), t("Akhir pekan ke-"), t("Jatah"), t("Terpakai"), t("Ke/dari Saku Sisa"))) +
        state.weekendLog.filter { !it.window.saturday.isBefore(range.from) && !it.window.saturday.isAfter(range.to) }
            .map { listOf(t(it.window.saturday.toString()), n(it.index.toLong()), n(if (it.funded) it.allowance else 0), n(it.used), n(if (it.funded) it.toSaku else -it.used)) }
    return listOf(
        Sheet("Ringkasan", ringkasan),
        Sheet("Transaksi", transaksi),
        Sheet("Per Kategori", perKategori),
        Sheet("Harian Makan & Buah", harian),
        Sheet("Akhir Pekan Transport", akhirPekan),
    )
}
