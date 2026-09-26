package app.catatuang.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.catatuang.data.SettingsStore
import app.catatuang.security.LockManager
import app.catatuang.security.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LockUi(val entered: String = "", val error: String? = null, val waitSeconds: Int = 0, val checking: Boolean = false)

/** 8.14 layar kunci PIN. */
class LockViewModel(private val settings: SettingsStore, private val lock: LockManager) : ViewModel() {
    private val _ui = MutableStateFlow(LockUi())
    val ui: StateFlow<LockUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val (failures, until) = settings.pinAttempts.first()
            if (failures > lock.attempts.failures) lock.attempts.restore(failures, until)
            refreshWait()
        }
    }

    fun digit(d: String) {
        val s = _ui.value
        if (s.checking || s.waitSeconds > 0 || s.entered.length >= 4) return
        val entered = s.entered + d
        _ui.update { it.copy(entered = entered, error = null) }
        if (entered.length == 4) check(entered)
    }

    fun backspace() = _ui.update { it.copy(entered = it.entered.dropLast(1)) }

    private fun check(pin: String) {
        _ui.update { it.copy(checking = true) }
        viewModelScope.launch {
            val stored = settings.pin.first()
            val ok = stored != null && withContext(Dispatchers.Default) { PinHasher.verify(pin, stored) }
            if (ok) {
                settings.setPinAttempts(0, 0)
                lock.unlock()
                _ui.value = LockUi()
            } else {
                lock.attempts.onFailure()
                val left = 5 - lock.attempts.failures % 5
                _ui.update {
                    it.copy(entered = "", checking = false, error = if (left == 5) null else "PIN salah. $left kali lagi sebelum jeda.")
                }
                refreshWait()
                // Simpan permanen (8.14): jeda tetap berlaku walau app ditutup paksa.
                settings.setPinAttempts(lock.attempts.failures, lock.attempts.lockedUntilMillis)
            }
        }
    }

    private fun refreshWait() {
        viewModelScope.launch {
            while (true) {
                val ms = lock.attempts.remainingLockMillis()
                _ui.update { it.copy(waitSeconds = ((ms + 999) / 1000).toInt(), error = if (ms > 0) "Terlalu banyak salah. Coba lagi sebentar." else it.error) }
                if (ms <= 0) break
                delay(250)
            }
        }
    }
}
