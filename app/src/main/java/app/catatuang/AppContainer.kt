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

/** DI manual (bagian 3): satu container untuk seluruh app, tanpa Hilt/Koin. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob())
    val clock = AppClock()
    val database: CatatDatabase = CatatDatabase.build(context)
    val settings = SettingsStore(context.settingsDataStore)
    val settingsState: StateFlow<SettingsRecord> = settings.settings.stateIn(appScope, SharingStarted.Eagerly, SettingsRecord())
    val repository = CatatRepository(database.dao(), settings, clock, appScope)
    val lockManager = LockManager(timeoutMinutes = { settingsState.value.lockTimeoutMinutes })
}
