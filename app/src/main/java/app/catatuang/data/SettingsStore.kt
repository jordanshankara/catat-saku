package app.catatuang.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.catatuang.engine.Defaults
import app.catatuang.engine.store.SettingsRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Hash PIN tersimpan (8.14, bagian 3): PBKDF2 + salt acak, tidak pernah PIN aslinya. */
data class StoredPin(val hash: String, val salt: String)

/**
 * Pengaturan di DataStore (7.4). Nilai yang ikut backup ada di [SettingsRecord]; hash PIN,
 * URI folder backup, dan checklist transfer sengaja dipisah karena tidak ikut backup (D-09).
 */
class SettingsStore(private val store: DataStore<Preferences>) {

    val settings: Flow<SettingsRecord> = store.data.map(::read)

    val pin: Flow<StoredPin?> = store.data.map { p ->
        val h = p[PIN_HASH]; val s = p[PIN_SALT]
        if (h != null && s != null) StoredPin(h, s) else null
    }

    val backupFolderUri: Flow<String?> = store.data.map { it[BACKUP_URI] }

    /** Bulan split terakhir yang checklist transfer-nya belum dicentang (R-57), dan berapa kali sudah diingatkan. */
    val transferChecklist: Flow<Pair<String?, Int>> = store.data.map { it[CHECKLIST_MONTH] to (it[CHECKLIST_REMINDERS] ?: 0) }

    /** Tanggal checklist dibuat; pengingat mulai esok harinya jam 09:00 (R-57). */
    val transferChecklistSince: Flow<String?> = store.data.map { it[CHECKLIST_SINCE] }

    /** Bulan yang banner cadangan tanggal 1-nya sudah ditutup (R-14 butir 2). */
    val cadanganBannerDismissed: Flow<String?> = store.data.map { it[CADANGAN_DISMISSED] }

    suspend fun dismissCadanganBanner(month: String) {
        store.edit { it[CADANGAN_DISMISSED] = month }
    }

    /** Hasil backup ke folder terakhir: "2026-09-27T23:00|catatuang-backup-….json" atau "…|GAGAL: alasan". */
    val lastFolderBackup: Flow<String?> = store.data.map { it[LAST_FOLDER_BACKUP] }

    suspend fun setLastFolderBackup(value: String) {
        store.edit { it[LAST_FOLDER_BACKUP] = value }
    }

    /** Mode Uji Tanggal (8.11): tanggal "hari ini" palsu; null = mati. */
    val testModeDate: Flow<String?> = store.data.map { it[TEST_MODE_DATE] }

    suspend fun setTestModeDate(date: String?) {
        store.edit { if (date == null) it.remove(TEST_MODE_DATE) else it[TEST_MODE_DATE] = date }
    }

    /** Bulan yang Tutup Buku-nya sudah dibuka otomatis sekali (6.10). */
    val closingAutoOpened: Flow<String?> = store.data.map { it[CLOSING_AUTO_OPENED] }

    suspend fun setClosingAutoOpened(month: String) {
        store.edit { it[CLOSING_AUTO_OPENED] = month }
    }

    suspend fun current(): SettingsRecord = settings.first()

    suspend fun update(transform: (SettingsRecord) -> SettingsRecord) {
        store.edit { p -> write(p, transform(read(p))) }
    }

    suspend fun replace(record: SettingsRecord) {
        store.edit { p -> write(p, record) }
    }

    suspend fun setPin(pin: StoredPin?) {
        store.edit { p ->
            if (pin == null) { p.remove(PIN_HASH); p.remove(PIN_SALT) } else { p[PIN_HASH] = pin.hash; p[PIN_SALT] = pin.salt }
        }
    }

    suspend fun setBackupFolder(uri: String?) {
        store.edit { p -> if (uri == null) p.remove(BACKUP_URI) else p[BACKUP_URI] = uri }
    }

    suspend fun setTransferChecklist(month: String?, reminders: Int = 0, since: String? = null) {
        store.edit { p ->
            if (month == null) p.remove(CHECKLIST_MONTH) else p[CHECKLIST_MONTH] = month
            p[CHECKLIST_REMINDERS] = reminders
            if (since != null) p[CHECKLIST_SINCE] = since
        }
    }

    private fun read(p: Preferences) = SettingsRecord(
        nickname = p[NICKNAME] ?: Defaults.NICKNAME,
        salaryTemplate = p[SALARY_TEMPLATE] ?: Defaults.SALARY_TEMPLATE,
        expectedPayday = p[EXPECTED_PAYDAY] ?: Defaults.EXPECTED_PAYDAY,
        notificationTime = p[NOTIFICATION_TIME] ?: "22:00",
        safeThreshold = p[SAFE_THRESHOLD] ?: Defaults.SAFE_THRESHOLD,
        emergencyTarget = p[EMERGENCY_TARGET] ?: Defaults.EMERGENCY_TARGET,
        theme = p[THEME] ?: "LIGHT",
        startDate = p[START_DATE],
        cashStart = p[CASH_START] ?: 0,
        savingsStart = p[SAVINGS_START] ?: 0,
        emergencyStart = p[EMERGENCY_START] ?: 0,
        lockTimeoutMinutes = p[LOCK_TIMEOUT] ?: Defaults.LOCK_TIMEOUT_MINUTES,
        blockScreenshots = p[BLOCK_SCREENSHOTS] ?: false,
        onboardingDone = p[ONBOARDING_DONE] ?: false,
    )

    private fun write(p: MutablePreferences, s: SettingsRecord) {
        p[NICKNAME] = s.nickname
        p[SALARY_TEMPLATE] = s.salaryTemplate
        p[EXPECTED_PAYDAY] = s.expectedPayday
        p[NOTIFICATION_TIME] = s.notificationTime
        p[SAFE_THRESHOLD] = s.safeThreshold
        p[EMERGENCY_TARGET] = s.emergencyTarget
        p[THEME] = s.theme
        val start = s.startDate
        if (start == null) p.remove(START_DATE) else p[START_DATE] = start
        p[CASH_START] = s.cashStart
        p[SAVINGS_START] = s.savingsStart
        p[EMERGENCY_START] = s.emergencyStart
        p[LOCK_TIMEOUT] = s.lockTimeoutMinutes
        p[BLOCK_SCREENSHOTS] = s.blockScreenshots
        p[ONBOARDING_DONE] = s.onboardingDone
    }

    private companion object {
        val NICKNAME = stringPreferencesKey("nickname")
        val SALARY_TEMPLATE = longPreferencesKey("salary_template")
        val EXPECTED_PAYDAY = intPreferencesKey("expected_payday")
        val NOTIFICATION_TIME = stringPreferencesKey("notification_time")
        val SAFE_THRESHOLD = longPreferencesKey("safe_threshold")
        val EMERGENCY_TARGET = longPreferencesKey("emergency_target")
        val THEME = stringPreferencesKey("theme")
        val START_DATE = stringPreferencesKey("start_date")
        val CASH_START = longPreferencesKey("cash_start")
        val SAVINGS_START = longPreferencesKey("savings_start")
        val EMERGENCY_START = longPreferencesKey("emergency_start")
        val LOCK_TIMEOUT = intPreferencesKey("lock_timeout_minutes")
        val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BACKUP_URI = stringPreferencesKey("backup_folder_uri")
        val CHECKLIST_MONTH = stringPreferencesKey("transfer_checklist_month")
        val CHECKLIST_REMINDERS = intPreferencesKey("transfer_checklist_reminders")
        val CHECKLIST_SINCE = stringPreferencesKey("transfer_checklist_since")
        val CADANGAN_DISMISSED = stringPreferencesKey("cadangan_banner_dismissed")
        val TEST_MODE_DATE = stringPreferencesKey("test_mode_date")
        val LAST_FOLDER_BACKUP = stringPreferencesKey("last_folder_backup")
        val CLOSING_AUTO_OPENED = stringPreferencesKey("closing_auto_opened")
    }
}
