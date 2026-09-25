package app.catatuang

import android.content.Context
import app.catatuang.data.AppClock
import app.catatuang.data.CatatRepository
import app.catatuang.data.SettingsStore
import app.catatuang.data.db.CatatDatabase
import app.catatuang.data.settingsDataStore
import app.catatuang.engine.store.SettingsRecord
import app.catatuang.security.LockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** DI manual (bagian 3): satu container untuk seluruh app, tanpa Hilt/Koin. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob())
    val clock = AppClock()
    val database: CatatDatabase = CatatDatabase.build(context)
    val settings = SettingsStore(context.settingsDataStore)
    val settingsState: StateFlow<SettingsRecord> = settings.settings.stateIn(appScope, SharingStarted.Eagerly, SettingsRecord())
    val repository = CatatRepository(
        database.dao(), settings, clock, appScope,
        testSnapshotFile = java.io.File(context.filesDir, "testmode-snapshot.json"),
    ).also { repo -> appScope.launch { repo.restoreTestClock() } }
    /** Rute dari notifikasi yang menunggu dibuka setelah app tidak terkunci (8.14). */
    val deepLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val lockManager = LockManager(timeoutMinutes = { settingsState.value.lockTimeoutMinutes })
}
