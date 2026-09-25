package app.catatuang.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

/** 4 titik PIN; dipakai layar kunci dan onboarding. */
@Composable
fun PinDots(count: Int, modifier: Modifier = Modifier) {
    val c = CatatTheme.colors
    Row(
        modifier.semantics { contentDescription = "$count dari 4 digit terisi" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(4) { i ->
            val filled = i < count
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(if (filled) c.primary else c.surface)
                    .border(2.dp, if (filled) c.primary else c.divider, CircleShape),
            )
        }
    }
}

@Composable
fun LockScreen(vm: LockViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = CatatTheme.colors
    var showForgot by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .padding(CatatShapes.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("Masukkan PIN", style = CatatType.screenTitle, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Catat Uang terkunci", style = CatatType.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(28.dp))
        PinDots(ui.entered.length)
        Spacer(Modifier.height(16.dp))
        val message = when {
            ui.waitSeconds > 0 -> "Terlalu banyak salah. Coba lagi dalam ${ui.waitSeconds} detik."
            else -> ui.error
        }
        Text(message ?: " ", style = CatatType.caption, color = c.dangerText, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Numpad(onDigits = { d -> d.forEach { vm.digit(it.toString()) } }, onBackspace = vm::backspace, tripleZero = false)
        Spacer(Modifier.height(12.dp))
        Text(
            "Lupa PIN?",
            style = CatatType.bodySmall,
            color = c.primary,
            modifier = Modifier.clip(CatatShapes.chip).clickable { showForgot = true }.padding(12.dp),
        )
    }
    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            confirmButton = { TextButton(onClick = { showForgot = false }) { Text("Mengerti") } },
            title = { Text("Lupa PIN?") },
            text = {
                Text(
                    "PIN tidak bisa di-reset dari dalam app. Satu-satunya cara: instal ulang Catat Uang, " +
                        "lalu pilih Pulihkan dari Backup. File backup tidak terenkripsi, jadi simpan di tempat aman.",
                )
            },
        )
    }
}
