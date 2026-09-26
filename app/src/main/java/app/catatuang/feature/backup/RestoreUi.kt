package app.catatuang.feature.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.catatuang.engine.store.BackupCodec
import app.catatuang.engine.store.BackupReadResult
import app.catatuang.ui.components.HoldToConfirmButton
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Baca & validasi file backup yang dipilih (bab 11). */
suspend fun readBackup(context: Context, uri: Uri): BackupReadResult = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
        .getOrNull()?.let(BackupCodec::decode)
        ?: BackupReadResult.Invalid("File tidak bisa dibaca.")
}

/** Pemilih file backup (SAF `ACTION_OPEN_DOCUMENT`). */
@Composable
fun rememberBackupPicker(onResult: (BackupReadResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { onResult(readBackup(context, uri)) }
    }
    return { launcher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
}

/**
 * Preview → konfirmasi Pulihkan. [replacesData] = ada data di HP ini yang akan diganti (Pengaturan):
 * wajib tahan 3 detik.
 */
@Composable
fun RestoreDialog(result: BackupReadResult, replacesData: Boolean, onConfirm: (BackupReadResult.Ok) -> Unit, onDismiss: () -> Unit) {
    when (result) {
        is BackupReadResult.Invalid -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("File tidak bisa dipulihkan") },
            text = { Text(result.reason) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        )
        is BackupReadResult.Ok -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pulihkan backup ini?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(BackupCodec.previewText(result.preview))
                    Text("Dibuat ${result.document.exportedAt.take(16).replace('T', ' ')} · app ${result.document.appVersion}")
                    if (replacesData) NoticeBox("Seluruh data di HP ini diganti isi backup. Tidak bisa diurungkan.", Tone.DANGER)
                    Text("Setelah pulihkan, buat PIN baru.")
                }
            },
            confirmButton = {
                if (replacesData) HoldToConfirmButton("Tahan 3 detik · pulihkan", onConfirmed = { onConfirm(result) })
                else TextButton(onClick = { onConfirm(result) }) { Text("Pulihkan") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        )
    }
}
