package app.catatuang.feature.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.engine.Category
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.StockStatus
import app.catatuang.engine.monthlyReport
import app.catatuang.engine.weekStart
import app.catatuang.engine.weeklyReport
import app.catatuang.feature.closing.VerdictCard
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.ui.components.AmountRow
import app.catatuang.ui.components.ChoiceChips
import app.catatuang.ui.components.SectionCard
import app.catatuang.ui.components.StatusPill
import app.catatuang.ui.components.ThinProgress
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.dayShort
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.rpShort
import app.catatuang.ui.format.shortDate
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Warna identitas pos untuk grafik: palet kategorikal tervalidasi (urutan tetap, D-60) —
 * warna mengikuti pos (urutan sortOrder), bukan peringkat nilai.
 */
private val SERIES = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
    Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948),
)

fun seriesColor(categories: List<Category>, id: Long): Color {
    val index = categories.sortedBy { it.sortOrder }.indexOfFirst { it.id == id }
    return SERIES[(index.coerceAtLeast(0)) % SERIES.size]
}

enum class ReportTab { MINGGUAN, BULANAN }

@Composable
fun ReportScreen(ready: AppState.Ready, vm: LedgerViewModel, onExport: () -> Unit) {
    val c = CatatTheme.colors
    var tab by rememberSaveable { mutableStateOf(ReportTab.MINGGUAN) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Laporan", style = CatatType.screenTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(CatatShapes.chip).background(c.surface).clickable(role = Role.Button, onClick = onExport).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(LucideIcons.Report, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
                Text("Export", style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = c.primary)
            }
        }
        ChoiceChips(listOf(ReportTab.MINGGUAN to "Mingguan", ReportTab.BULANAN to "Bulanan"), tab, onSelect = { tab = it })
        when (tab) {
            ReportTab.MINGGUAN -> Weekly(ready)
            ReportTab.BULANAN -> Monthly(ready)
        }
    }
}

@Composable
private fun Stepper(label: String, canBack: Boolean, canNext: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    val c = CatatTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow(LucideIcons.ArrowLeft, "Sebelumnya", canBack, onBack)
        Text(label, style = CatatType.cardTitle, color = c.textPrimary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Arrow(LucideIcons.ChevronRight, "Berikutnya", canNext, onNext)
    }
}

@Composable
private fun Arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = CatatTheme.colors
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = if (enabled) c.textPrimary else c.divider, modifier = Modifier.size(20.dp)) }
}

private fun weekLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    return if (start.month == end.month) "${start.dayOfMonth}–${end.dayOfMonth} ${monthName(YearMonth.from(end)).take(3)} ${end.year}"
    else "${start.dayOfMonth} ${monthName(YearMonth.from(start)).take(3)} – ${end.dayOfMonth} ${monthName(YearMonth.from(end)).take(3)}"
}

@Composable
private fun Weekly(ready: AppState.Ready) {
    val c = CatatTheme.colors
    val cats = ready.input.categories
    val first = weekStart(ready.input.onboarding.startDate)
    val current = weekStart(ready.today)
    var startText by rememberSaveable { mutableStateOf(current.toString()) }
    val start = LocalDate.parse(startText)
    val report = remember(ready, start) { weeklyReport(ready.input, ready.ledger, start) }

    Stepper(weekLabel(start), start > first, start < current, { startText = start.minusWeeks(1).toString() }, { startText = start.plusWeeks(1).toString() })

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Total pengeluaran", style = CatatType.bodySmall, color = c.textSecondary)
            Text(rp(report.total), style = CatatType.heroAmount.copy(fontSize = 28.sp), color = c.textPrimary)
            report.changePercent?.let { pct ->
                val (text, tone) = when {
                    pct > 0 -> "Naik $pct% dari minggu lalu (${rp(report.previousTotal)})" to Tone.WARNING
                    pct < 0 -> "Turun ${-pct}% dari minggu lalu (${rp(report.previousTotal)})" to Tone.SUCCESS
                    else -> "Sama dengan minggu lalu" to Tone.NEUTRAL
                }
                StatusPill(text, tone)
            } ?: Text("Minggu lalu belum ada pengeluaran.", style = CatatType.caption, color = c.textSecondary)
        }
    }

    if (report.daily.isNotEmpty()) DailyLineChart(report.daily, cats)

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Per pos", style = CatatType.cardTitle, color = c.textPrimary)
            val max = report.perCategory.values.maxOrNull()?.coerceAtLeast(1) ?: 1
            if (report.perCategory.isEmpty()) Text("Belum ada pengeluaran.", style = CatatType.bodySmall, color = c.textSecondary)
            report.perCategory.entries.sortedBy { e -> cats.firstOrNull { it.id == e.key }?.sortOrder ?: 0 }.forEach { (id, v) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AmountRow(cats.firstOrNull { it.id == id }?.name ?: "pos", rp(v))
                    ThinProgress(v.toFloat() / max, seriesColor(cats, id))
                }
            }
        }
    }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ringkasan minggu", style = CatatType.cardTitle, color = c.textPrimary)
            report.weekends.forEach { w ->
                val result = when {
                    !w.funded -> if (w.closed) "akhir pekan ke-5 · dari reservasi" else "ke-5 · berjalan"
                    !w.closed -> "berjalan · ${rp(w.used)} / ${rp(w.allowance)}"
                    else -> "${rp(w.used)} / ${rp(w.allowance)} · ${(w.toSaku ?: 0).let { if (it >= 0) "+${rp(it)} ke Sisa" else "${rp(it)} dari Sisa" }}"
                }
                AmountRow("Transport ${shortDate(w.saturday)}", result)
            }
            AmountRow("Hari paling boros", report.worstDay?.let { "${shortDate(it.first)} · ${rp(it.second)}" } ?: "—")
            AmountRow("Hutang harian", "${rp(report.debtStart)} → ${rp(report.debtEnd)}")
            AmountRow("Perubahan Saku Sisa", report.sakuChange?.let { (if (it >= 0) "+" else "") + rp(it) } ?: "— (lintas bulan)")
        }
    }
}

/**
 * Grafik garis harian Makan & Buah vs jatah (8.10): satu sumbu rupiah, garis 2dp, penanda 8dp, jatah
 * putus-putus. Ketuk hari untuk melihat angkanya (lapisan detail).
 */
@Composable
private fun DailyLineChart(daily: Map<Long, List<app.catatuang.engine.DayUsage>>, cats: List<Category>) {
    val c = CatatTheme.colors
    val days = daily.values.flatten().map { it.date }.distinct().sorted()
    var selected by remember(days) { mutableStateOf(days.lastOrNull()) }
    val max = daily.values.flatten().maxOf { maxOf(it.used, it.jatah) }.coerceAtLeast(1).toFloat() * 1.1f
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Harian vs jatah", style = CatatType.cardTitle, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                daily.keys.forEach { id ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(seriesColor(cats, id)))
                        Text(cats.first { it.id == id }.name, style = CatatType.caption, color = c.textSecondary)
                    }
                }
                Text("- - jatah", style = CatatType.caption, color = c.textSecondary)
            }
            val lineColors = daily.keys.associateWith { seriesColor(cats, it) }
            val grid = c.divider
            val surface = c.surface
            Canvas(
                Modifier.fillMaxWidth().height(160.dp).pointerInput(days) {
                    detectTapGestures { pos ->
                        if (days.isEmpty()) return@detectTapGestures
                        val step = size.width / 7f
                        selected = days.minByOrNull { d -> kotlin.math.abs((d.dayOfWeek.value - 1) * step + step / 2 - pos.x) }
                    }
                }.semantics { contentDescription = "Grafik harian Makan dan Buah terhadap jatah" },
            ) {
                val step = size.width / 7f
                fun x(d: LocalDate) = (d.dayOfWeek.value - 1) * step + step / 2
                fun y(v: Long) = size.height - (v / max) * size.height
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                selected?.let { drawLine(grid, Offset(x(it), 0f), Offset(x(it), size.height), 1.dp.toPx()) }
                daily.forEach { (id, list) ->
                    val color = lineColors.getValue(id)
                    val jatah = list.firstOrNull()?.jatah ?: 0
                    drawLine(color.copy(alpha = 0.55f), Offset(0f, y(jatah)), Offset(size.width, y(jatah)), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    list.zipWithNext().forEach { (a, b) ->
                        drawLine(color, Offset(x(a.date), y(a.used)), Offset(x(b.date), y(b.used)), 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                    list.forEach { p ->
                        drawCircle(surface, 6.dp.toPx(), Offset(x(p.date), y(p.used)))
                        drawCircle(color, 4.dp.toPx(), Offset(x(p.date), y(p.used)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val d = days.firstOrNull()?.let { weekStart(it).plusDays(i.toLong()) }
                    Text(d?.let(::dayShort) ?: "", style = CatatType.captionSmall, color = if (d == selected) c.textPrimary else c.textSecondary,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            selected?.let { d ->
                Column(Modifier.fillMaxWidth().clip(CatatShapes.chip).background(c.background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(shortDate(d), style = CatatType.caption.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    daily.forEach { (id, list) ->
                        list.firstOrNull { it.date == d }?.let { u ->
                            val over = u.used - u.jatah
                            AmountRow(cats.first { it.id == id }.name, "${rp(u.used)} / ${rp(u.jatah)}" + if (over > 0) " · lebih ${rpShort(over)}" else "")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Monthly(ready: AppState.Ready) {
    val c = CatatTheme.colors
    val cats = ready.input.categories
    val first = YearMonth.from(ready.input.onboarding.startDate)
    val current = ready.ledger.currentMonth
    var monthText by rememberSaveable { mutableStateOf(current.toString()) }
    val month = YearMonth.parse(monthText)
    val report = remember(ready, month) { monthlyReport(ready.input, ready.ledger, month) } ?: return
    val m = report.summary

    Stepper("${monthName(month)} ${month.year}", month > first, month < current, { monthText = month.minusMonths(1).toString() }, { monthText = month.plusMonths(1).toString() })
    VerdictCard(m, report.reconciled, provisional = report.provisional)

    val spend = report.perCategory.filterValues { it > 0 }.entries.sortedBy { e -> cats.firstOrNull { it.id == e.key }?.sortOrder ?: 0 }
    if (spend.isNotEmpty()) {
        val total = spend.sumOf { it.value }
        var pick by remember(month) { mutableStateOf<Long?>(null) }
        val surface = c.surface
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pengeluaran per pos", style = CatatType.cardTitle, color = c.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(132.dp).semantics { contentDescription = "Donut pengeluaran per pos" }) {
                            val stroke = 22.dp.toPx()
                            val inset = stroke / 2 + 4.dp.toPx()
                            var angle = -90f
                            spend.forEach { (id, v) ->
                                val sweep = 360f * v / total
                                val chosen = pick == id
                                drawArc(seriesColor(cats, id), angle, sweep, false, topLeft = Offset(inset, inset),
                                    size = Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(if (chosen) stroke + 6.dp.toPx() else stroke))
                                // Celah 2dp antar potongan (warna permukaan).
                                drawArc(surface, angle + sweep - 0.8f, 0.8f, false, topLeft = Offset(inset, inset),
                                    size = Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(stroke + 8.dp.toPx()))
                                angle += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (pick != null) (cats.firstOrNull { it.id == pick }?.name ?: "") else "Total", style = CatatType.captionSmall, color = c.textSecondary)
                            Text(rpShort(pick?.let { report.perCategory[it] } ?: total), style = CatatType.money, color = c.textPrimary)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        spend.forEach { (id, v) ->
                            Row(
                                Modifier.fillMaxWidth().clip(CatatShapes.chip).clickable { pick = if (pick == id) null else id }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(seriesColor(cats, id)))
                                Text(cats.firstOrNull { it.id == id }?.name ?: "", style = CatatType.caption, color = c.textPrimary, modifier = Modifier.weight(1f))
                                Text("${Math.round(v * 100.0 / total)}%", style = CatatType.caption, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Budget vs realisasi", style = CatatType.cardTitle, color = c.textPrimary)
            Row {
                listOf("Pos" to 1.4f, "Budget" to 1f, "Terpakai" to 1f).forEach { (h, w) ->
                    Text(h, style = CatatType.captionSmall, color = c.textSecondary, modifier = Modifier.weight(w))
                }
            }
            report.rows.forEach { r ->
                val cat = cats.firstOrNull { it.id == r.categoryId } ?: return@forEach
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, style = CatatType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, modifier = Modifier.weight(1.4f))
                        Text(rpShort(r.budget), style = CatatType.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text(rpShort(r.used), style = CatatType.caption.copy(fontWeight = FontWeight.Bold), color = c.textPrimary, modifier = Modifier.weight(1f))
                    }
                    if (cat.kind != CategoryKind.SAVING && r.budget > 0) {
                        val (label, tone) = when (r.status) {
                            StockStatus.LEBIH -> "Lebih ${rp(-r.remaining)}" to Tone.DANGER
                            StockStatus.WASPADA -> "Waspada · sisa ${rp(r.remaining)}" to Tone.WARNING
                            StockStatus.NORMAL -> "Sisa ${rp(r.remaining)}" to Tone.NEUTRAL
                        }
                        Text(label, style = CatatType.captionSmall, color = if (tone == Tone.NEUTRAL) c.textSecondary else app.catatuang.ui.components.toneColors(tone).content)
                    }
                }
            }
        }
    }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tabungan & Dana Darurat", style = CatatType.cardTitle, color = c.textPrimary)
            AmountRow("Tabungan masuk", rp(m.tabunganMasuk))
            AmountRow("Tabungan keluar", rp(m.tabunganKeluar))
            AmountRow("Dana Darurat masuk", rp(m.danaDaruratMasuk))
            AmountRow("Dana Darurat terpakai", rp(m.danaDaruratTerpakai))
            if (m.rencanaWithdrawn > 0) AmountRow("Ambil rencana", rp(m.rencanaWithdrawn))
            if (m.daruratWithdrawn > 0) AmountRow("Ambil darurat", rp(m.daruratWithdrawn))
        }
    }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hutang & hari", style = CatatType.cardTitle, color = c.textPrimary)
            AmountRow("Hutang masuk awal bulan", rp(m.hutangMasuk))
            AmountRow(if (m.monthEnded) "Hutang dibawa" else "Hutang sekarang", rp(m.hutangDibawa))
            report.dayStats.forEach { (id, s) ->
                AmountRow(cats.firstOrNull { it.id == id }?.name ?: "", "${s.savedDays} hari hemat · ${s.overDays} hari lebih · +${rpShort(s.toSaku)} ke Sisa")
            }
            report.reconciled?.let { AmountRow("Cocokkan saldo", if (it < 0) "Tidak tercatat ${rp(-it)}" else "Selisih lebih ${rp(it)}") }
        }
    }
    Spacer(Modifier.height(4.dp))
    Spacer(Modifier.width(0.dp))
}
