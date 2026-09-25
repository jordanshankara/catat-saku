package app.catatuang.feature.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.Defaults
import app.catatuang.feature.lock.PinDots
import app.catatuang.ui.components.CategoryIcon
import app.catatuang.ui.components.MoneyField
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.SectionCard
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.rp
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

private val ORDER = OnboardingStep.entries

@Composable
fun OnboardingScreen(vm: OnboardingViewModel) {
    val f by vm.form.collectAsStateWithLifecycle()
    val index = ORDER.indexOf(f.step)
    BackHandler(enabled = index > 0) { vm.go(ORDER[index - 1]) }
    val next = { vm.go(ORDER[(index + 1).coerceAtMost(ORDER.lastIndex)]) }
    when (f.step) {
        OnboardingStep.WELCOME -> Welcome(onStart = next)
        OnboardingStep.NAME -> Page(1, "Mau dipanggil apa?", "Nama ini muncul di Beranda. Bisa diubah nanti di Pengaturan.") {
            val c = CatatTheme.colors
            OutlinedTextField(
                value = f.nickname,
                onValueChange = vm::setName,
                label = { Text("Nama panggilan") },
                singleLine = true,
                shape = CatatShapes.chip,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.divider, focusedContainerColor = c.surface, unfocusedContainerColor = c.surface),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            PrimaryButton("Lanjut", onClick = next, enabled = f.nickname.isNotBlank())
        }
        OnboardingStep.PIN -> Page(2, if (f.pinFirst.length < 4) "Buat PIN 4 digit" else "Ketik ulang PIN", "PIN diminta saat app dibuka setelah ditinggal 5 menit.") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                PinDots(if (f.pinFirst.length < 4) f.pinFirst.length else f.pinSecond.length)
                Spacer(Modifier.height(12.dp))
                Text(f.pinError ?: " ", style = CatatType.caption, color = CatatTheme.colors.dangerText)
            }
            Spacer(Modifier.weight(1f))
            Numpad(onDigits = { d -> d.forEach { vm.pinDigit(it.toString()) } }, onBackspace = vm::pinBackspace, tripleZero = false)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Lanjut", onClick = next, enabled = vm.pinComplete)
        }
        OnboardingStep.CATEGORIES -> Page(3, "Cek pos & nominal", "Ini nominal default. Ubah kalau perlu; nanti juga bisa diubah di Pengaturan (berlaku bulan depan).", scroll = true) {
            Defaults.categories.forEach { c ->
                val daily = c.kind == CategoryKind.DAILY
                val hint = when {
                    daily -> "per hari"
                    c.weekendMode -> "per bulan · jatah ${rp(((f.amounts[c.id] ?: 0) / 4) / 1000 * 1000)} per akhir pekan"
                    c.kind == CategoryKind.FIXED -> "per bulan · tagihan tetap"
                    c.kind == CategoryKind.SAVING -> "per bulan · disetor ke Tabungan"
                    else -> "per bulan"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryIcon(c.key, 40)
                    MoneyField(c.name, f.amounts[c.id] ?: 0, { vm.setAmount(c.id, it) }, Modifier.weight(1f), supporting = hint)
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Lanjut", onClick = next)
        }
        OnboardingStep.BALANCES -> Page(4, "Saldo awal", "Hitung uang yang ada sekarang. Tabungan & dana darurat dipisah dari uang pegangan.", scroll = true) {
            MoneyField("Uang pegangan lu sekarang berapa?", f.cash, vm::setCash, supporting = "Dompet + rekening + e-wallet, di luar tabungan")
            MoneyField("Tabungan sekarang berapa?", f.savings, vm::setSavings, supporting = "Contoh: 500.000")
            MoneyField("Dana darurat sekarang berapa?", f.emergency, vm::setEmergency, supporting = "Contoh: 1.000.000")
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Lanjut", onClick = next)
        }
        OnboardingStep.PERIOD -> {
            val p = vm.plan()
            Page(5, "Periode awal", "Bulan ini dimulai dari hari ini sampai akhir bulan.", scroll = true) {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlanRow("Jatah harian · ${p.remainingDays} hari", p.dailyTotal)
                        PlanRow("Transport · ${p.transportWindows} akhir pekan", p.transportAmount)
                        p.stockProrata.forEach { (id, amount) ->
                            PlanRow("${Defaults.categories.first { it.id == id }.name} · prorata", amount)
                        }
                        PlanRow("Pos tetap bulan ini", 0, note = "dianggap sudah dibayar")
                        Box(Modifier.fillMaxWidth().height(1.dp).background(CatatTheme.colors.divider))
                        PlanRow("Saku Sisa awal", p.sakuSisaAwal, bold = true)
                    }
                }
                if (p.sakuSisaAwal < 0) {
                    NoticeBox("Uang pegangan kurang ${rp(-p.sakuSisaAwal)} untuk sisa bulan ini. Kumpulkan lewat hemat (target cadangan ${rp(p.targetCadangan)}).", Tone.WARNING)
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Lanjut", onClick = next)
            }
        }
        OnboardingStep.NOTIFICATIONS -> Page(6, "Izin notifikasi", "Dipakai untuk pengingat jam 22:00, jatuh tempo tagihan, dan tutup buku.", scroll = true) {
            val context = LocalContext.current
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            PrimaryButton("Izinkan notifikasi", onClick = {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Supaya notifikasi tidak dimatikan HP", style = CatatType.cardTitle, color = CatatTheme.colors.textPrimary)
                    Text(
                        "HP Xiaomi, Oppo, Vivo, dan Realme suka mematikan app yang jalan di belakang. Buka info app ini, lalu:\n" +
                            "• Xiaomi: Hemat baterai → Tanpa batasan, dan nyalakan Mulai otomatis.\n" +
                            "• Oppo / Realme: Penggunaan baterai → izinkan aktivitas latar belakang.\n" +
                            "• Vivo: Baterai → izinkan penggunaan daya tinggi di latar belakang.",
                        style = CatatType.bodySmall,
                        color = CatatTheme.colors.textSecondary,
                    )
                    SecondaryButton("Buka info app", onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Lanjut", onClick = next)
        }
        OnboardingStep.BACKUP -> Page(7, "Folder auto-backup", "Backup mingguan disimpan ke folder ini. Boleh dilewati dan diatur nanti.") {
            val context = LocalContext.current
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    vm.setBackupFolder(uri.toString())
                }
            }
            SecondaryButton(if (f.backupFolder == null) "Pilih folder" else "Folder dipilih · ganti", onClick = { picker.launch(null) })
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (f.saving) "Menyimpan…" else "Mulai pakai", onClick = { vm.finish { } }, enabled = !f.saving)
        }
    }
}

@Composable
private fun PlanRow(label: String, amount: Long, bold: Boolean = false, note: String? = null) {
    val c = CatatTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = if (bold) CatatType.cardTitle else CatatType.bodySmall, color = c.textPrimary)
            note?.let { Text(it, style = CatatType.captionSmall, color = c.textSecondary) }
        }
        Text(rp(amount), style = CatatType.money.copy(fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold), color = if (amount < 0) c.dangerText else c.textPrimary)
    }
}

@Composable
private fun Page(
    number: Int,
    title: String,
    subtitle: String,
    scroll: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CatatTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(CatatShapes.screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Langkah $number dari 7", style = CatatType.caption, color = c.primary)
        Text(title, style = CatatType.screenTitle, color = c.textPrimary)
        Text(subtitle, style = CatatType.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    val c = CatatTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(CatatShapes.screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text("Catat Uang", style = CatatType.screenTitle.copy(fontSize = CatatType.heroAmount.fontSize), color = c.textPrimary)
        Text("Catat pengeluaran dalam 4 tap. Semua data tetap di HP ini.", style = CatatType.body, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        StartTile("Mulai Baru", "Atur pos, PIN, dan saldo awal", enabled = true, onClick = onStart)
        StartTile("Pulihkan dari Backup", "Tersedia di pembaruan berikutnya", enabled = false, onClick = {})
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StartTile(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val c = CatatTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface)
            .border(1.5.dp, if (enabled) c.primary else c.divider, CatatShapes.card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CatatType.cardTitle, color = if (enabled) c.textPrimary else c.textSecondary)
            Text(subtitle, style = CatatType.bodySmall, color = c.textSecondary)
        }
        Icon(LucideIcons.ChevronRight, contentDescription = null, tint = if (enabled) c.primary else c.divider, modifier = Modifier.size(20.dp))
    }
}
