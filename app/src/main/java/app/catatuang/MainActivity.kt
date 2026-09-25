package app.catatuang

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.catatuang.data.AppClock
import app.catatuang.data.AppState
import app.catatuang.data.CatatRepository
import app.catatuang.data.SettingsStore
import app.catatuang.security.LockManager
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.lock.LockScreen
import app.catatuang.feature.lock.LockViewModel
import app.catatuang.feature.onboarding.OnboardingScreen
import app.catatuang.feature.onboarding.OnboardingViewModel
import app.catatuang.ui.nav.MainScaffold
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatUangTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CatatUangApp).container
        // 8.14: pratinjau Recent apps kosong di API 33+; FLAG_SECURE hanya jika "Blokir screenshot" aktif.
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        lifecycleScope.launch {
            container.settingsState.collect { s ->
                if (s.blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        handle(intent)
        setContent {
            CatatUangTheme {
                CatatRoot(container.repository, container.settings, container.lockManager, container.clock, container.deepLink)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: android.content.Intent?) {
        intent?.getStringExtra(app.catatuang.notify.Notifier.EXTRA_ROUTE)?.let { (application as CatatUangApp).container.deepLink.value = it }
    }
}

@Composable
fun CatatRoot(
    repository: CatatRepository,
    settings: SettingsStore,
    lockManager: LockManager,
    clock: AppClock,
    deepLink: kotlinx.coroutines.flow.MutableStateFlow<String?> = remember { kotlinx.coroutines.flow.MutableStateFlow(null) },
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val locked by lockManager.locked.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(CatatTheme.colors.background)) {
        when (state) {
            AppState.Loading -> Unit
            is AppState.NeedsOnboarding -> {
                val vm: OnboardingViewModel = viewModel(factory = viewModelFactory {
                    initializer { OnboardingViewModel(repository, settings, lockManager, clock::now) }
                })
                OnboardingScreen(vm)
            }
            is AppState.Ready -> if (locked) {
                val vm: LockViewModel = viewModel(factory = viewModelFactory {
                    initializer { LockViewModel(settings, lockManager) }
                })
                LockScreen(vm)
            } else {
                val vm: LedgerViewModel = viewModel(factory = viewModelFactory {
                    initializer { LedgerViewModel(repository) }
                })
                MainScaffold(vm, deepLink)
            }
        }
        // 8.11: banner merah "MODE UJI" di semua layar selama Mode Uji Tanggal aktif.
        val testDate by repository.testModeDate.collectAsStateWithLifecycle(null)
        testDate?.let { d ->
            androidx.compose.material3.Text(
                "MODE UJI · ${app.catatuang.ui.format.shortDate(d)}",
                style = app.catatuang.ui.theme.CatatType.captionSmall,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).statusBarsPadding()
                    .background(androidx.compose.ui.graphics.Color(0xFFD93A5A), androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }
    }
}
