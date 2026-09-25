package app.catatuang.feature.history

import app.catatuang.engine.Category
import app.catatuang.engine.Destination
import app.catatuang.engine.IncomeKind
import app.catatuang.engine.Pot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.WithdrawReason
import app.catatuang.engine.recordMonth
import app.catatuang.ui.format.monthName
import java.time.LocalDate
import java.time.YearMonth

/** Efek transaksi ke uang pegangan (7.3): + masuk, − keluar, 0 perpindahan internal. */
fun cashSign(tx: Tx): Long = when (tx.type) {
    TxType.EXPENSE, TxType.FIXED_PAYMENT, TxType.SAVING_DEPOSIT, TxType.UNRECORDED -> -tx.amount
    TxType.CORRECTION -> -(tx.signedAmount ?: tx.amount)
    TxType.SALARY, TxType.REFUND, TxType.SAVING_WITHDRAW, TxType.SURPLUS_FOUND -> tx.amount
    TxType.INCOME -> if (tx.pot != null) 0 else tx.amount
    TxType.DEBT_PAYOFF, TxType.CARRY_OVER -> 0
}

private fun potName(p: Pot?) = if (p == Pot.DANA_DARURAT) "Dana Darurat" else "Tabungan"

fun txTitle(tx: Tx, categories: List<Category>): String {
    val cat = categories.firstOrNull { it.id == tx.categoryId }?.name ?: "pos"
    return when (tx.type) {
        TxType.EXPENSE -> cat
        TxType.REFUND -> "Pengembalian · $cat"
        TxType.SALARY -> (if (tx.incomeKind != null) "Gaji tambahan " else "Gaji ") + (tx.refYearMonth?.let { monthName(it) } ?: "")
        TxType.INCOME -> incomeName(tx.incomeKind) + when {
            tx.pot != null -> " → ${potName(tx.pot)}"
            tx.destination == Destination.CATEGORY -> " → budget $cat"
            else -> ""
        }
        TxType.FIXED_PAYMENT -> "Bayar $cat"
        TxType.SAVING_DEPOSIT -> if (tx.routine) "Nabung rutin" else "Setor ${potName(tx.pot)}"
        TxType.SAVING_WITHDRAW -> "Ambil ${potName(tx.pot)} · " + when (tx.reason) {
            WithdrawReason.DARURAT -> "darurat"
            WithdrawReason.TANPA_GAJI -> "tanpa gaji"
            else -> "rencana"
        }
        TxType.DEBT_PAYOFF -> "Lunasi hutang ${cat.lowercase()}"
        TxType.CARRY_OVER -> "Bawa ke ${tx.refYearMonth?.let { monthName(it) } ?: "bulan depan"}"
        TxType.UNRECORDED -> "Tidak tercatat"
        TxType.SURPLUS_FOUND -> "Selisih lebih"
        TxType.CORRECTION -> "Koreksi $cat"
    }
}

private fun incomeName(kind: IncomeKind?) = when (kind) {
    IncomeKind.PEMBERIAN -> "Pemberian"
    IncomeKind.SAMPINGAN -> "Penghasilan sampingan"
    IncomeKind.THR_BONUS -> "THR / Bonus"
    else -> "Pemasukan"
}

/** Hanya transaksi harian yang diedit/dihapus langsung dari Riwayat; sisanya lewat alurnya (D-26). */
fun isEditable(tx: Tx): Boolean = tx.type == TxType.EXPENSE || tx.type == TxType.REFUND || tx.type == TxType.INCOME

data class DayGroup(val date: LocalDate, val spent: Long, val items: List<Tx>)

/**
 * 8.9: transaksi bulan akuntansi [month], dikelompokkan per tanggal (terbaru dulu), total hari =
 * pengeluaran bersih (pengeluaran − pengembalian ± koreksi).
 */
fun historyGroups(transactions: List<Tx>, categories: List<Category>, month: YearMonth, filter: Long?): List<DayGroup> =
    transactions.filter { recordMonth(it, categories) == month && (filter == null || it.categoryId == filter) }
        .groupBy { it.date }
        .toSortedMap(compareByDescending { it })
        .map { (date, list) ->
            val spent = list.sumOf {
                when (it.type) {
                    TxType.EXPENSE -> it.amount
                    TxType.REFUND -> -it.amount
                    TxType.CORRECTION -> it.signedAmount ?: it.amount
                    else -> 0
                }
            }
            DayGroup(date, spent, list.sortedByDescending { it.createdAt })
        }
