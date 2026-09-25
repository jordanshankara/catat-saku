package app.catatuang.engine

import app.catatuang.engine.Defaults.ID_AI
import app.catatuang.engine.Defaults.ID_BUAH
import app.catatuang.engine.Defaults.ID_IURAN_MESS
import app.catatuang.engine.Defaults.ID_LAIN
import app.catatuang.engine.Defaults.ID_MAKAN
import app.catatuang.engine.Defaults.ID_PROTEIN
import app.catatuang.engine.Defaults.ID_TRANSPORT
import java.time.LocalDate
import java.time.YearMonth

fun d(s: String): LocalDate = LocalDate.parse(s)
fun ym(s: String): YearMonth = YearMonth.parse(s)

/** Pembangun skenario test dengan konfigurasi default bagian 5. */
class Scenario(
    val start: LocalDate,
    var cash: Long = 0,
    var savings: Long = 500_000,
    var emergency: Long = 1_000_000,
    val categories: List<Category> = Defaults.categories,
) {
    val txs = mutableListOf<Tx>()
    val plans = mutableMapOf<YearMonth, MonthPlan>()
    private var nextId = 1L

    fun add(tx: Tx): Tx {
        val t = tx.copy(id = nextId, createdAt = nextId)
        nextId++
        txs += t
        return t
    }

    fun tx(date: String, type: TxType, amount: Long, block: Tx.() -> Tx = { this }): Tx =
        add(Tx(0, d(date), type, amount).block())

    fun expense(date: String, cat: Long, amount: Long, slot: Slot? = null) =
        tx(date, TxType.EXPENSE, amount) { copy(categoryId = cat, slot = slot) }

    fun refund(date: String, cat: Long, amount: Long) = tx(date, TxType.REFUND, amount) { copy(categoryId = cat) }

    fun salary(date: String, target: String, amount: Long = 3_300_000) =
        tx(date, TxType.SALARY, amount) { copy(refYearMonth = ym(target)) }

    fun income(date: String, amount: Long, kind: IncomeKind = IncomeKind.PEMBERIAN): Tx {
        val (dest, pot) = defaultIncomeDestination(kind)
        return tx(date, TxType.INCOME, amount) { copy(destination = dest, pot = pot, incomeKind = kind) }
    }

    fun withdraw(date: String, pot: Pot, amount: Long, reason: WithdrawReason, closingOf: String? = null) =
        tx(date, TxType.SAVING_WITHDRAW, amount) { copy(pot = pot, reason = reason, closingOf = closingOf?.let(::ym)) }

    fun deposit(date: String, pot: Pot, amount: Long, closingOf: String? = null) =
        tx(date, TxType.SAVING_DEPOSIT, amount) { copy(pot = pot, closingOf = closingOf?.let(::ym)) }

    fun routineDeposit(date: String, month: String, amount: Long = 160_000) =
        tx(date, TxType.SAVING_DEPOSIT, amount) { copy(pot = Pot.TABUNGAN, routine = true, refYearMonth = ym(month)) }

    fun payoff(date: String, cat: Long, amount: Long, closingOf: String? = null) =
        tx(date, TxType.DEBT_PAYOFF, amount) { copy(categoryId = cat, closingOf = closingOf?.let(::ym)) }

    fun carry(date: String, from: String, amount: Long) =
        tx(date, TxType.CARRY_OVER, amount) { copy(closingOf = ym(from), refYearMonth = ym(from).plusMonths(1)) }

    fun fixedPay(date: String, cat: Long, amount: Long) = tx(date, TxType.FIXED_PAYMENT, amount) { copy(categoryId = cat) }

    fun plan(month: String, block: MonthPlan.() -> MonthPlan) {
        val m = ym(month)
        plans[m] = (plans[m] ?: MonthPlan(m)).block()
    }

    /** Belanja pas jatah/budget sepanjang bulan: HARIAN tiap hari, Transport J tiap akhir pekan 1–4, STOK & TETAP pas. */
    fun spendExactly(month: String, skipBuahDays: Int = 0) {
        val m = ym(month)
        for (day in 1..m.lengthOfMonth()) {
            val date = m.atDay(day).toString()
            expense(date, ID_MAKAN, 50_000)
            if (day > skipBuahDays) expense(date, ID_BUAH, 10_000)
        }
        saturdaysOf(m).take(4).forEach { expense(it.toString(), ID_TRANSPORT, 120_000) }
        expense(m.atDay(1).toString(), ID_PROTEIN, 200_000)
        expense(m.atDay(1).toString(), ID_LAIN, 150_000)
        fixedPay(m.atDay(1).toString(), ID_IURAN_MESS, 120_000)
        fixedPay(m.atDay(1).toString(), ID_AI, 390_000)
    }

    fun input(): LedgerInput = LedgerInput(
        categories = categories,
        onboarding = Onboarding(start, cash, savings, emergency),
        transactions = txs.toList(),
        plans = plans.values.toList(),
    )

    fun at(today: String): LedgerState = computeLedger(input(), d(today))

    companion object {
        /**
         * Bulan [month] dibiayai gaji 3.300.000 yang diterima tepat waktu. Onboarding di hari terakhir bulan
         * sebelumnya supaya bulan [month] bersih (tanpa bawaan).
         */
        fun funded(month: String, salary: Long = 3_300_000): Scenario {
            val m = ym(month)
            val start = m.minusMonths(1).atEndOfMonth()
            return Scenario(start).apply { salary(start.toString(), month, salary) }
        }
    }
}
