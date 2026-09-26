package app.catatuang.engine

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/* Tutup Buku (6.10): transaksi yang dibuat tiap langkah. Semua bertanda `closingOf = M`, bertanggal hari
 * pembuatan (D-08), sehingga verdict & saldo bulan M dihitung ulang oleh computeLedger. */

/** Bulan tertua yang sudah berakhir tetapi belum ditutup (Tutup Buku tertunda). */
fun pendingClosing(state: LedgerState): MonthSummary? =
    state.months.values.filter { it.monthEnded && !it.closed }.minByOrNull { it.month }

/** Hutang per pos HARIAN saat hari terakhir bulan [month] tertutup. */
fun debtAtMonthEnd(state: LedgerState, month: YearMonth): Map<Long, Long> {
    val last = month.atEndOfMonth()
    return state.dailyLog.filter { it.date == last }.associate { it.categoryId to it.debtAfter }
}

/**
 * Langkah 4 "Lunasi pakai Saku Sisa": nominal per pos = hutang akhir bulan yang belum dilunasi di Tutup Buku,
 * dibatasi hutang sekarang (hemat hari-hari bulan baru bisa sudah melunasi sebagian, R-24).
 */
fun closingDebts(input: LedgerInput, state: LedgerState, month: YearMonth): Map<Long, Long> {
    val paid = input.transactions.filter { it.type == TxType.DEBT_PAYOFF && it.closingOf == month }
        .groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amount } }
    return debtAtMonthEnd(state, month).mapNotNull { (id, end) ->
        val left = minOf(end - (paid[id] ?: 0), state.hutang[id] ?: 0)
        if (left > 0) id to left else null
    }.toMap()
}

fun debtPayoffTx(month: YearMonth, categoryId: Long, amount: Long, date: LocalDate): Tx =
    Tx(0, date, TxType.DEBT_PAYOFF, amount, categoryId = categoryId, closingOf = month)

/** Langkah 3 cocokkan saldo: UNRECORDED / SURPLUS_FOUND bulan [month]; null bila pas. */
fun reconcileTx(month: YearMonth, computedCash: Long, actualCash: Long, date: LocalDate): Tx? =
    reconcile(computedCash, actualCash)?.let { Tx(0, date, it.type, it.amount, closingOf = month, note = "Cocokkan saldo") }

/** Langkah 5 (R-43) "Sudah dibayar": pembayaran pos TETAP dihitung ke bulan [month]. */
fun closingFixedPaymentTx(month: YearMonth, categoryId: Long, amount: Long, date: LocalDate): Tx =
    Tx(0, date, TxType.FIXED_PAYMENT, amount, categoryId = categoryId, closingOf = month)

/** Nominal Split langkah 6. */
data class SplitTargets(val tabungan: Long, val danaDarurat: Long, val bawa: Long) {
    val total: Long get() = tabungan + danaDarurat + bawa
}

/**
 * Langkah 6 Saku Sisa akhir [saku] (sisa yang belum didistribusikan):
 * minus → wajib ditutup urutan penutup (R-72; alasan TANPA_GAJI bila bulan tanpa gaji, 6.10 langkah 1);
 * positif → sesuai [option]. [split] wajib untuk [ClosingOption.SPLIT] dan totalnya harus = [saku].
 */
fun closingDistribution(
    month: YearMonth,
    saku: Long,
    option: ClosingOption,
    date: LocalDate,
    tabungan: Long,
    danaDarurat: Long,
    noSalary: Boolean,
    split: SplitTargets? = null,
): List<Tx> {
    fun deposit(pot: Pot, amount: Long) = Tx(0, date, TxType.SAVING_DEPOSIT, amount, pot = pot, closingOf = month)
    fun carry(amount: Long) = Tx(0, date, TxType.CARRY_OVER, amount, closingOf = month, refYearMonth = month.plusMonths(1))
    return when (option) {
        ClosingOption.TUTUP_WAJIB -> {
            if (saku >= 0) return emptyList()
            val reason = if (noSalary) WithdrawReason.TANPA_GAJI else WithdrawReason.DARURAT
            val cover = coverShortfall(-saku, 0, tabungan, danaDarurat)
            listOfNotNull(
                cover.fromTabungan.takeIf { it > 0 }?.let { Tx(0, date, TxType.SAVING_WITHDRAW, it, pot = Pot.TABUNGAN, reason = reason, closingOf = month) },
                cover.fromDanaDarurat.takeIf { it > 0 }?.let { Tx(0, date, TxType.SAVING_WITHDRAW, it, pot = Pot.DANA_DARURAT, reason = reason, closingOf = month) },
            )
        }
        ClosingOption.ISI_DANA_DARURAT -> listOf(deposit(Pot.DANA_DARURAT, saku)).filter { it.amount > 0 }
        ClosingOption.SEMUA_KE_TABUNGAN -> listOf(deposit(Pot.TABUNGAN, saku)).filter { it.amount > 0 }
        ClosingOption.BAWA_KE_BULAN_DEPAN -> listOf(carry(saku)).filter { it.amount > 0 }
        ClosingOption.SPLIT -> {
            val s = requireNotNull(split) { "Split butuh nominal" }
            require(s.total == saku && s.tabungan >= 0 && s.danaDarurat >= 0 && s.bawa >= 0) { "Split harus pas ${s.total} = $saku" }
            listOfNotNull(
                s.tabungan.takeIf { it > 0 }?.let { deposit(Pot.TABUNGAN, it) },
                s.danaDarurat.takeIf { it > 0 }?.let { deposit(Pot.DANA_DARURAT, it) },
                s.bawa.takeIf { it > 0 }?.let(::carry),
            )
        }
    }
}

/** Snapshot angka yang disimpan di `month_closure.snapshotJson` saat bulan ditutup (6.10). */
@Serializable
data class ClosureSnapshot(
    val month: String,
    val verdict: String,
    val verdictAmount: Long,
    val salary: Long?,
    val gajiTambahan: Long,
    val pemasukan: Long,
    val pengeluaranRiil: Long,
    val tabunganMasuk: Long,
    val tabunganKeluar: Long,
    val danaDaruratMasuk: Long,
    val danaDaruratTerpakai: Long,
    val hutangDibawa: Long,
    val sakuSebelumDistribusi: Long,
    val movedToPots: Long,
    val carriedOut: Long,
    val nabungRutin: Long,
    val reconciled: Long?,
)

fun closureSnapshot(m: MonthSummary, reconciled: Long?): ClosureSnapshot = ClosureSnapshot(
    month = m.month.toString(),
    verdict = m.verdict.kind.name,
    verdictAmount = m.verdict.amount,
    salary = m.salary,
    gajiTambahan = m.gajiTambahan,
    pemasukan = m.pemasukan,
    pengeluaranRiil = m.pengeluaranRiil,
    tabunganMasuk = m.tabunganMasuk,
    tabunganKeluar = m.tabunganKeluar,
    danaDaruratMasuk = m.danaDaruratMasuk,
    danaDaruratTerpakai = m.danaDaruratTerpakai,
    hutangDibawa = m.hutangDibawa,
    sakuSebelumDistribusi = m.sakuSebelumDistribusi,
    movedToPots = m.movedToPots,
    carriedOut = m.carriedOut,
    nabungRutin = m.nabungRutin,
    reconciled = reconciled,
)

/** Hasil cocokkan saldo bulan [month] (± selisih) bila langkah 3 dilakukan. */
fun reconciledDiff(input: LedgerInput, month: YearMonth): Long? {
    val r = input.transactions.filter { it.closingOf == month && (it.type == TxType.UNRECORDED || it.type == TxType.SURPLUS_FOUND) }
    if (r.isEmpty()) return null
    return r.sumOf { if (it.type == TxType.SURPLUS_FOUND) it.amount else -it.amount }
}

/** R-64: koreksi bulan tertutup dicatat di bulan berjalan; [signedAmount] + = tambah terpakai, − = kurangi. */
fun correctionTx(categoryId: Long, signedAmount: Long, date: LocalDate, note: String): Tx {
    require(signedAmount != 0L && note.isNotBlank()) { "Koreksi butuh nominal dan catatan" }
    return Tx(0, date, TxType.CORRECTION, kotlin.math.abs(signedAmount), categoryId = categoryId, signedAmount = signedAmount, note = note.trim())
}
