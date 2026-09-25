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
import app.catatuang.engine.coverShortfall
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
    val snackbar: String,
    val undo: (suspend () -> Unit)?,
    val detailCategoryId: Long? = null,
)

/**
 * ViewModel bersama untuk layar yang membaca ledger (Beranda, Input, Detail, Riwayat).
 * Semua hitungan tetap di core/engine; di sini hanya meneruskan aksi ke repository.
 */
class LedgerViewModel(private val repo: CatatRepository) : ViewModel() {
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
