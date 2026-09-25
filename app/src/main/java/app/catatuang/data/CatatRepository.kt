package app.catatuang.data

import app.catatuang.data.db.CatatDao
import app.catatuang.data.db.MonthAllocationEntity
import app.catatuang.data.db.MonthPlanEntity
import app.catatuang.data.db.amountEntities
import app.catatuang.data.db.toEntity
import app.catatuang.data.db.toRecord
import app.catatuang.engine.AllocationLine
import app.catatuang.engine.Category
import app.catatuang.engine.LedgerInput
import app.catatuang.engine.Defaults
import app.catatuang.engine.LedgerState
import app.catatuang.engine.Tx
import app.catatuang.engine.computeLedger
import app.catatuang.engine.fixedObligationsFor
import app.catatuang.engine.store.DataSnapshot
import app.catatuang.engine.store.FixedObligationRecord
import app.catatuang.engine.store.SettingsRecord
import app.catatuang.engine.store.toCategory
import app.catatuang.engine.store.toLedgerInput
import app.catatuang.engine.store.toRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

sealed interface AppState {
    data object Loading : AppState
    data class NeedsOnboarding(val settings: SettingsRecord) : AppState
    data class Ready(val settings: SettingsRecord, val input: LedgerInput, val ledger: LedgerState, val today: LocalDate) : AppState
}

/**
 * Satu-satunya pintu data (7.2). Setiap perubahan data memicu `computeLedger` ulang penuh; hasilnya
 * diekspos sebagai [ledger]. Tidak ada saldo yang disimpan atau di-update manual (prinsip 6).
 */
class CatatRepository(
    private val dao: CatatDao,
    private val settings: SettingsStore,
    private val clock: AppClock,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val tables: Flow<DataSnapshot> = combine(
        dao.categories(), dao.categoryAmounts(), dao.monthPlans(), dao.allocations(), dao.transactions(),
    ) { cats, amounts, plans, allocs, txs ->
        DataSnapshot(
            settings = SettingsRecord(),
            categories = cats.map { it.toRecord(amounts) },
            monthPlans = plans.map { it.toRecord() },
            allocations = allocs.map { it.toRecord() },
            transactions = txs.map { it.toRecord() },
            fixedObligations = emptyList(),
            dayMarks = emptyList(),
            closures = emptyList(),
        )
    }

    val snapshot: Flow<DataSnapshot> = combine(
        tables, dao.fixedObligations(), dao.dayMarks(), dao.closures(), settings.settings,
    ) { base, obligations, marks, closures, s ->
        base.copy(
            settings = s,
            fixedObligations = obligations.map { it.toRecord() },
            dayMarks = marks.map { it.toRecord() },
            closures = closures.map { it.toRecord() },
        )
    }

    /** Keadaan app: memuat → perlu onboarding → siap (dengan ledger hasil hitung ulang penuh). */
    val state: StateFlow<AppState> = combine(snapshot, clock.today) { snap, today ->
        val input = snap.toLedgerInput()
        if (!snap.settings.onboardingDone || input == null) {
            AppState.NeedsOnboarding(snap.settings)
        } else {
            AppState.Ready(snap.settings, input, computeLedger(input, today), today)
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, AppState.Loading)

    /** Null sampai onboarding selesai. */
    val ledger: StateFlow<LedgerState?> = state.map { (it as? AppState.Ready)?.ledger }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * 8.12 Mulai Baru: simpan pos (nominal hasil konfirmasi berlaku sejak awal), saldo awal, tanggal
     * mulai, dan hash PIN. Ledger bulan awal dihitung engine (planOnboarding).
     */
    suspend fun completeOnboarding(
        nickname: String,
        pin: StoredPin,
        categories: List<Category>,
        cash: Long,
        savings: Long,
        emergency: Long,
        start: LocalDate,
    ) {
        val records = categories.map { it.toRecord() }
        dao.replaceAll(
            categories = records.map { it.toEntity() },
            amounts = records.flatMap { it.amountEntities() },
            plans = emptyList(), allocations = emptyList(), txs = emptyList(),
            obligations = emptyList(), dayMarks = emptyList(), closures = emptyList(),
        )
        settings.setPin(pin)
        settings.update {
            it.copy(
                nickname = nickname.ifBlank { Defaults.NICKNAME },
                startDate = start.toString(),
                cashStart = cash,
                savingsStart = savings,
                emergencyStart = emergency,
                onboardingDone = true,
            )
        }
    }

    suspend fun seedDefaultCategories() {
        if (dao.categoriesNow().isNotEmpty()) return
        val records = Defaults.categories.map { it.toRecord() }
        dao.upsertCategories(records.map { it.toEntity() })
        dao.insertCategoryAmounts(records.flatMap { it.amountEntities() })
    }

    init {
        // R-40 / D-34: kewajiban pos TETAP bulan berjalan dibuat begitu bulannya dimulai (tanggal 1),
        // dari alokasi bulan itu atau template bila gaji belum masuk. Bulan onboarding tidak (D-19).
        scope.launch {
            state.filterIsInstance<AppState.Ready>()
                .map { r -> r.ledger.current?.takeIf { !it.isOnboarding }?.let { it.month to it.allocation } }
                .distinctUntilChanged()
                .collect { pair ->
                    pair?.let { (m, alloc) -> runCatching { ensureFixedObligations(m, alloc) }.onFailure { it.printStackTrace() } }
                }
        }
    }

    val transferChecklist: Flow<Pair<String?, Int>> = settings.transferChecklist
    val cadanganBannerDismissed: Flow<String?> = settings.cadanganBannerDismissed

    suspend fun setTransferChecklist(month: YearMonth?) = settings.setTransferChecklist(month?.toString())
    suspend fun dismissCadanganBanner(month: YearMonth) = settings.dismissCadanganBanner(month.toString())

    /** Hasil simpan gaji, untuk Urungkan. */
    data class SavedSalary(val ids: List<Long>, val month: YearMonth, val previousAllocation: List<MonthAllocationEntity>, val previousPlan: MonthPlanEntity?)

    /**
     * 8.6 Konfirmasi gaji: transaksi (gaji + Nabung rutin), alokasi bulan target (termasuk hasil
     * Penyesuaian), dan `month_plan` dalam satu transaksi database. [allocation] null untuk gaji
     * tambahan (R-05) dan gaji bulan onboarding (8.12).
     */
    suspend fun saveSalary(txs: List<Tx>, month: YearMonth, allocation: List<AllocationLine>?): SavedSalary {
        val t = now()
        val m = month.toString()
        val prevAlloc = dao.allocationsNow().filter { it.yearMonth == m }
        val prevPlan = dao.monthPlansNow().firstOrNull { it.yearMonth == m }
        val entities = txs.mapIndexed { i, tx -> tx.copy(id = 0, createdAt = t + i).toRecord(updatedAt = t + i).toEntity() }
        val allocEntities = allocation?.map { MonthAllocationEntity(m, it.categoryId, it.dailyAmount, it.monthlyAmount) }
        val plan = if (allocation != null) MonthPlanEntity(m, null, noSalary = false, status = "OPEN", createdAt = prevPlan?.createdAt ?: t) else null
        val ids = dao.saveSalary(entities, m, allocEntities, plan)
        return SavedSalary(ids, month, prevAlloc, prevPlan)
    }

    suspend fun undoSalary(saved: SavedSalary) {
        dao.undoSalary(saved.ids, saved.month.toString(), saved.previousAllocation, saved.previousPlan)
    }

    /** R-41: bayar pos TETAP bulan [month]; kewajibannya jadi LUNAS. Mengembalikan id transaksi. */
    suspend fun payFixed(month: YearMonth, tx: Tx, estimate: Long): Long {
        val t = now()
        val obligation = dao.fixedObligationsNow().firstOrNull { it.yearMonth == month.toString() && it.categoryId == tx.categoryId }
            ?: FixedObligationRecord(month.toString(), tx.categoryId!!, estimate).toEntity()
        return dao.payFixed(tx.copy(id = 0, createdAt = t).toRecord(updatedAt = t).toEntity(), obligation)
    }

    suspend fun unpayFixed(month: YearMonth, categoryId: Long, txId: Long, estimate: Long) {
        val obligation = dao.fixedObligationsNow().firstOrNull { it.yearMonth == month.toString() && it.categoryId == categoryId }
            ?: FixedObligationRecord(month.toString(), categoryId, estimate).toEntity()
        dao.unpayFixed(txId, obligation)
    }

    /** Simpan transaksi baru; id diberikan database. */
    suspend fun addTransaction(tx: Tx, testMode: Boolean = false): Long {
        val t = now()
        return dao.insertTx(tx.copy(id = 0, createdAt = t).toRecord(updatedAt = t, testMode = testMode).toEntity())
    }

    /** Beberapa transaksi dalam satu transaksi database (Mode Darurat, Pakai Tabungan + pengeluaran). */
    suspend fun addTransactions(txs: List<Tx>, testMode: Boolean = false): List<Long> {
        val t = now()
        return dao.insertTxBatch(txs.mapIndexed { i, tx ->
            tx.copy(id = 0, createdAt = t + i).toRecord(updatedAt = t + i, testMode = testMode).toEntity()
        })
    }

    /** Kembalikan transaksi yang baru dihapus (Urungkan) dengan id & urutan aslinya. */
    suspend fun addTransactionKeepingId(tx: Tx) {
        dao.insertTx(tx.toRecord(updatedAt = now()).toEntity())
    }

    suspend fun updateTransaction(tx: Tx) {
        dao.upsertTx(tx.toRecord(updatedAt = now()).toEntity())
    }

    suspend fun deleteTransaction(id: Long) = dao.deleteTx(id)

    /** R-40: kewajiban pos TETAP dibuat tiap tanggal 1 dari alokasi bulan itu (atau template). */
    suspend fun ensureFixedObligations(month: YearMonth, allocation: List<AllocationLine>) {
        val existing = dao.fixedObligationsNow().filter { it.yearMonth == month.toString() }.associateBy { it.categoryId }
        val amounts = dao.categoryAmountsNow()
        val categories = dao.categoriesNow().map { it.toRecord(amounts).toCategory() }
        // Baris baru dibuat BELUM BAYAR; estimasi baris yang belum dibayar mengikuti alokasi terbaru (mis. setelah gaji telat).
        val seeds = fixedObligationsFor(month, allocation, categories).mapNotNull { seed ->
            val old = existing[seed.categoryId]
            when {
                old == null -> FixedObligationRecord(month.toString(), seed.categoryId, seed.estimate).toEntity()
                old.status == "UNPAID" && old.estimate != seed.estimate -> old.copy(estimate = seed.estimate)
                else -> null
            }
        }
        if (seeds.isNotEmpty()) dao.upsertFixedObligations(seeds)
    }

    /** Seluruh data sekarang (untuk backup). */
    suspend fun exportSnapshot(): DataSnapshot {
        val amounts = dao.categoryAmountsNow()
        return DataSnapshot(
            settings = settings.current(),
            categories = dao.categoriesNow().map { it.toRecord(amounts) },
            monthPlans = dao.monthPlansNow().map { it.toRecord() },
            allocations = dao.allocationsNow().map { it.toRecord() },
            transactions = dao.transactionsNow().map { it.toRecord() },
            fixedObligations = dao.fixedObligationsNow().map { it.toRecord() },
            dayMarks = dao.dayMarksNow().map { it.toRecord() },
            closures = dao.closuresNow().map { it.toRecord() },
        )
    }

    /** Pulihkan: ganti seluruh data dalam satu transaksi database, lalu pengaturan (bab 11). */
    suspend fun restore(snapshot: DataSnapshot) {
        dao.replaceAll(
            categories = snapshot.categories.map { it.toEntity() },
            amounts = snapshot.categories.flatMap { it.amountEntities() },
            plans = snapshot.monthPlans.map { it.toEntity() },
            allocations = snapshot.allocations.map { it.toEntity() },
            txs = snapshot.transactions.map { it.toEntity() },
            obligations = snapshot.fixedObligations.map { it.toEntity() },
            dayMarks = snapshot.dayMarks.map { it.toEntity() },
            closures = snapshot.closures.map { it.toEntity() },
        )
        settings.replace(snapshot.settings)
    }
}
