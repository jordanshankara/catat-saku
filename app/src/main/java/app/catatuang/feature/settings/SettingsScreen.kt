package app.catatuang.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.catatuang.BuildConfig
import app.catatuang.CatatUangApp
import app.catatuang.data.AppState
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.notify.NotificationRunner
import app.catatuang.notify.Scheduler
import app.catatuang.notify.Slot
import app.catatuang.ui.components.ChoiceChips
import app.catatuang.ui.components.DateChip
import app.catatuang.ui.components.MoneyField
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.RangeDatePickerDialog
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.SectionCard
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.fullDate
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import kotlinx.coroutines.launch
import java.time.LocalTime

/** 8.11 Pengaturan (bagian yang sudah tersedia) + Mode Uji Tanggal tersembunyi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(ready: AppState.Ready, vm: LedgerViewModel, onCategories: () -> Unit = {}) {
    val c = CatatTheme.colors
    val s = ready.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = (context.applicationContext as? CatatUangApp)?.container
    val testDate by vm.testModeDate.collectAsStateWithLifecycle()
    var nickname by remember(s.nickname) { mutableStateOf(s.nickname) }
    var threshold by remember(s.safeThreshold) { mutableLongStateOf(s.safeThreshold) }
    var emergencyTarget by remember(s.emergencyTarget) { mutableLongStateOf(s.emergencyTarget) }
    var pickTime by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var pickTestDate by remember { mutableStateOf(false) }
    var changePin by remember { mutableStateOf(false) }
    var notifEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val folder by vm.backupFolderUri.collectAsStateWithLifecycle()
    val lastBackup by vm.lastFolderBackup.collectAsStateWithLifecycle()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            vm.setBackupFolder(uri.toString())
        }
    }
    var restorePick by remember { mutableStateOf<app.catatuang.engine.store.BackupReadResult?>(null) }
    val pickBackup = app.catatuang.feature.backup.rememberBackupPicker { restorePick = it }
    restorePick?.let { r ->
        app.catatuang.feature.backup.RestoreDialog(r, replacesData = true, onConfirm = { ok ->
            restorePick = null
            vm.restoreFromBackup(ok.document.toSnapshot())
        }, onDismiss = { restorePick = null })
    }
    fun save(transform: (app.catatuang.engine.store.SettingsRecord) -> app.catatuang.engine.store.SettingsRecord) = vm.updateSettings(transform)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Pengaturan", style = CatatType.screenTitle, color = c.textPrimary)

        Group("Profil") {
            OutlinedTextField(
                value = nickname, onValueChange = { nickname = it.take(20) }, label = { Text("Nama panggilan") }, singleLine = true,
                shape = CatatShapes.chip, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.divider),
                modifier = Modifier.fillMaxWidth(),
            )
            if (nickname.trim() != s.nickname && nickname.isNotBlank()) SecondaryButton("Simpan nama", onClick = { save { it.copy(nickname = nickname.trim()) } })
        }

        Group("Pos & anggaran") {
            Row(Modifier.fillMaxWidth().clip(CatatShapes.chip).clickable(role = Role.Button, onClick = onCategories).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pos & nominal", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    Text("Ubah nominal, jatuh tempo, tambah/arsip pos · berlaku bulan depan", style = CatatType.caption, color = c.textSecondary)
                }
                Icon(LucideIcons.ChevronRight, contentDescription = null, tint = c.textSecondary)
            }
        }

        Group("Tampilan") {
            Row(Modifier.toggleable(value = s.theme == "DARK", role = Role.Switch) { v -> save { it.copy(theme = if (v) "DARK" else "LIGHT") } },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tema gelap", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    Text(if (s.theme == "DARK") "Aktif" else "Mati · tema terang", style = CatatType.caption, color = c.textSecondary)
                }
                Switch(checked = s.theme == "DARK", onCheckedChange = null)
            }
        }

        Group("Notifikasi") {
            Row(Modifier.fillMaxWidth().clip(CatatShapes.chip).clickable(role = Role.Button) { pickTime = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Jam notifikasi harian", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    Text("Preview Makan & Buah, hutang, Saku Sisa", style = CatatType.caption, color = c.textSecondary)
                }
                Text(s.notificationTime, style = CatatType.money, color = c.primary)
            }
            if (notifEnabled) NoticeBox("Notifikasi aktif.", Tone.SUCCESS)
            else {
                NoticeBox("Notifikasi belum diizinkan — pengingat 22:00 tidak akan muncul.", Tone.WARNING)
                PrimaryButton("Izinkan notifikasi", onClick = {
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
            }
            Text(
                "Supaya notifikasi tidak dimatikan HP, izinkan app jalan di latar belakang:\n" +
                    "• Xiaomi: Hemat baterai → Tanpa batasan, dan nyalakan Mulai otomatis.\n" +
                    "• Oppo / Realme: Penggunaan baterai → izinkan aktivitas latar belakang.\n" +
                    "• Vivo: Baterai → izinkan penggunaan daya tinggi di latar belakang.",
                style = CatatType.bodySmall, color = c.textSecondary,
            )
            SecondaryButton("Buka info app", onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            })
        }

        Group("Uang") {
            MoneyField("Ambang aman Sisa bebas", threshold, onChange = { threshold = it })
            MoneyField("Target Dana Darurat", emergencyTarget, onChange = { emergencyTarget = it })
            if (threshold != s.safeThreshold || emergencyTarget != s.emergencyTarget) {
                SecondaryButton("Simpan", onClick = { save { it.copy(safeThreshold = threshold, emergencyTarget = emergencyTarget) } })
            }
        }

        Group("Keamanan") {
            SecondaryButton("Ganti PIN", onClick = { changePin = true })
            Text("Kunci PIN setelah di-background", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
            ChoiceChips(listOf(1, 5, 15, 30).map { it to "$it menit" }, s.lockTimeoutMinutes, onSelect = { m -> save { it.copy(lockTimeoutMinutes = m) } })
            Row(Modifier.toggleable(value = s.blockScreenshots, role = Role.Switch) { v -> save { it.copy(blockScreenshots = v) } },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Blokir screenshot", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    Text("Layar app tidak bisa di-screenshot atau direkam", style = CatatType.caption, color = c.textSecondary)
                }
                Switch(checked = s.blockScreenshots, onCheckedChange = null)
            }
        }

        Group("Backup & pulihkan") {
            Text("Auto-backup tiap Minggu 23:00 ke folder pilihan (8 file terakhir disimpan).", style = CatatType.bodySmall, color = c.textSecondary)
            if (folder == null) NoticeBox("Folder auto-backup belum dipilih — backup mingguan tidak jalan.", Tone.WARNING)
            else Text("Folder: ${Uri.parse(folder).lastPathSegment?.substringAfterLast(':')?.ifEmpty { "(root)" } ?: folder}", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
            lastBackup?.split('|', limit = 2)?.let { parts ->
                val failed = parts.getOrNull(1)?.startsWith("GAGAL") == true
                NoticeBox("Backup terakhir ${parts[0].replace('T', ' ')} · ${parts.getOrNull(1) ?: ""}", if (failed) Tone.DANGER else Tone.SUCCESS)
            }
            SecondaryButton(if (folder == null) "Pilih folder" else "Ganti folder", onClick = { folderPicker.launch(null) })
            if (folder != null && container != null) {
                SecondaryButton("Backup ke folder sekarang", onClick = { scope.launch { app.catatuang.notify.AutoBackup.run(context, container) } })
            }
            PrimaryButton("Kirim backup", onClick = { vm.shareBackup(context) })
            SecondaryButton("Pulihkan dari backup", enabled = testDate == null, onClick = pickBackup)
            if (testDate != null) Text("Matikan Mode Uji dulu sebelum pulihkan.", style = CatatType.caption, color = c.warningText)
            Text("File backup tidak terenkripsi — simpan di tempat aman.", style = CatatType.caption, color = c.textSecondary)
        }

        Text(
            "Catat Uang versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = CatatType.caption, color = c.textSecondary,
            modifier = Modifier.fillMaxWidth().clip(CatatShapes.chip).clickable { versionTaps++ }.padding(vertical = 12.dp),
        )

        if ((versionTaps >= 7 || testDate != null) && vm.testModeAvailable) {
            Group("Mode Uji Tanggal") {
                NoticeBox("Semua data yang dibuat selama Mode Uji dihapus saat mode dimatikan. Data asli dipulihkan utuh.", Tone.DANGER)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(testDate?.let { "Hari ini (uji): ${fullDate(it)}" } ?: "Mode Uji mati", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary, modifier = Modifier.weight(1f))
                    DateChip("Pilih tanggal", onClick = { pickTestDate = true })
                }
                if (testDate != null) {
                    SecondaryButton("Maju 1 hari", onClick = { vm.setTestDate(testDate!!.plusDays(1)) })
                    if (container != null) {
                        Text("Kirim notifikasi uji sekarang (isi sesuai tanggal uji):", style = CatatType.caption, color = c.textSecondary)
                        ChoiceChips(
                            listOf(Slot.DAILY to "Harian", Slot.AT_0700 to "07:00", Slot.AT_0900 to "09:00", Slot.AT_1200 to "12:00"), null,
                            onSelect = { slot -> scope.launch { NotificationRunner.run(context, container, slot) } },
                        )
                    }
                    PrimaryButton("Matikan Mode Uji", color = c.dangerFill, onClick = { vm.exitTestMode(); versionTaps = 0 })
                }
            }
        }
    }

    if (changePin) {
        ChangePinSheet(vm, onDone = { msg ->
            changePin = false
            vm.announce(app.catatuang.feature.common.UiEvent(null, Tone.SUCCESS, msg, undo = null))
        }, onDismiss = { changePin = false })
    }

    if (pickTime) {
        val parsed = runCatching { LocalTime.parse(s.notificationTime) }.getOrDefault(LocalTime.of(22, 0))
        val state = rememberTimePickerState(parsed.hour, parsed.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Jam notifikasi harian") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val t = "%02d:%02d".format(state.hour, state.minute)
                    save { it.copy(notificationTime = t) }
                    Scheduler.scheduleAll(context, t)
                    pickTime = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Batal") } },
        )
    }

    if (pickTestDate) {
        val start = ready.input.onboarding.startDate
        RangeDatePickerDialog(testDate ?: ready.today, start, start.plusYears(2), onPick = { vm.setTestDate(it) }, onDismiss = { pickTestDate = false })
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val c = CatatTheme.colors
    Text(title, style = CatatType.cardTitle, color = c.textSecondary)
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
    Spacer(Modifier.height(2.dp))
}
