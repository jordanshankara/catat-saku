package app.catatuang.feature.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.catatuang.data.AppState
import app.catatuang.export.ExportFormat
import app.catatuang.export.Ranges
import app.catatuang.export.buildExport
import app.catatuang.export.exportFileName
import app.catatuang.export.shareFile
import app.catatuang.ui.components.ChoiceChips
import app.catatuang.ui.components.DateChip
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.RangeDatePickerDialog
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.shortDate
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

private enum class RangeKind { MINGGU, BULAN, CUSTOM }

/** Bab 10 menu Export: rentang → format → bagikan atau simpan (SAF). */
@Composable
fun ExportContent(ready: AppState.Ready, onDone: () -> Unit) {
    val c = CatatTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = ready.today
    val first = YearMonth.from(ready.input.onboarding.startDate)
    var kind by remember { mutableStateOf(RangeKind.BULAN) }
    var month by remember { mutableStateOf(ready.ledger.currentMonth) }
    var from by remember { mutableStateOf(ready.ledger.currentMonth.atDay(1).coerceAtLeast(ready.input.onboarding.startDate)) }
    var to by remember { mutableStateOf(today) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val range = when (kind) {
        RangeKind.MINGGU -> Ranges.week(today)
        RangeKind.BULAN -> Ranges.month(month)
        RangeKind.CUSTOM -> Ranges.custom(from, to)
    }
    val name = exportFileName(range, format, today)
    suspend fun bytes() = withContext(Dispatchers.Default) { buildExport(context, ready.input, ready.ledger, range, format) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = runCatching {
                val data = bytes()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(data) } }
                "Tersimpan: $name" to Tone.SUCCESS
            }.getOrElse { "Gagal menyimpan: ${it.message}" to Tone.DANGER }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Export laporan", style = CatatType.cardTitle, color = c.textPrimary)
        Text("Rentang", style = CatatType.caption, color = c.textSecondary)
        ChoiceChips(listOf(RangeKind.MINGGU to "Minggu ini", RangeKind.BULAN to "Bulan", RangeKind.CUSTOM to "Custom"), kind, onSelect = { kind = it })
        when (kind) {
            RangeKind.BULAN -> {
                val months = generateSequence(ready.ledger.currentMonth) { it.minusMonths(1) }.takeWhile { it >= first }.take(12).toList()
                ChoiceChips(months.map { it to "${monthName(it).take(3)} ${it.year}" }, month, onSelect = { month = it })
            }
            RangeKind.CUSTOM -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateChip("Dari ${shortDate(from)}", onClick = { pickFrom = true })
                DateChip("Sampai ${shortDate(to)}", onClick = { pickTo = true })
            }
            RangeKind.MINGGU -> Text("${shortDate(range.from)} – ${shortDate(range.to)}", style = CatatType.bodySmall, color = c.textPrimary)
        }
        Text("Format", style = CatatType.caption, color = c.textSecondary)
        ChoiceChips(listOf(ExportFormat.PDF to "PDF", ExportFormat.XLSX to "Excel (.xlsx)"), format, onSelect = { format = it })
        Text(name, style = CatatType.caption, color = c.textSecondary)
        message?.let { (m, tone) -> NoticeBox(m, tone) }
        PrimaryButton(if (busy) "Menyiapkan…" else "Bagikan", enabled = !busy, onClick = {
            scope.launch {
                busy = true
                runCatching { val data = bytes(); shareFile(context, name, format.mime) { it.writeBytes(data) } }
                    .onFailure { message = "Gagal membuat file: ${it.message}" to Tone.DANGER }
                busy = false
            }
        })
        SecondaryButton("Simpan ke…", enabled = !busy, onClick = { save.launch(name) })
        Spacer(Modifier.height(4.dp))
    }

    val start = ready.input.onboarding.startDate
    if (pickFrom) RangeDatePickerDialog(from, start, today, onPick = { from = it }, onDismiss = { pickFrom = false })
    if (pickTo) RangeDatePickerDialog(to, start, today, onPick = { to = it }, onDismiss = { pickTo = false })
}
