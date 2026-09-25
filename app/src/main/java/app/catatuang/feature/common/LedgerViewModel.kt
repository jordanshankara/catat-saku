package app.catatuang.feature.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.catatuang.data.AppState
import app.catatuang.data.CatatRepository
import app.catatuang.engine.Impact
import app.catatuang.engine.Pot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.WithdrawReason
import app.catatuang.engine.AllocationLine
import app.catatuang.engine.coverNowTransactions
import app.catatuang.engine.coverShortfall
import app.catatuang.engine.fixedPaymentTx
import app.catatuang.ui.format.rp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import app.catatuang.engine.previewImpact
import app.catatuang.ui.components.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pesan satu kali ke layar: kartu feedback (R-62) + snackbar dengan aksi Urungkan. */
data class UiEvent(
    val feedback: String?,
    val tone: Tone,
    /** Null = tanpa snackbar (hanya kartu feedback). */
    val snackbar: String?,
    val undo: (suspend () -> Unit)?,
    val detailCategoryId: Long? = null,
)

/**
 * ViewModel bersama untuk layar yang membaca ledger (Beranda, Input, Detail, Riwayat).
 * Semua hitungan tetap di core/engine; di sini hanya meneruskan aksi ke repository.
 */
class LedgerViewModel(private val repo: CatatRepository) : ViewModel() {
    companion object {
        const val LOADING = "loading"
    }

    val state: StateFlow<AppState> = repo.state

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events

    private fun ready() = state.value as? AppState.Ready

    suspend fun preview(draft: Tx): Impact? {
        val r = ready() ?: return null
        return withContext(Dispatchers.Default) { previewImpact(r.input, r.today, draft) }
    }

    /** Simpan pengeluaran; feedback & Urungkan 5 detik (R-62). */
    fun saveExpense(draft: Tx, feedback: String, tone: Tone) {
        viewModelScope.launch {
            val id = repo.addTransaction(draft)
            _events.emit(
                UiEvent(feedback, tone, "Tersimpan", undo = { repo.deleteTransaction(id) }, detailCategoryId = draft.categoryId),
            )
        }
    }

    val transferChecklist: StateFlow<Pair<String?, Int>> =
        repo.transferChecklist.stateIn(viewModelScope, SharingStarted.Eagerly, null to 0)
    val cadanganBannerDismissed: StateFlow<String?> =
        repo.cadanganBannerDismissed.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Beberapa transaksi sekaligus (Pakai Tabungan + pengeluaran, pemasukan, setor/ambil kantong). */
    fun saveAll(txs: List<Tx>, feedback: String?, tone: Tone, snackbar: String = "Tersimpan", detailCategoryId: Long? = null) {
        viewModelScope.launch {
            val ids = repo.addTransactions(txs)
            _events.emit(UiEvent(feedback, tone, snackbar, undo = { ids.forEach { repo.deleteTransaction(it) } }, detailCategoryId = detailCategoryId))
        }
    }

    /**
     * 8.6 Konfirmasi gaji. [checklistMonth] diisi bila ada setoran Nabung rutin yang perlu ditransfer (R-57);
     * feedback baru dikirim saat alur selesai lewat [announce].
     */
    suspend fun saveSalary(txs: List<Tx>, month: YearMonth, allocation: List<AllocationLine>?): CatatRepository.SavedSalary =
        repo.saveSalary(txs, month, allocation)

    fun undoSalary(saved: CatatRepository.SavedSalary) {
        viewModelScope.launch { repo.undoSalary(saved) }
    }

    fun setTransferChecklist(month: YearMonth?) {
        viewModelScope.launch { repo.setTransferChecklist(month) }
    }

    fun dismissCadanganBanner(month: YearMonth) {
        viewModelScope.launch { repo.dismissCadanganBanner(month) }
    }

    fun announce(event: UiEvent) {
        viewModelScope.launch { _events.emit(event) }
    }

    /** R-41/R-42: bayar pos TETAP bulan berjalan. */
    fun payFixed(month: YearMonth, categoryId: Long, amount: Long, estimate: Long, feedback: String, tone: Tone) {
        val r = ready() ?: return
        viewModelScope.launch {
            val id = repo.payFixed(month, fixedPaymentTx(categoryId, amount, r.today), estimate)
            _events.emit(UiEvent(feedback, tone, "Pembayaran tersimpan", undo = { repo.unpayFixed(month, categoryId, id, estimate) }))
        }
    }

    /** R-52 "Tutup sekarang": Saku Sisa minus ditutup dari Tabungan lalu Dana Darurat (DARURAT). */
    fun coverNow() {
        val r = ready() ?: return
        val l = r.ledger
        val txs = coverNowTransactions(l.sakuSisa, l.tabungan, l.danaDarurat, r.today)
        if (txs.isEmpty()) return
        saveAll(txs, feedback = "Saku Sisa ditutup ${rp(txs.sumOf { it.amount })} dari kantong", tone = Tone.WARNING, snackbar = "Saku Sisa ditutup")
    }

    // ---------- Tutup Buku (6.10) ----------
    val testModeDate: StateFlow<java.time.LocalDate?> = repo.testModeDate.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** [LOADING] sampai DataStore terbaca, supaya Tutup Buku tidak terbuka dua kali. */
    val closingAutoOpened: StateFlow<String?> = repo.closingAutoOpened.stateIn(viewModelScope, SharingStarted.Eagerly, LOADING)
    val testModeAvailable: Boolean get() = repo.testModeAvailable

    fun markClosingAutoOpened(month: YearMonth) { viewModelScope.launch { repo.setClosingAutoOpened(month) } }
    suspend fun setNoSalary(month: YearMonth, value: Boolean) = repo.setNoSalary(month, value)
    suspend fun setFixedCancelled(month: YearMonth, categoryId: Long, estimate: Long, cancelled: Boolean) =
        repo.setFixedCancelled(month, categoryId, estimate, cancelled)
    suspend fun replaceTransactions(deleteIds: List<Long>, txs: List<Tx>) = repo.replaceTransactions(deleteIds, txs)
    suspend fun addAll(txs: List<Tx>) = repo.addTransactions(txs)
    suspend fun payFixedNow(month: YearMonth, tx: Tx, estimate: Long) = repo.payFixed(month, tx, estimate)
    suspend fun closeMonth(month: YearMonth, verdict: String, amount: Long, snapshotJson: String, reconciled: Long?) =
        repo.closeMonth(month, verdict, amount, snapshotJson, reconciled)

    fun shareBackup(context: android.content.Context) {
        viewModelScope.launch { app.catatuang.export.shareBackup(context, repo) }
    }

    fun updateSettings(transform: (app.catatuang.engine.store.SettingsRecord) -> app.catatuang.engine.store.SettingsRecord) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }

    fun setTestDate(date: java.time.LocalDate) { viewModelScope.launch { repo.setTestDate(date) } }
    fun exitTestMode() { viewModelScope.launch { repo.exitTestMode() } }

    fun update(old: Tx, new: Tx) {
        viewModelScope.launch {
            repo.updateTransaction(new)
            _events.emit(UiEvent(null, Tone.SUCCESS, "Perubahan disimpan", undo = { repo.updateTransaction(old) }))
        }
    }

    fun delete(tx: Tx) {
        viewModelScope.launch {
            repo.deleteTransaction(tx.id)
            _events.emit(UiEvent(null, Tone.NEUTRAL, "Transaksi dihapus", undo = { repo.addTransactionKeepingId(tx) }))
        }
    }

    /**
     * R-23 Mode Darurat: lunasi [amount] hutang pos [categoryId] mengikuti urutan penutup.
     * Porsi dari kantong dicatat SAVING_WITHDRAW(DARURAT) lalu DEBT_PAYOFF, dalam satu transaksi database.
     */
    fun payDebt(categoryId: Long, amount: Long) {
        val r = ready() ?: return
        val l = r.ledger
        val cover = coverShortfall(amount, l.sakuSisa, l.tabungan, l.danaDarurat)
        val paid = amount - cover.uncovered
        if (paid <= 0) return
        val date = r.today
        val txs = buildList {
            if (cover.fromTabungan > 0) add(Tx(0, date, TxType.SAVING_WITHDRAW, cover.fromTabungan, pot = Pot.TABUNGAN, reason = WithdrawReason.DARURAT))
            if (cover.fromDanaDarurat > 0) add(Tx(0, date, TxType.SAVING_WITHDRAW, cover.fromDanaDarurat, pot = Pot.DANA_DARURAT, reason = WithdrawReason.DARURAT))
            add(Tx(0, date, TxType.DEBT_PAYOFF, paid, categoryId = categoryId))
        }
        viewModelScope.launch {
            val ids = repo.addTransactions(txs)
            _events.emit(
                UiEvent(
                    feedback = null,
                    tone = Tone.SUCCESS,
                    snackbar = "Hutang dilunasi",
                    undo = { ids.forEach { repo.deleteTransaction(it) } },
                ),
            )
        }
    }
}
