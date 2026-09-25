package app.catatuang.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.ui.format.digits
import app.catatuang.ui.format.rp
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Nominal besar "Rp 45.000" dengan kursor (8.2). */
@Composable
fun BigAmount(amount: Long, modifier: Modifier = Modifier, color: Color = CatatTheme.colors.textPrimary) {
    val c = CatatTheme.colors
    Row(modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("Rp", style = CatatType.body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = c.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(digits(amount), style = CatatType.inputAmount.copy(fontSize = 42.sp), color = if (amount == 0L) c.divider else color)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(c.primary))
    }
}

/** Pilihan tunggal berbentuk chip; teks selalu tampil (prinsip 3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = CatatTheme.colors
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Row(
                Modifier.heightIn(min = CatatShapes.minTouch).clip(CatatShapes.chip)
                    .background(if (on) Color(0xFFE3E9FF) else c.surface)
                    .border(1.5.dp, if (on) c.primary else c.divider, CatatShapes.chip)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (on) Icon(LucideIcons.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(14.dp))
                Text(label, style = CatatType.bodySmall.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = if (on) Color(0xFF1E3A9E) else c.textPrimary)
            }
        }
    }
}

/** Angka bayangan bergaris putus-putus untuk porsi talangan (R-04, 8.13 ShadowAmount). */
@Composable
fun ShadowAmount(amount: Long, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .drawBehind {
                drawRoundRect(
                    color = color.copy(alpha = 0.7f),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${rp(amount)} talangan" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("−${rp(amount)}", style = CatatType.caption.copy(fontFeatureSettings = "tnum"), color = color)
        Text("talangan", style = CatatType.captionSmall, color = color)
    }
}

/** Header layar penuh: tombol kembali + judul. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    val c = CatatTheme.colors
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Kembali" },
            contentAlignment = Alignment.Center,
        ) { Icon(LucideIcons.ArrowLeft, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = CatatType.screenTitle, color = c.textPrimary)
            subtitle?.let { Text(it, style = CatatType.bodySmall, color = c.textSecondary) }
        }
    }
}

/** Baris label–nilai untuk ringkasan angka. */
@Composable
fun AmountRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false, color: Color = CatatTheme.colors.textPrimary) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (strong) CatatType.body.copy(fontWeight = FontWeight.Bold) else CatatType.bodySmall,
            color = if (strong) color else CatatTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = CatatType.money.copy(fontSize = if (strong) 16.sp else 14.sp), color = color)
    }
}

private fun LocalDate.millis() = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

/** Date picker yang dibatasi ke rentang [min]..[max]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeDatePickerDialog(initial: LocalDate, min: LocalDate, max: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in min.millis()..max.millis()
            override fun isSelectableYear(year: Int) = year in min.year..max.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    ) { DatePicker(state = state) }
}
