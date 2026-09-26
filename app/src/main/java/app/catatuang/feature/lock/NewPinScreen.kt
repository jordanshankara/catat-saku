package app.catatuang.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.catatuang.data.SettingsStore
import app.catatuang.security.LockManager
import app.catatuang.security.PinHasher
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import kotlinx.coroutines.launch

/** Buat PIN baru setelah Pulihkan (bab 11): hash PIN tidak ikut backup. */
@Composable
fun NewPinScreen(settings: SettingsStore, lockManager: LockManager) {
    val c = CatatTheme.colors
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun digit(d: Char) {
        when {
            first.length < 4 -> { first += d; error = null }
            second.length < 4 -> {
                second += d
                if (second.length == 4) {
                    if (second == first) {
                        val pin = first
                        scope.launch { settings.setPin(PinHasher.hash(pin)); lockManager.markUnlocked() }
                    } else { error = "PIN tidak sama. Ulangi."; first = ""; second = "" }
                }
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(CatatShapes.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("Data berhasil dipulihkan", style = CatatType.bodySmall, color = c.success)
        Spacer(Modifier.height(8.dp))
        Text(if (first.length < 4) "Buat PIN baru" else "Ketik ulang PIN", style = CatatType.screenTitle, color = c.textPrimary)
        Spacer(Modifier.height(24.dp))
        PinDots(if (first.length < 4) first.length else second.length)
        Spacer(Modifier.height(16.dp))
        Text(error ?: " ", style = CatatType.caption, color = c.dangerText, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Numpad(onDigits = { d -> d.forEach(::digit) }, onBackspace = {
            if (second.isNotEmpty()) second = second.dropLast(1) else first = first.dropLast(1)
        }, tripleZero = false)
    }
}
