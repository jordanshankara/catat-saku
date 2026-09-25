package app.catatuang.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 8.14: app terkunci saat dibuka pertama kali (bila PIN sudah ada) dan setelah di-background
 * ≥ batas waktu. Dipasang ke ProcessLifecycleOwner.
 */
class LockManager(
    private val timeoutMinutes: () -> Int,
    private val clock: () -> Long = System::currentTimeMillis,
) : DefaultLifecycleObserver {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    val attempts = PinAttempts(clock)
    private var backgroundAt: Long? = null

    override fun onStop(owner: LifecycleOwner) {
        backgroundAt = clock()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundAt ?: return
        backgroundAt = null
        if (clock() - since >= timeoutMinutes() * 60_000L) _locked.value = true
    }

    fun unlock() {
        attempts.onSuccess()
        _locked.value = false
    }

    /** Dipakai saat onboarding baru saja membuat PIN. */
    fun markUnlocked() {
        _locked.value = false
    }
}
