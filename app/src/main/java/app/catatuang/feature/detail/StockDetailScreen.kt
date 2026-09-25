package app.catatuang.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.engine.Category
import app.catatuang.engine.StockStatus
import app.catatuang.engine.TxType
import app.catatuang.engine.accountingMonth
import app.catatuang.engine.stockStatus
import app.catatuang.engine.weekendWindowOf
import app.catatuang.feature.history.TxRow
import app.catatuang.ui.components.CategoryIcon
import app.catatuang.ui.components.StatusPill
import app.catatuang.ui.components.ThinProgress
import app.catatuang.ui.components.Tone
import app.catatuang.ui.components.toneColors
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.shortDate
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

private fun statusPill(s: StockStatus) = when (s) {
    StockStatus.NORMAL -> "Normal" to Tone.SUCCESS
    StockStatus.WASPADA -> "Waspada" to Tone.WARNING
    StockStatus.LEBIH -> "Lebih" to Tone.DANGER
}

/** 8.4 Detail pos STOK (tema terang). Transport: daftar akhir pekan & trip tambahan. */
@Composable
fun StockDetailScreen(category: Category, ready: AppState.Ready, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val c = CatatTheme.colors
    val l = ready.ledger
    val month = l.current ?: return
    val cats = ready.input.categories
    val monthTx = ready.input.transactions.filter { it.categoryId == category.id && accountingMonth(it, cats) == l.currentMonth }
        .sortedWith(compareByDescending<app.catatuang.engine.Tx> { it.date }.thenByDescending { it.createdAt })

    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Kembali" },
                contentAlignment = Alignment.Center,
            ) { Icon(LucideIcons.ArrowLeft, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(22.dp)) }
            CategoryIcon(category.key, 40)
            Column(Modifier.weight(1f)) {
                Text(category.name, style = CatatType.cardTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = c.textPrimary)
                Text(monthName(l.currentMonth), style = CatatType.caption, color = c.textSecondary)
            }
        }

        if (category.weekendMode) {
            val t = l.transport
            val budget = month.transportBudget
            val remaining = t?.remainingBudget ?: budget
            SummaryCard(budget, budget - remaining, remaining, null, extra = "Trip ${t?.tripsTaken ?: 0} dari ${month.saturdays} · jatah ${rp(month.weekendAllowance)} per akhir pekan")
            Text("Akhir pekan ${monthName(l.currentMonth)}", style = CatatType.cardTitle, color = c.textPrimary)
            Column(Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface).padding(vertical = 4.dp)) {
                month.windows.forEach { w ->
                    val log = l.weekendLog.firstOrNull { it.window.saturday == w.window.saturday }
                    val detail = when {
                        !w.funded -> "dari reservasi"
                        log != null && log.toSaku > 0 -> "+${rp(log.toSaku)} ke Sisa"
                        log != null && log.toSaku < 0 -> "${rp(log.toSaku)} dari Sisa"
                        w.closed -> "pas"
                        weekendWindowOf(ready.today)?.saturday == w.window.saturday -> "sedang berjalan"
                        else -> "belum"
                    }
                    val tone = when {
                        w.funded && w.used > month.weekendAllowance -> Tone.DANGER
                        w.funded && w.used > 0 -> toneFor(stockStatus(w.used, month.weekendAllowance))
                        else -> Tone.NEUTRAL
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${shortDate(w.window.saturday)} · ${rp(w.used)}", style = CatatType.body.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                            Text("Akhir pekan ke-${w.index}", style = CatatType.caption, color = c.textSecondary)
                        }
                        StatusPill(detail, tone)
                    }
                }
            }
            val extra = monthTx.filter { weekendWindowOf(it.date) == null && it.type == TxType.EXPENSE }
            if (extra.isNotEmpty()) {
                Text("Trip tambahan (di luar akhir pekan)", style = CatatType.cardTitle, color = c.textPrimary)
                Column(Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface)) { extra.forEach { TxRow(it, ready) { onEdit(it.id) } } }
            }
        } else {
            val s = l.stock.firstOrNull { it.categoryId == category.id }
            val budget = s?.budget ?: month.allocation.firstOrNull { it.categoryId == category.id }?.monthlyAmount ?: 0
            val used = s?.used ?: 0
            SummaryCard(budget, used, budget - used, s?.status ?: StockStatus.NORMAL, extra = null)
        }

        Text("Transaksi bulan ini", style = CatatType.cardTitle, color = c.textPrimary)
        if (monthTx.isEmpty()) {
            Text("Belum ada transaksi.", style = CatatType.bodySmall, color = c.textSecondary)
        } else {
            Column(Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface)) { monthTx.forEach { TxRow(it, ready) { onEdit(it.id) } } }
        }
    }
}

private fun toneFor(s: StockStatus) = when (s) {
    StockStatus.NORMAL -> Tone.SUCCESS
    StockStatus.WASPADA -> Tone.WARNING
    StockStatus.LEBIH -> Tone.DANGER
}

@Composable
private fun SummaryCard(budget: Long, used: Long, remaining: Long, status: StockStatus?, extra: String?) {
    val c = CatatTheme.colors
    val st = status ?: stockStatus(used, budget)
    val (label, tone) = statusPill(st)
    Column(Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sisa budget", style = CatatType.caption, color = c.textSecondary)
                Text(rp(remaining), style = CatatType.heroAmount.copy(fontSize = 28.sp), color = if (remaining < 0) c.dangerText else c.textPrimary)
            }
            StatusPill(label, tone)
        }
        ThinProgress(if (budget > 0) used.toFloat() / budget else 0f, toneColors(if (tone == Tone.SUCCESS) Tone.INFO else tone).content)
        Row(Modifier.fillMaxWidth()) {
            Text("Terpakai ${rp(used)}", style = CatatType.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text("Budget ${rp(budget)}", style = CatatType.caption, color = c.textSecondary)
        }
        extra?.let { Text(it, style = CatatType.bodySmall, color = c.textSecondary) }
    }
}
