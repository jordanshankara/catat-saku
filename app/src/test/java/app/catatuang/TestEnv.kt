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
    val repo = CatatRepository(db.dao(), settings, clock, scope)
    val lock = LockManager(timeoutMinutes = { 5 })

    fun onboard(start: LocalDate, cash: Long, pin: String = "4821") = runBlocking {
        repo.completeOnboarding("Ko", PinHasher.hash(pin), Defaults.categories, cash, 500_000, 1_000_000, start)
        repo.state.filterIsInstance<app.catatuang.data.AppState.Ready>().first()
    }

    fun add(vararg txs: Tx) = runBlocking { txs.forEach { repo.addTransaction(it) } }

    fun ready() = runBlocking { repo.state.filterIsInstance<app.catatuang.data.AppState.Ready>().first() }

    fun close() {
        db.close()
    }
}
