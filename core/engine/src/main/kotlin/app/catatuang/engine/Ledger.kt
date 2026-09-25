package app.catatuang.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class SalaryStatus { ONBOARDING, RECEIVED, MISSING, NO_SALARY }

/** Log penutupan hari untuk satu pos HARIAN (R-22). */
data class DayClose(
    val date: LocalDate,
    val categoryId: Long,
    val jatah: Long,
    val used: Long,
    val delta: Long,
    val paidDebt: Long,
    val debtAfter: Long,
    val toSaku: Long,
)

/** Log penutupan jendela akhir pekan (R-37). */
data class WeekendClose(
    val window: WeekendWindow,
    val index: Int,
    val funded: Boolean,
    val allowance: Long,
    val used: Long,
    /** Efek ke Saku Sisa saat jendela ditutup (akhir pekan ke-5: 0, karena sudah langsung). */
    val toSaku: Long,
)

data class WindowInfo(
    val window: WeekendWindow,
    val index: Int,
    /** Akhir pekan ke-1..4 dibiayai budget Transport; ke-5 dari reservasi. */
    val funded: Boolean,
    val used: Long,
    val txCount: Int,
    val closed: Boolean,
)

data class StockInfo(
    val categoryId: Long,
    val budget: Long,
    val used: Long,
    val status: StockStatus,
    /** R-39: sisa yang dicairkan ke Saku Sisa di akhir bulan (0 sebelum bulan berakhir). */
    val residualToSaku: Long,
)

data class FixedInfo(val categoryId: Long, val estimate: Long, val paid: Long?, val cancelled: Boolean) {
    val isPaid: Boolean get() = paid != null
}

/** Ringkasan satu bulan akuntansi. */
data class MonthSummary(
    val month: YearMonth,
    val isOnboarding: Boolean,
    val salary: Long?,
    val salaryActivation: LocalDate?,
    val funded: Boolean,
    val noSalary: Boolean,
    val closed: Boolean,
    val allocation: List<AllocationLine>,
    val days: Int,
    val saturdays: Int,
    val kebutuhan: Long,
    val tripTambahan: Long,
    val weekendAllowance: Long,
    val transportBudget: Long,
    val bawaan: Long,
    val sakuSisaAwal: Long,
    val sakuSisa: Long,
    /** Saku Sisa akhir sebelum distribusi langkah 6 (R-72 / ke kantong / bawa) — dipakai verdict. */
    val sakuSebelumDistribusi: Long,
    val reservasi: Long,
    val sisaBebas: Long,
    val targetCadangan: Long?,
    val cadanganTerkumpul: Long,
    val gajiTambahan: Long,
    val pemasukan: Long,
    val pengeluaranRiil: Long,
    val tabunganMasuk: Long,
    val tabunganKeluar: Long,
    val danaDaruratMasuk: Long,
    val danaDaruratTerpakai: Long,
    val daruratWithdrawn: Long,
    val rencanaWithdrawn: Long,
    val tanpaGajiWithdrawn: Long,
    val nabungRutin: Long,
    val movedToPots: Long,
    val carriedOut: Long,
    val hutangMasuk: Long,
    val hutangDibawa: Long,
    val monthEnded: Boolean,
    /** R-32: jendela akhir pekan terakhir milik bulan ini sudah tertutup. */
    val lastWindowClosed: Boolean,
    val windows: List<WindowInfo>,
    val stock: List<StockInfo>,
    val fixed: List<FixedInfo>,
    val transportResidualToSaku: Long,
    val verdict: Verdict,
) {
    /** Bisa menyelesaikan Tutup Buku (bulan berakhir & R-32). */
    val closable: Boolean get() = monthEnded && lastWindowClosed
}

data class DailyToday(
    val categoryId: Long,
    val jatah: Long,
    val used: Long,
    val bySlot: Map<Slot, Long>,
    val hutang: Long,
    /** Hutang jika hari ini ditutup sekarang (preview, notifikasi 22:00). */
    val projectedHutang: Long,
    val largeDebt: Boolean,
) {
    val remaining: Long get() = jatah - used
}

data class TransportToday(
    val categoryId: Long,
    val month: YearMonth,
    /** Jendela yang sedang terbuka hari ini (null di luar Jumat–Minggu). */
    val openWindow: WindowInfo?,
    val openWindowMonth: YearMonth?,
    val allowance: Long,
    val tripsTaken: Int,
    val saturdays: Int,
    val remainingBudget: Long,
    val windowStatus: StockStatus?,
)

data class Talangan(
    val amount: Long,
    val fromSaku: Long,
    val fromTabungan: Long,
    val fromDanaDarurat: Long,
    /** "Dana talangan habis, kurang Rp X". */
    val shortfall: Long,
)

data class LedgerState(
    val today: LocalDate,
    val currentMonth: YearMonth,
    val months: Map<YearMonth, MonthSummary>,
    val salaryStatus: SalaryStatus,
    val sakuSisa: Long,
    val reservasi: Long,
    val sisaBebas: Long,
    val sisaBebasStatus: SafetyStatus,
    val targetCadangan: Long?,
    val cadanganTerkumpul: Long,
    val hutang: Map<Long, Long>,
    val largeDebt: Set<Long>,
    val tabungan: Long,
    val danaDarurat: Long,
    /** Saldo kantong yang tampil (dikurangi porsi talangan bayangan, R-04). */
    val tabunganShown: Long,
    val danaDaruratShown: Long,
    val uangPegangan: Long,
    val saldoPending: Long,
    val pendingMonth: YearMonth?,
    val talangan: Talangan?,
    val daily: List<DailyToday>,
    val stock: List<StockInfo>,
    val transport: TransportToday?,
    val dailyLog: List<DayClose>,
    val weekendLog: List<WeekendClose>,
    /** Saku Sisa & Sisa bebas bulan berjalan jika hari ini ditutup sekarang. */
    val projectedSakuSisa: Long,
    val projectedSisaBebas: Long,
) {
    val current: MonthSummary? get() = months[currentMonth]
    val totalHutang: Long get() = hutang.values.sum()
}

/**
 * 7.2 `computeLedger`: memutar ulang semua transaksi sejak tanggal mulai. Hari yang sudah tertutup
 * (R-21) diproses penuh; hari ini hanya preview ([LedgerState.daily] dan nilai `projected*`).
 */
fun computeLedger(input: LedgerInput, today: LocalDate): LedgerState {
    val real = Replay(input, today, closeThrough = today.minusDays(1)).run()
    val projected = Replay(input, today, closeThrough = today).run()
    return real.toState(projected)
}

/** Satu kali putar ulang dengan hari-hari s/d [closeThrough] dianggap tertutup. */
internal class Replay(
    private val input: LedgerInput,
    private val today: LocalDate,
    private val closeThrough: LocalDate,
) {
    private val categories = input.categories
    private val catById = categories.associateBy { it.id }
    private val start = input.onboarding.startDate
    private val firstMonth = YearMonth.from(start)
    private val currentMonth = YearMonth.from(today)
    private fun isWeekend(id: Long) = catById[id]?.weekendMode == true

    private val txs: List<Tx> = input.transactions
        .filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        .sortedWith(compareBy<Tx>({ it.date }, { it.createdAt }, { it.id }))
    private val txMonth: Map<Long, YearMonth> = txs.associate { it.id to accountingMonth(it, ::isWeekend) }
    private val lastMonth: YearMonth = (txMonth.values + currentMonth).max()
    private val months: List<YearMonth> = generateSequence(firstMonth) { it.plusMonths(1) }.takeWhile { it <= lastMonth }.toList()

    private val salaryByMonth: Map<YearMonth, Tx> = txs.filter { it.type == TxType.SALARY }
        .groupBy { txMonth.getValue(it.id) }.mapValues { it.value.first() }

    private val ctx: Map<YearMonth, MonthCtx> = months.associateWith { MonthCtx(it) }
    private val acc: Map<YearMonth, MonthAcc> = months.associateWith { MonthAcc() }

    private val hutang = linkedMapOf<Long, Long>()
    private var tabungan = input.onboarding.savingsStart
    private var danaDarurat = input.onboarding.emergencyStart
    private var cash = input.onboarding.cashStart
    private val dailyLog = mutableListOf<DayClose>()
    private val weekendLog = mutableListOf<WeekendClose>()

    inner class MonthCtx(val month: YearMonth) {
        val isOnboarding = month == firstMonth
        val plan = input.plan(month)
        val salary: Tx? = salaryByMonth[month]
        val noSalary = plan?.noSalary == true && salary == null && !isOnboarding
        val funded = isOnboarding || salary != null
        val onboardingPlan: OnboardingPlan? = if (isOnboarding) planOnboarding(start, input.onboarding.cashStart, categories) else null
        val allocation: List<AllocationLine> = onboardingPlan?.allocation
            ?: (if (salary != null) plan?.allocation else null)
            ?: input.template(month)
        val lines = allocation.associateBy { it.categoryId }
        val days = month.lengthOfMonth()
        val dailyDays = if (isOnboarding) days - start.dayOfMonth + 1 else days
        val windows: List<WeekendWindow> = windowsFrom(month, if (isOnboarding) start else null)
        val transportId: Long? = categories.firstOrNull { it.weekendMode }?.id
        val transportBudget: Long = transportId?.let { lines[it]?.monthlyAmount } ?: 0
        val allowance: Long = when {
            onboardingPlan != null -> transportId?.let { id -> input.template(month).firstOrNull { it.categoryId == id } }
                ?.let { weekendAllowance(it.monthlyAmount) } ?: 0
            else -> weekendAllowance(transportBudget)
        }
        val fundedWindows = minOf(4, windows.size)
        val kebutuhan: Long = onboardingPlan?.kebutuhan ?: kebutuhan(allocation, categories, days)
        val trip: Long = maxOf(0, windows.size - 4) * allowance
        fun windowIndex(w: WeekendWindow): Int = windows.indexOfFirst { it.saturday == w.saturday } + 1
    }

    class MonthAcc {
        var saku = 0L
        val dailyUsed = mutableMapOf<LocalDate, MutableMap<Long, Long>>()
        val stockUsed = mutableMapOf<Long, Long>()
        val stockExtra = mutableMapOf<Long, Long>()
        val windowUsed = mutableMapOf<LocalDate, Long>()
        val windowTx = mutableMapOf<LocalDate, Int>()
        val fixedPaid = mutableMapOf<Long, Long>()
        var gajiTambahan = 0L
        var pemasukan = 0L
        var riil = 0L
        var tabMasuk = 0L
        var tabKeluar = 0L
        var ddMasuk = 0L
        var ddKeluar = 0L
        var darurat = 0L
        var rencana = 0L
        var tanpaGaji = 0L
        var nabungRutin = 0L
        var movedToPots = 0L
        var coverIn = 0L
        var carriedOut = 0L
        var otherCashFlows = 0L
        var closingPayoff = 0L
        var hutangMasuk = 0L
        var hutangAtEnd = 0L
        var monthEnded = false
    }

    private fun accOf(tx: Tx): Pair<MonthCtx, MonthAcc>? {
        val m = txMonth.getValue(tx.id)
        val c = ctx[m] ?: return null
        return c to acc.getValue(m)
    }

    fun run(): Replay {
        var d = start
        val stop = today.plusDays(1)
        while (!d.isAfter(stop)) {
            val prev = d.minusDays(1)
            if (!prev.isBefore(start) && !prev.isAfter(closeThrough)) {
                closeDay(prev)
                if (prev.dayOfWeek == DayOfWeek.SUNDAY) closeWindow(WeekendWindow(prev.minusDays(1)))
                if (prev == YearMonth.from(prev).lastDay()) endMonth(YearMonth.from(prev))
            }
            if (d.dayOfMonth == 1) acc[YearMonth.from(d)]?.let { it.hutangMasuk = hutang.values.sum() }
            if (!d.isAfter(today)) txs.filter { it.date == d }.forEach(::apply)
            d = d.plusDays(1)
        }
        return this
    }

    /** R-22. */
    private fun closeDay(date: LocalDate) {
        val m = YearMonth.from(date)
        val c = ctx[m] ?: return
        val a = acc.getValue(m)
        c.allocation.filter { catById[it.categoryId]?.kind == CategoryKind.DAILY }.forEach { line ->
            val id = line.categoryId
            val jatah = line.dailyAmount ?: 0
            val used = a.dailyUsed[date]?.get(id) ?: 0
            val delta = jatah - used
            val debt = hutang[id] ?: 0
            if (delta >= 0) {
                val pay = minOf(delta, debt)
                hutang[id] = debt - pay
                a.saku += delta - pay
                dailyLog += DayClose(date, id, jatah, used, delta, pay, debt - pay, delta - pay)
            } else {
                hutang[id] = debt - delta
                dailyLog += DayClose(date, id, jatah, used, delta, 0, debt - delta, 0)
            }
        }
    }

    /** R-37. */
    private fun closeWindow(w: WeekendWindow) {
        val c = ctx[w.owner] ?: return
        val index = c.windowIndex(w)
        if (index == 0) return
        val a = acc.getValue(w.owner)
        val used = a.windowUsed[w.saturday] ?: 0
        val funded = index <= c.fundedWindows
        val toSaku = if (funded) c.allowance - used else 0
        a.saku += toSaku
        weekendLog += WeekendClose(w, index, funded, c.allowance, used, toSaku)
    }

    /** Akhir bulan: R-39/R-17 (dihitung di [summary]), bulan tanpa gaji menol-kan hutang (6.10 langkah 1). */
    private fun endMonth(m: YearMonth) {
        val c = ctx[m] ?: return
        val a = acc.getValue(m)
        a.monthEnded = true
        if (c.noSalary) hutang.keys.toList().forEach { hutang[it] = 0 }
        a.hutangAtEnd = hutang.values.sum()
    }

    private fun apply(tx: Tx) {
        val (c, a) = accOf(tx) ?: return
        val amt = tx.amount
        when (tx.type) {
            TxType.SALARY -> {
                cash += amt
                if (c.salary?.id != tx.id) { a.saku += amt; a.gajiTambahan += amt }
            }
            TxType.INCOME -> when (tx.destination ?: Destination.SAKU) {
                Destination.POT -> depositPot(tx.pot ?: Pot.TABUNGAN, amt, a)
                Destination.CATEGORY -> {
                    val id = tx.categoryId
                    cash += amt; a.pemasukan += amt
                    if (id != null && catById[id]?.kind == CategoryKind.STOCK && !isWeekend(id)) {
                        a.stockExtra.merge(id, amt, Long::plus)
                    } else {
                        a.saku += amt
                    }
                }
                Destination.SAKU -> { cash += amt; a.pemasukan += amt; a.saku += amt }
            }
            TxType.REFUND -> { cash += amt; a.riil -= amt; categoryEffect(tx, -amt, c, a) }
            TxType.EXPENSE -> { cash -= amt; a.riil += amt; categoryEffect(tx, amt, c, a) }
            TxType.CORRECTION -> {
                val s = tx.signedAmount ?: amt
                cash -= s; a.riil += s; categoryEffect(tx, s, c, a)
            }
            TxType.FIXED_PAYMENT -> {
                cash -= amt; a.riil += amt
                val id = tx.categoryId
                val line = id?.let { c.lines[it] }
                if (id != null && line != null && catById[id]?.kind == CategoryKind.FIXED && id !in a.fixedPaid) {
                    a.saku += line.monthlyAmount - amt
                } else {
                    a.saku -= amt
                }
                if (id != null) a.fixedPaid.merge(id, amt, Long::plus)
            }
            TxType.SAVING_DEPOSIT -> {
                val pot = tx.pot ?: Pot.TABUNGAN
                cash -= amt
                if (pot == Pot.TABUNGAN) { tabungan += amt; a.tabMasuk += amt } else { danaDarurat += amt; a.ddMasuk += amt }
                when {
                    tx.routine -> a.nabungRutin += amt
                    tx.closingOf != null -> { a.saku -= amt; a.movedToPots += amt }
                    else -> { a.saku -= amt; a.otherCashFlows -= amt }
                }
            }
            TxType.SAVING_WITHDRAW -> {
                val pot = tx.pot ?: Pot.TABUNGAN
                cash += amt
                if (pot == Pot.TABUNGAN) { tabungan -= amt; a.tabKeluar += amt } else { danaDarurat -= amt; a.ddKeluar += amt }
                a.saku += amt
                when (tx.reason ?: WithdrawReason.RENCANA) {
                    WithdrawReason.DARURAT -> a.darurat += amt
                    WithdrawReason.RENCANA -> a.rencana += amt
                    WithdrawReason.TANPA_GAJI -> a.tanpaGaji += amt
                }
                if (tx.closingOf != null) a.coverIn += amt else a.otherCashFlows += amt
            }
            TxType.DEBT_PAYOFF -> {
                a.saku -= amt
                val id = tx.categoryId
                if (id != null) hutang[id] = maxOf(0, (hutang[id] ?: 0) - amt)
                if (tx.closingOf != null) a.closingPayoff += amt
            }
            TxType.CARRY_OVER -> { a.saku -= amt; a.carriedOut += amt }
            TxType.UNRECORDED -> { cash -= amt; a.riil += amt; a.saku -= amt }
            TxType.SURPLUS_FOUND -> { cash += amt; a.pemasukan += amt; a.saku += amt }
        }
    }

    private fun depositPot(pot: Pot, amt: Long, a: MonthAcc) {
        if (pot == Pot.TABUNGAN) { tabungan += amt; a.tabMasuk += amt } else { danaDarurat += amt; a.ddMasuk += amt }
    }

    /** Efek terpakai pos: HARIAN per hari, STOK per bulan, Transport per jendela / trip tambahan (R-37, R-38). */
    private fun categoryEffect(tx: Tx, x: Long, c: MonthCtx, a: MonthAcc) {
        val id = tx.categoryId ?: run { a.saku -= x; return }
        val cat = catById[id] ?: run { a.saku -= x; return }
        when (cat.kind) {
            CategoryKind.DAILY -> a.dailyUsed.getOrPut(tx.date) { mutableMapOf() }.merge(id, x, Long::plus)
            CategoryKind.STOCK -> if (cat.weekendMode) {
                val w = weekendWindowOf(tx.date)
                val index = w?.let(c::windowIndex) ?: 0
                if (w != null && index > 0) {
                    a.windowUsed.merge(w.saturday, x, Long::plus)
                    if (tx.type == TxType.EXPENSE) a.windowTx.merge(w.saturday, 1, Int::plus)
                    if (index > c.fundedWindows) a.saku -= x
                } else {
                    a.saku -= x
                }
            } else {
                a.stockUsed.merge(id, x, Long::plus)
            }
            else -> a.saku -= x
        }
    }

    private fun lastWindowOf(c: MonthCtx): WeekendWindow? = c.windows.lastOrNull()

    fun summary(m: YearMonth): MonthSummary {
        val c = ctx.getValue(m)
        val a = acc.getValue(m)
        val bawaan = txs.filter { it.type == TxType.CARRY_OVER && it.refYearMonth == m }.sumOf { it.amount }
        val salary = c.salary?.amount
        val sakuAwal = when {
            c.isOnboarding -> input.onboarding.cashStart - c.kebutuhan + (salary ?: 0) + bawaan
            salary != null -> salary - c.kebutuhan + bawaan
            else -> bawaan
        }
        // R-31 / R-39: STOK non-Transport.
        val stockIds = (c.allocation.map { it.categoryId } + a.stockUsed.keys + a.stockExtra.keys).distinct()
            .filter { id -> catById[id]?.let { it.kind == CategoryKind.STOCK && !it.weekendMode } == true }
        val stock = stockIds.map { id ->
            val budget = (c.lines[id]?.monthlyAmount ?: 0) + (a.stockExtra[id] ?: 0)
            val used = a.stockUsed[id] ?: 0
            val residual = if (a.monthEnded) maxOf(0, budget - used) else 0
            StockInfo(id, budget, used, stockStatus(used, budget), residual)
        }
        val stockEffect = stock.sumOf { it.residualToSaku - maxOf(0, it.used - it.budget) }
        // R-17: sisa pembagian budget Transport dicairkan di akhir bulan.
        val transportResidual = if (a.monthEnded) maxOf(0, c.transportBudget - c.fundedWindows * c.allowance) else 0
        val cancelled = input.plan(m)?.cancelledFixed.orEmpty()
        val fixed = c.allocation.filter { catById[it.categoryId]?.kind == CategoryKind.FIXED && !c.isOnboarding }.map {
            FixedInfo(it.categoryId, it.monthlyAmount, a.fixedPaid[it.categoryId], it.categoryId in cancelled && it.categoryId !in a.fixedPaid)
        }
        val cancelledBack = fixed.filter { it.cancelled }.sumOf { it.estimate }

        val saku: Long
        val beforeDistribution: Long
        if (c.noSalary) {
            // 6.10 langkah 1: bawaan − hutang masuk + pemasukan − pengeluaran riil (+ arus kantong manual, D-12).
            val cashBased = bawaan - a.hutangMasuk + a.pemasukan + a.gajiTambahan - a.riil + a.otherCashFlows
            beforeDistribution = cashBased
            saku = cashBased + a.coverIn - a.movedToPots - a.carriedOut
        } else {
            saku = sakuAwal + a.saku + stockEffect + transportResidual + cancelledBack
            beforeDistribution = saku - a.coverIn + a.movedToPots + a.carriedOut
        }

        val windowInfos = c.windows.mapIndexed { i, w ->
            WindowInfo(w, i + 1, i + 1 <= c.fundedWindows, a.windowUsed[w.saturday] ?: 0, a.windowTx[w.saturday] ?: 0, w.isClosedBy(closeThrough))
        }
        // R-15: reservasi akhir pekan ke-5.
        val reservasi = windowInfos.filter { !it.funded }.sumOf { if (it.closed) 0 else maxOf(0, c.allowance - it.used) }
        val sisaBebas = saku - reservasi
        val target = if (c.funded) maxOf(0, c.trip - sakuAwal) else null
        val terkumpul = target?.let { (it - maxOf(0, -sisaBebas)).coerceIn(0, it) } ?: 0
        val hutangDibawa = if (a.monthEnded) maxOf(0, a.hutangAtEnd - a.closingPayoff) else hutang.values.sum()
        val lastWindowClosed = lastWindowOf(c)?.isClosedBy(closeThrough) ?: true
        val verdict = computeVerdict(
            noSalary = c.noSalary,
            daruratWithdrawn = a.darurat,
            sakuAkhir = beforeDistribution,
            hutangDibawa = hutangDibawa,
            nabungRutin = a.nabungRutin,
            movedToPots = a.movedToPots,
            safeThreshold = input.config.safeThreshold,
        )
        return MonthSummary(
            month = m,
            isOnboarding = c.isOnboarding,
            salary = salary,
            salaryActivation = c.salary?.let { maxOf(it.date, m.atDay(1)) },
            funded = c.funded,
            noSalary = c.noSalary,
            closed = c.plan?.closed == true,
            allocation = c.allocation,
            days = c.days,
            saturdays = countSaturdays(m),
            kebutuhan = c.kebutuhan,
            tripTambahan = c.trip,
            weekendAllowance = c.allowance,
            transportBudget = c.transportBudget,
            bawaan = bawaan,
            sakuSisaAwal = sakuAwal,
            sakuSisa = saku,
            sakuSebelumDistribusi = beforeDistribution,
            reservasi = reservasi,
            sisaBebas = sisaBebas,
            targetCadangan = target,
            cadanganTerkumpul = terkumpul,
            gajiTambahan = a.gajiTambahan,
            pemasukan = a.pemasukan,
            pengeluaranRiil = a.riil,
            tabunganMasuk = a.tabMasuk,
            tabunganKeluar = a.tabKeluar,
            danaDaruratMasuk = a.ddMasuk,
            danaDaruratTerpakai = a.ddKeluar,
            daruratWithdrawn = a.darurat,
            rencanaWithdrawn = a.rencana,
            tanpaGajiWithdrawn = a.tanpaGaji,
            nabungRutin = a.nabungRutin,
            movedToPots = a.movedToPots,
            carriedOut = a.carriedOut,
            hutangMasuk = a.hutangMasuk,
            hutangDibawa = hutangDibawa,
            monthEnded = a.monthEnded,
            lastWindowClosed = lastWindowClosed,
            windows = windowInfos,
            stock = stock,
            fixed = fixed,
            transportResidualToSaku = transportResidual,
            verdict = verdict,
        )
    }

    fun toState(projected: Replay): LedgerState {
        val summaries = months.associateWith(::summary)
        val cur = summaries.getValue(currentMonth)
        val c = ctx.getValue(currentMonth)
        val a = acc.getValue(currentMonth)
        val projCur = projected.summary(currentMonth)

        val status = when {
            c.isOnboarding -> SalaryStatus.ONBOARDING
            c.salary != null -> SalaryStatus.RECEIVED
            c.noSalary -> SalaryStatus.NO_SALARY
            else -> SalaryStatus.MISSING
        }
        val pendingTx = txs.filter { it.type == TxType.SALARY && txMonth.getValue(it.id) > currentMonth }
        val talangan = if (status == SalaryStatus.MISSING) talanganOf(cur, summaries) else null

        val daily = c.allocation.filter { catById[it.categoryId]?.kind == CategoryKind.DAILY }.map { line ->
            val id = line.categoryId
            val jatah = line.dailyAmount ?: 0
            val todayTx = txs.filter { it.categoryId == id && it.date == today }
            val bySlot = todayTx.filter { it.slot != null }.groupBy { it.slot!! }.mapValues { (_, list) ->
                list.sumOf { t -> signedUse(t) }
            }
            val h = hutang[id] ?: 0
            DailyToday(id, jatah, a.dailyUsed[today]?.get(id) ?: 0, bySlot, h, projected.hutang[id] ?: 0, isLargeDebt(h, jatah))
        }
        val transport = c.transportId?.let { tid ->
            val open = weekendWindowOf(today)
            val ownerSummary = open?.let { summaries[it.owner] }
            val openInfo = ownerSummary?.windows?.firstOrNull { it.window.saturday == open.saturday }
            val allowance = ownerSummary?.weekendAllowance ?: cur.weekendAllowance
            val used = cur.windows.filter { it.funded }.sumOf { if (it.closed) cur.weekendAllowance else it.used }
            TransportToday(
                categoryId = tid,
                month = currentMonth,
                openWindow = openInfo,
                openWindowMonth = openInfo?.let { open.owner },
                allowance = allowance,
                tripsTaken = cur.windows.count { it.txCount > 0 },
                saturdays = cur.saturdays,
                remainingBudget = cur.transportBudget - used,
                windowStatus = openInfo?.let { stockStatus(it.used, if (it.funded) allowance else allowance) },
            )
        }
        val tabShown = tabungan - (talangan?.fromTabungan ?: 0)
        val ddShown = danaDarurat - (talangan?.fromDanaDarurat ?: 0)
        return LedgerState(
            today = today,
            currentMonth = currentMonth,
            months = summaries,
            salaryStatus = status,
            sakuSisa = cur.sakuSisa,
            reservasi = cur.reservasi,
            sisaBebas = cur.sisaBebas,
            sisaBebasStatus = safetyStatus(cur.sisaBebas, input.config.safeThreshold),
            targetCadangan = cur.targetCadangan,
            cadanganTerkumpul = cur.cadanganTerkumpul,
            hutang = hutang.toMap(),
            largeDebt = daily.filter { it.largeDebt }.map { it.categoryId }.toSet(),
            tabungan = tabungan,
            danaDarurat = danaDarurat,
            tabunganShown = tabShown,
            danaDaruratShown = ddShown,
            uangPegangan = cash,
            saldoPending = pendingTx.sumOf { it.amount },
            pendingMonth = pendingTx.firstOrNull()?.let { txMonth.getValue(it.id) },
            talangan = talangan,
            daily = daily,
            stock = cur.stock,
            transport = transport,
            dailyLog = dailyLog.toList(),
            weekendLog = weekendLog.toList(),
            projectedSakuSisa = projCur.sakuSisa,
            projectedSisaBebas = projCur.sisaBebas,
        )
    }

    private fun signedUse(t: Tx): Long = when (t.type) {
        TxType.EXPENSE -> t.amount
        TxType.REFUND -> -t.amount
        TxType.CORRECTION -> t.signedAmount ?: t.amount
        else -> 0
    }

    /**
     * R-04: talangan bulan berjalan = pengeluaran riil sejak tanggal 1, dibebankan bayangan ke
     * Saku (bawaan / Saku akhir bulan lalu + pemasukan bulan ini, D-11) → Tabungan → Dana Darurat.
     */
    private fun talanganOf(cur: MonthSummary, summaries: Map<YearMonth, MonthSummary>): Talangan {
        val amount = maxOf(0, cur.pengeluaranRiil)
        val prev = summaries[currentMonth.minusMonths(1)]
        val carry = when {
            prev == null -> cur.bawaan
            prev.closed -> cur.bawaan
            else -> maxOf(0, prev.sakuSisa)
        }
        val first = carry + cur.pemasukan + cur.gajiTambahan
        val cover = coverShortfall(amount, first, tabungan, danaDarurat)
        return Talangan(amount, cover.fromSaku, cover.fromTabungan, cover.fromDanaDarurat, cover.uncovered)
    }
}
