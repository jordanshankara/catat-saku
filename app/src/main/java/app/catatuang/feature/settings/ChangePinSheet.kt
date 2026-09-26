package app.catatuang.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.catatuang.data.CatatRepository.PinChange
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.lock.PinDots
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import kotlinx.coroutines.launch

/** 8.11 Ganti PIN: PIN lama → PIN baru → ketik ulang. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinSheet(vm: LedgerViewModel, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val c = CatatTheme.colors
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) } // 0 PIN lama, 1 PIN baru, 2 ketik ulang
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        busy = true
        val (o, n) = old to first
        scope.launch {
            when (vm.changePin(o, n)) {
                PinChange.OK -> onDone("PIN berhasil diganti")
                PinChange.WRONG -> { error = "PIN lama salah."; old = ""; first = ""; second = ""; step = 0 }
                PinChange.LOCKED -> { error = "Terlalu banyak salah. Coba lagi nanti."; old = ""; first = ""; second = ""; step = 0 }
            }
            busy = false
        }
    }

    fun digit(d: Char) {
        if (busy) return
        error = null
        when (step) {
            0 -> { old += d; if (old.length == 4) step = 1 }
            1 -> { first += d; if (first.length == 4) step = 2 }
            else -> {
                second += d
                if (second.length == 4) {
                    if (second == first) submit() else { error = "PIN baru tidak sama. Ulangi."; first = ""; second = ""; step = 1 }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = CatatShapes.sheet,
        containerColor = c.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (step) { 0 -> "Masukkan PIN lama"; 1 -> "PIN baru"; else -> "Ketik ulang PIN baru" },
                style = CatatType.screenTitle, color = c.textPrimary,
            )
            Spacer(Modifier.height(20.dp))
            PinDots(when (step) { 0 -> old.length; 1 -> first.length; else -> second.length })
            Spacer(Modifier.height(12.dp))
            Text(error ?: " ", style = CatatType.caption, color = c.dangerText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Numpad(onDigits = { d -> d.forEach(::digit) }, onBackspace = {
                when (step) { 0 -> old = old.dropLast(1); 1 -> first = first.dropLast(1); else -> second = second.dropLast(1) }
            }, tripleZero = false)
        }
    }
}
