package app.catatuang

import android.content.Context
import app.catatuang.data.AppClock
import app.catatuang.data.CatatRepository
import app.catatuang.data.SettingsStore
import app.catatuang.data.db.CatatDatabase
import app.catatuang.data.secureDataStore
import app.catatuang.data.settingsDataStore
import app.catatuang.engine.store.SettingsRecord
import app.catatuang.security.LockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import app.catatuang.widget.CatatWidget
import app.catatuang.widget.widgetData

/** DI manual (bagian 3): satu container untuk seluruh app, tanpa Hilt/Koin. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob())
    val clock = AppClock()
    val database: CatatDatabase = CatatDatabase.build(context)
    val settings = SettingsStore(context.settingsDataStore, context.secureDataStore).also { s ->
        appScope.launch { s.migrateSecure() }
    }
    val settingsState: StateFlow<SettingsRecord> = settings.settings.stateIn(appScope, SharingStarted.Eagerly, SettingsRecord())
    val repository = CatatRepository(
        database.dao(), settings, clock, appScope,
        testSnapshotFile = java.io.File(context.filesDir, "testmode-snapshot.json"),
    ).also { repo -> appScope.launch { repo.restoreTestClock() } }
    /** Rute dari notifikasi yang menunggu dibuka setelah app tidak terkunci (8.14). */
    val deepLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val lockManager = LockManager(timeoutMinutes = { settingsState.value.lockTimeoutMinutes })

    init {
        // Widget ikut berubah setiap data/hari berubah (hanya bila isinya benar-benar berbeda).
        val appContext = context.applicationContext
        appScope.launch {
            repository.state.map(::widgetData).distinctUntilChanged().drop(1).collect { CatatWidget.refresh(appContext) }
        }
    }
}
