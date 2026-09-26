package app.catatuang.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.catatuang.data.CatatRepository
import app.catatuang.data.SettingsStore
import app.catatuang.engine.Category
import app.catatuang.engine.CategoryAmount
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.Defaults
import app.catatuang.engine.OnboardingPlan
import app.catatuang.engine.planOnboarding
import app.catatuang.security.LockManager
import app.catatuang.security.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class OnboardingStep { WELCOME, NAME, PIN, CATEGORIES, BALANCES, PERIOD, NOTIFICATIONS, BACKUP }

data class OnboardingForm(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val nickname: String = Defaults.NICKNAME,
    val pinFirst: String = "",
    val pinSecond: String = "",
    val pinError: String? = null,
    /** Nominal per pos: jatah harian untuk HARIAN, per bulan untuk lainnya. */
    val amounts: Map<Long, Long> = Defaults.categories.associate { c ->
        c.id to (if (c.kind == CategoryKind.DAILY) c.amounts.first().dailyAmount!! else c.amounts.first().monthlyAmount!!)
    },
    val cash: Long = 0,
    val savings: Long = 0,
    val emergency: Long = 0,
    val backupFolder: String? = null,
    val saving: Boolean = false,
)

/** 8.12 Mulai Baru: nama → PIN → pos & nominal → saldo awal → periode awal → izin → folder backup. */
class OnboardingViewModel(
    private val repo: CatatRepository,
    private val settings: SettingsStore,
    private val lock: LockManager,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val _form = MutableStateFlow(OnboardingForm())
    val form: StateFlow<OnboardingForm> = _form.asStateFlow()

    fun go(step: OnboardingStep) = _form.update { it.copy(step = step) }
    fun setName(v: String) = _form.update { it.copy(nickname = v.take(20)) }
    fun setAmount(id: Long, v: Long) = _form.update { it.copy(amounts = it.amounts + (id to v)) }
    fun setCash(v: Long) = _form.update { it.copy(cash = v) }
    fun setSavings(v: Long) = _form.update { it.copy(savings = v) }
    fun setEmergency(v: Long) = _form.update { it.copy(emergency = v) }
    fun setBackupFolder(uri: String?) = _form.update { it.copy(backupFolder = uri) }

    /** Ketik PIN dua kali (8.12 langkah 2). */
    fun pinDigit(d: String) = _form.update { f ->
        when {
            f.pinFirst.length < 4 -> f.copy(pinFirst = f.pinFirst + d, pinError = null)
            f.pinSecond.length < 4 -> {
                val second = f.pinSecond + d
                if (second.length == 4 && second != f.pinFirst) f.copy(pinFirst = "", pinSecond = "", pinError = "PIN tidak sama. Ulangi dari awal.")
                else f.copy(pinSecond = second)
            }
            else -> f
        }
    }

    fun pinBackspace() = _form.update { f ->
        if (f.pinSecond.isNotEmpty()) f.copy(pinSecond = f.pinSecond.dropLast(1)) else f.copy(pinFirst = f.pinFirst.dropLast(1))
    }

    val pinComplete: Boolean get() = _form.value.let { it.pinFirst.length == 4 && it.pinSecond == it.pinFirst }

    fun categories(): List<Category> = Defaults.categories.map { c ->
        val v = _form.value.amounts[c.id] ?: 0
        c.copy(amounts = listOf(if (c.kind == CategoryKind.DAILY) CategoryAmount(null, dailyAmount = v) else CategoryAmount(null, monthlyAmount = v)))
    }

    fun plan(): OnboardingPlan = planOnboarding(today(), _form.value.cash, categories())

    fun finish(onDone: () -> Unit) {
        val f = _form.value
        if (f.saving || !pinComplete) return
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val pin = withContext(Dispatchers.Default) { PinHasher.hash(f.pinFirst) }
            repo.completeOnboarding(f.nickname, pin, categories(), f.cash, f.savings, f.emergency, today())
            f.backupFolder?.let { settings.setBackupFolder(it) }
            lock.markUnlocked()
            onDone()
        }
    }

    /** 8.12 / bab 11: Pulihkan dari Backup di layar pertama. PIN baru dibuat sesudahnya. */
    fun restore(snapshot: app.catatuang.engine.store.DataSnapshot) {
        viewModelScope.launch { repo.restoreFromBackup(snapshot) }
    }
}
