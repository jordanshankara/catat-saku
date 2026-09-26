package app.catatuang.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import app.catatuang.ui.theme.CategoryPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Nada status; selalu dipasangkan dengan teks/ikon (prinsip 3). */
enum class Tone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER, SAVINGS, EMERGENCY }

data class ToneColors(val content: Color, val background: Color)

@Composable
fun toneColors(tone: Tone): ToneColors {
    val c = CatatTheme.colors
    return when (tone) {
        Tone.NEUTRAL -> ToneColors(c.textSecondary, c.background)
        Tone.INFO -> ToneColors(c.primary, c.infoBg)
        Tone.SUCCESS -> ToneColors(c.success, c.successBg)
        Tone.WARNING -> ToneColors(c.warningText, c.warningBg)
        Tone.DANGER -> ToneColors(c.dangerText, c.dangerBg)
        Tone.SAVINGS -> ToneColors(c.savings, c.savingsBg)
        Tone.EMERGENCY -> ToneColors(c.emergency, c.emergencyBg)
    }
}

fun toneIcon(tone: Tone): ImageVector? = when (tone) {
    Tone.SUCCESS -> LucideIcons.Check
    Tone.WARNING, Tone.DANGER -> LucideIcons.AlertTriangle
    else -> null
}

/** Ikon & warna pos (bagian 12). Pos baru memakai palet Lain-lain. */
data class CategoryVisual(val icon: ImageVector, val background: Color, val tint: Color)

fun categoryVisual(key: String): CategoryVisual = when (key) {
    "makan" -> CategoryVisual(LucideIcons.Bowl, CategoryPalette.makan.first, CategoryPalette.makan.second)
    "buah" -> CategoryVisual(LucideIcons.Fruit, CategoryPalette.buah.first, CategoryPalette.buah.second)
    "transport" -> CategoryVisual(LucideIcons.Bus, CategoryPalette.transport.first, CategoryPalette.transport.second)
    "protein" -> CategoryVisual(LucideIcons.Dumbbell, CategoryPalette.protein.first, CategoryPalette.protein.second)
    else -> CategoryVisual(LucideIcons.Package, CategoryPalette.lain.first, CategoryPalette.lain.second)
}

@Composable
fun CategoryIcon(key: String, size: Int = 40, modifier: Modifier = Modifier) {
    val v = categoryVisual(key)
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(v.background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(v.icon, contentDescription = null, tint = v.tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val t = toneColors(tone)
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(t.background).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        toneIcon(tone)?.let { Icon(it, contentDescription = null, tint = t.content, modifier = Modifier.size(12.dp)) }
        Text(text, style = CatatType.captionSmall.copy(fontWeight = FontWeight.Bold), color = t.content)
    }
}

@Composable
fun ThinProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = CatatTheme.colors.track) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(track)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(999.dp)).background(color),
        )
    }
}

/** Kotak pesan dengan ikon + teks (preview dampak, peringatan). */
@Composable
fun NoticeBox(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val t = toneColors(tone)
    Row(
        modifier.fillMaxWidth().clip(CatatShapes.chip).background(t.background)
            .border(1.dp, t.content.copy(alpha = 0.35f), CatatShapes.chip).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(toneIcon(tone) ?: LucideIcons.Check, contentDescription = null, tint = t.content, modifier = Modifier.size(16.dp))
        Text(text, style = CatatType.caption.copy(fontSize = 12.5.sp), color = t.content)
    }
}

/** Numpad 1–9, 000, 0, hapus (8.2). Tombol 50dp. */
@Composable
fun Numpad(onDigits: (String) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier, tripleZero: Boolean = true) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (tripleZero) "000" else "", "0", "⌫")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.weight(1f).height(CatatShapes.numpadKey))
                        return@forEach
                    }
                    val isBack = key == "⌫"
                    Box(
                        Modifier.weight(1f).height(CatatShapes.numpadKey).clip(RoundedCornerShape(14.dp))
                            .background(CatatTheme.colors.keyBg)
                            .clickable(role = Role.Button) { if (isBack) onBackspace() else onDigits(key) }
                            .semantics { if (isBack) contentDescription = "Hapus angka" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isBack) {
                            Icon(LucideIcons.Delete, contentDescription = null, tint = CatatTheme.colors.textPrimary, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                key,
                                style = CatatType.body.copy(fontSize = if (key == "000") 18.sp else 22.sp, fontWeight = FontWeight.SemiBold),
                                color = CatatTheme.colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tombol utama biru (16dp). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = CatatTheme.colors.primaryFill) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CatatShapes.button)
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CatatType.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CatatTheme.colors
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CatatShapes.button).border(1.5.dp, c.divider, CatatShapes.button)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = if (enabled) c.textPrimary else c.textSecondary)
    }
}

/** Chip nominal cepat (8.2). */
@Composable
fun QuickAmountChips(amounts: List<Long>, selected: Long?, onPick: (Long) -> Unit, label: (Long) -> String) {
    val c = CatatTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        amounts.forEach { a ->
            val on = a == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(CatatShapes.chip)
                    .background(if (on) c.infoBg else c.surface)
                    .border(1.5.dp, if (on) c.primary else c.divider, CatatShapes.chip)
                    .clickable(role = Role.Button) { onPick(a) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(a), style = CatatType.body.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold), color = if (on) c.selectedText else c.textPrimary)
            }
        }
        repeat(4 - amounts.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
fun DateChip(text: String, onClick: () -> Unit) {
    val c = CatatTheme.colors
    Row(
        Modifier.clip(CatatShapes.chip).background(c.background).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(LucideIcons.Calendar, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(15.dp))
        Text(text, style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
    }
}

/** Kartu feedback setelah simpan (R-62): warna + ikon + teks. */
@Composable
fun FeedbackCard(text: String, tone: Tone, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val t = toneColors(tone)
    Row(
        modifier.fillMaxWidth().clip(CatatShapes.card).background(CatatTheme.colors.surface)
            .border(1.5.dp, t.content.copy(alpha = 0.5f), CatatShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(t.background), contentAlignment = Alignment.Center) {
            Icon(toneIcon(tone) ?: LucideIcons.Check, contentDescription = null, tint = t.content, modifier = Modifier.size(18.dp))
        }
        Text(text, style = CatatType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = CatatTheme.colors.textPrimary)
    }
}

/**
 * 8.13: tahan 3 detik dengan cincin progres; lepas sebelum selesai = batal; getar singkat saat berhasil.
 * Wajib untuk semua aksi yang mengurangi Tabungan atau Dana Darurat.
 */
@Composable
fun HoldToConfirmButton(text: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = true, enabled: Boolean = true) {
    val c = CatatTheme.colors
    val progress = remember { Animatable(0f) }
    val confirm by rememberUpdatedState(onConfirmed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val color = (if (danger) c.dangerFill else c.primaryFill).let { if (enabled) it else it.copy(alpha = 0.4f) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).clip(CatatShapes.button).background(color)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$text. Tahan 3 detik untuk konfirmasi"
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    var job: Job? = null
                    job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(3_000, easing = LinearEasing))
                        vibrate(context)
                        confirm()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            drawArc(Color.White.copy(alpha = 0.3f), 0f, 360f, false, style = Stroke(4.dp.toPx()))
            drawArc(Color.White, -90f, 360f * progress.value, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = CatatType.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp), color = Color.White)
    }
}

@Suppress("DEPRECATION")
private fun vibrate(context: android.content.Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(CatatShapes.card).background(CatatTheme.colors.surface).padding(16.dp)) { content() }
}
