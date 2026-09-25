package app.catatuang

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import app.catatuang.data.AppClock
import app.catatuang.data.CatatRepository
import app.catatuang.data.SettingsStore
import app.catatuang.data.db.CatatDatabase
import app.catatuang.engine.Defaults
import app.catatuang.engine.Tx
import app.catatuang.security.LockManager
import app.catatuang.security.PinHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate

/** Lingkungan app nyata (Room in-memory + DataStore file sementara) untuk test Robolectric. */
class TestEnv(context: Context, today: LocalDate) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock = AppClock().apply { override.value = today }
    val db: CatatDatabase = Room.inMemoryDatabaseBuilder(context, CatatDatabase::class.java).allowMainThreadQueries().build()
    val settings = SettingsStore(
        PreferenceDataStoreFactory.create(scope = scope) { File.createTempFile("settings", ".preferences_pb").apply { delete() } },
    )
    val testFile: File = File.createTempFile("testmode", ".json").apply { delete() }
    val repo = CatatRepository(db.dao(), settings, clock, scope, testSnapshotFile = testFile)
    val lock = LockManager(timeoutMinutes = { 5 })

    fun onboard(start: LocalDate, cash: Long, pin: String = "4821") = runBlocking {
        repo.completeOnboarding("Ko", PinHasher.hash(pin), Defaults.categories, cash, 500_000, 1_000_000, start)
        repo.state.filterIsInstance<app.catatuang.data.AppState.Ready>().first()
    }

    fun add(vararg txs: Tx) = runBlocking { txs.forEach { repo.addTransaction(it) } }

    /** Gaji dengan alokasi template bulan target (tanpa lewat UI). */
    fun salary(received: LocalDate, target: java.time.YearMonth, amount: Long = 3_300_000) = runBlocking {
        val input = ready().input
        val alloc = input.template(target)
        repo.saveSalary(app.catatuang.engine.salaryTransactions(amount, received, target, alloc, input.categories, extra = false), target, alloc)
    }

    /** Keadaan siap yang sudah memuat semua transaksi di database (StateFlow bisa sedikit tertinggal). */
    /** Anggap Tutup Buku tertunda sudah pernah dibuka otomatis (supaya test lain tidak terbawa ke layar itu). */
    fun skipAutoClosing() = runBlocking {
        app.catatuang.engine.pendingClosing(ready().ledger)?.let { repo.setClosingAutoOpened(it.month) }
    }

    fun ready() = runBlocking {
        val n = db.dao().transactionsNow().size
        repo.state.filterIsInstance<app.catatuang.data.AppState.Ready>().first { it.input.transactions.size == n }
    }

    fun close() {
        db.close()
    }
}
