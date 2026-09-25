package app.catatuang.feature.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.engine.Category
import app.catatuang.engine.Slot
import app.catatuang.engine.coverShortfall
import app.catatuang.engine.dailyMonthStats
import app.catatuang.engine.holdTarget
import app.catatuang.engine.lastSevenDays
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.history.TxRow
import app.catatuang.ui.components.HoldToConfirmButton
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.dayShort
import app.catatuang.ui.format.digits
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.rpShort
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

private val SLOT_NAMES = listOf(Slot.SARAPAN to "Sarapan", Slot.SIANG to "Siang", Slot.MALAM to "Malam", Slot.JAJAN to "Jajan")

/** 8.3 Detail pos HARIAN — dipanggil di dalam CatatUangTheme(dark = true). */
@Composable
fun DailyDetailScreen(
    category: Category,
    ready: AppState.Ready,
    vm: LedgerViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val c = CatatTheme.colors
    val l = ready.ledger
    val d = l.daily.firstOrNull { it.categoryId == category.id } ?: return
    var emergency by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Kembali" },
                contentAlignment = Alignment.Center,
            ) { Icon(LucideIcons.ArrowLeft, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(22.dp)) }
            Text("Jatah ${category.name}", style = CatatType.cardTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = c.textPrimary,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(monthName(l.currentMonth).take(3), style = CatatType.caption.copy(fontWeight = FontWeight.Bold), color = c.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.surface).padding(horizontal = 10.dp, vertical = 6.dp))
        }

        // Gauge setengah lingkaran: terpakai vs jatah hari ini.
        Column(
            Modifier.fillMaxWidth().clip(CatatShapes.hero).background(c.surface).padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val fraction = if (d.jatah > 0) (d.used.toFloat() / d.jatah).coerceIn(0f, 1f) else 0f
            val over = d.used > d.jatah
            Box(Modifier.width(260.dp).height(140.dp), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val arcSize = Size(size.width - stroke, (size.width - stroke))
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(c.track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(if (over) c.danger else c.primary, 180f, 180f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (over) rp(d.jatah - d.used) else rp(d.remaining),
                        style = CatatType.heroAmount.copy(fontSize = 30.sp), color = if (over) c.dangerText else c.textPrimary)
                    Text(if (over) "lebih dari jatah hari ini" else "sisa jatah hari ini", style = CatatType.caption, color = c.textSecondary)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Terpakai ${rp(d.used)}", style = CatatType.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text("Jatah ${rp(d.jatah)}", style = CatatType.caption, color = c.textSecondary)
            }
        }

        if (category.hasSlots) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SLOT_NAMES.forEach { (slot, name) ->
                    val v = d.bySlot[slot] ?: 0
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, style = CatatType.captionSmall, color = c.textSecondary)
                        Text(if (v == 0L) "—" else rpShort(v).removePrefix("Rp "), style = CatatType.money.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                            color = if (v == 0L) Color(0xFF6B7299) else c.textPrimary)
                    }
                }
            }
        }

        if (d.hutang > 0) {
            Column(
                Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.dangerBg).border(1.dp, c.danger.copy(alpha = 0.45f), CatatShapes.card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Hutang ${category.name.lowercase()}", style = CatatType.caption, color = Color(0xFFFFB3C0))
                        Text(rp(d.hutang), style = CatatType.heroAmount.copy(fontSize = 22.sp), color = c.dangerText)
                    }
                    Text("Mode darurat", style = CatatType.caption.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFFD1D9),
                        modifier = Modifier.clip(CatatShapes.chip).border(1.dp, c.dangerText, CatatShapes.chip).clickable(role = Role.Button) { emergency = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp))
                }
                holdTarget(d.jatah, d.hutang, d.used)?.let { x ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(LucideIcons.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Text("Tahan di ${rpShort(x)} sampai akhir hari — hutang lunas saat hari ditutup tengah malam.",
                            style = CatatType.bodySmall, color = Color(0xFFEBDDE3))
                    }
                }
                if (d.largeDebt) Text("Hutang sudah lebih dari 3× jatah. Pertimbangkan Mode Darurat.", style = CatatType.bodySmall, color = Color(0xFFFFB3C0))
            }
        }

        WeekChart(ready, category)

        val stats = dailyMonthStats(l, category.id)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Hari hemat", stats.savedDays.toString(), c.success, Modifier.weight(1f))
            Stat("Hari lebih", stats.overDays.toString(), c.dangerText, Modifier.weight(1f))
            Stat("Ke Saku Sisa", rpShort(stats.toSaku), c.textPrimary, Modifier.weight(1f))
        }

        val todays = ready.input.transactions.filter { it.categoryId == category.id && it.date == ready.today }
            .sortedByDescending { it.createdAt }
        if (todays.isNotEmpty()) {
            Text("Hari ini", style = CatatType.cardTitle, color = c.textPrimary)
            Column(Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface)) {
                todays.forEach { TxRow(it, ready, onClick = { onEdit(it.id) }) }
            }
        }
    }

    if (emergency) EmergencySheet(category, ready, vm, onDismiss = { emergency = false })
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    val c = CatatTheme.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = CatatType.captionSmall, color = c.textSecondary)
        Text(value, style = CatatType.money.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = color)
    }
}

@Composable
private fun WeekChart(ready: AppState.Ready, category: Category) {
    val c = CatatTheme.colors
    val days = lastSevenDays(ready.ledger, category.id)
    if (days.isEmpty()) return
    val jatah = days.maxOf { it.jatah }.coerceAtLeast(1)
    val max = maxOf(days.maxOf { it.used }, jatah) * 1.25f
    Column(Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("7 hari terakhir", style = CatatType.body.copy(fontWeight = FontWeight.ExtraBold), color = c.textPrimary, modifier = Modifier.weight(1f))
            Text("- - jatah ${rpShort(jatah).removePrefix("Rp ")}", style = CatatType.captionSmall, color = c.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { day ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (day.over > 0) Text("+${(day.over / 1000)}", style = CatatType.captionSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = c.dangerText)
                        val h = (day.used / max).coerceIn(0.02f, 1f)
                        val color = when {
                            day.used > day.jatah -> c.danger
                            day.date == ready.today -> c.success.copy(alpha = 0.85f)
                            else -> c.primary
                        }
                        Box(Modifier.fillMaxWidth().fillMaxHeight(h).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)).background(color))
                    }
                }
            }
            // Garis putus-putus setinggi jatah, memotong batang (mockup DetailMakan).
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * (1f - jatah / max)
                drawLine(Color(0xFF8C93BA), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEach { day ->
                Text(dayShort(day.date), style = CatatType.captionSmall.copy(fontWeight = if (day.date == ready.today) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = if (day.date == ready.today) c.textPrimary else c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Text("Garis putus-putus = jatah per hari. Angka merah = kelebihan (ribuan).", style = CatatType.captionSmall, color = c.textSecondary)
    }
}

/** R-23 Mode Darurat: urutan penutup; porsi dari kantong wajib tahan 3 detik di layar merah. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencySheet(category: Category, ready: AppState.Ready, vm: LedgerViewModel, onDismiss: () -> Unit) {
    val l = ready.ledger
    val debt = l.hutang[category.id] ?: 0
    var amount by remember { mutableLongStateOf(debt) }
    val cover = coverShortfall(amount, l.sakuSisa, l.tabungan, l.danaDarurat)
    val touchesPots = cover.fromPots > 0
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (touchesPots) Color(0xFF3A0F1C) else CatatTheme.colors.surface,
        shape = CatatShapes.sheet,
    ) {
        val text = if (touchesPots) Color(0xFFFFE3E8) else CatatTheme.colors.textPrimary
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mode darurat · hutang ${category.name.lowercase()}", style = CatatType.cardTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = text)
            Text("Lunasi berapa? (maks ${rp(debt)})", style = CatatType.bodySmall, color = text.copy(alpha = 0.8f))
            Text("Rp ${digits(amount)}", style = CatatType.inputAmount.copy(fontSize = 40.sp), color = text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CoverLine("Dari Saku Sisa", cover.fromSaku, text)
                CoverLine("Dari Tabungan", cover.fromTabungan, text)
                CoverLine("Dari Dana Darurat", cover.fromDanaDarurat, text)
            }
            if (cover.fromDanaDarurat > 0) NoticeBox("Ini dana darurat terakhir lu. Tercatat sebagai ambil DARURAT (verdict BONCOS).", Tone.DANGER)
            else if (touchesPots) NoticeBox("Mengambil ${rp(cover.fromPots)} dari Tabungan. Tercatat sebagai ambil DARURAT (verdict BONCOS).", Tone.DANGER)
            if (cover.uncovered > 0) NoticeBox("Semua kantong tidak cukup; hanya ${rp(amount - cover.uncovered)} yang bisa dilunasi.", Tone.WARNING)
            Numpad(onDigits = { d ->
                val s = (if (amount == 0L) "" else amount.toString()) + d
                amount = minOf(debt, s.trimStart('0').take(12).toLongOrNull() ?: 0)
            }, onBackspace = { amount /= 10 })
            if (touchesPots) {
                HoldToConfirmButton("Tahan 3 detik · lunasi ${rp(amount - cover.uncovered)}", onConfirmed = {
                    vm.payDebt(category.id, amount); onDismiss()
                })
            } else {
                PrimaryButton("Lunasi ${rp(amount)} dari Saku Sisa", enabled = amount > 0, onClick = { vm.payDebt(category.id, amount); onDismiss() })
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CoverLine(label: String, value: Long, color: Color) {
    if (value <= 0) return
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = CatatType.bodySmall, color = color, modifier = Modifier.weight(1f))
        Text(rp(value), style = CatatType.money.copy(fontSize = 14.sp), color = color)
    }
}

