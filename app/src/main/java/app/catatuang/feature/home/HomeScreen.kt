package app.catatuang.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.ui.components.CategoryIcon
import app.catatuang.ui.components.FeedbackCard
import app.catatuang.ui.components.StatusPill
import app.catatuang.ui.components.ThinProgress
import app.catatuang.ui.components.Tone
import app.catatuang.ui.components.toneColors
import app.catatuang.ui.components.toneIcon
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

private val HeroText = Color(0xFFD6E0FF)

@Composable
fun HomeScreen(
    ui: HomeUi,
    feedback: Pair<String, Tone>?,
    onFeedbackClick: () -> Unit,
    onTile: (Long) -> Unit,
    onTileLong: (Long) -> Unit,
    onNotifications: () -> Unit,
    onNotYet: (String) -> Unit,
) {
    val c = CatatTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ui.date, style = CatatType.bodySmall, color = c.textSecondary)
                Text(ui.greeting, style = CatatType.screenTitle, color = c.textPrimary)
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                    .clickable(role = Role.Button, onClick = onNotifications)
                    .semantics { contentDescription = "Notifikasi, ${ui.notices.size} pengingat aktif" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(LucideIcons.Bell, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(22.dp))
                if (ui.notices.isNotEmpty()) {
                    Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp).clip(RoundedCornerShape(999.dp)).background(c.danger))
                }
            }
        }

        feedback?.let { (text, tone) -> FeedbackCard(text, tone, onClick = onFeedbackClick) }

        Hero(ui.hero)

        ui.salaryBanner?.let { b ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.goldBg).border(1.5.dp, c.gold, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { onNotYet("Alur Gajian dibuat di Fase 3.") }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.gold), contentAlignment = Alignment.Center) {
                    Icon(LucideIcons.Wallet, contentDescription = null, tint = Color(0xFF5C3D00), modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(b.title, style = CatatType.cardTitle, color = Color(0xFF3D2A00))
                    Text(b.subtitle, style = CatatType.caption.copy(fontWeight = FontWeight.Medium), color = c.goldText)
                }
                Text("Input", style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    modifier = Modifier.clip(CatatShapes.chip).background(c.goldButton).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        ui.closingBanner?.let {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.warningBg)
                    .clickable(role = Role.Button) { onNotYet("Alur Tutup Buku dibuat di Fase 4.") }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(LucideIcons.AlertTriangle, contentDescription = null, tint = c.warningText, modifier = Modifier.size(18.dp))
                Text(it, style = CatatType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = c.warningText)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Catat pengeluaran", style = CatatType.cardTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = c.textPrimary, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(CatatShapes.chip).background(c.successBg).clickable(role = Role.Button) { onNotYet("Pemasukan dibuat di Fase 3.") }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(LucideIcons.TrendingUp, contentDescription = null, tint = Color(0xFF0F6B45), modifier = Modifier.size(16.dp))
                Text("Pemasukan", style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0F6B45))
            }
        }

        val (wide, grid) = ui.tiles.partition { it.wide }
        grid.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Tile(it, Modifier.weight(1f), onTile, onTileLong) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        wide.forEach { WideTile(it, onTile, onTileLong) }
    }
}

@Composable
private fun Hero(h: HeroUi) {
    Column(
        Modifier.fillMaxWidth().shadow(12.dp, CatatShapes.hero, ambientColor = Color(0x471E3A9E), spotColor = Color(0x471E3A9E))
            .clip(CatatShapes.hero).background(CatatTheme.colors.heroGradient).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(h.label, style = CatatType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = HeroText)
                Text(h.amount, style = if (h.talangan) CatatType.heroAmount.copy(fontSize = 28.sp) else CatatType.heroAmount, color = Color.White)
            }
            Text(h.month, style = CatatType.caption.copy(fontWeight = FontWeight.Bold), color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        h.status?.let { HeroStatus(it, h.statusTone) }
        h.talanganDetail?.let { Text(it, style = CatatType.bodySmall, color = HeroText) }
        h.talanganShortfall?.let { HeroStatus(it, Tone.DANGER) }
        h.pending?.let { HeroStatus(it, Tone.SUCCESS) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            h.chips.forEach { chip ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(chip.label, style = CatatType.captionSmall, color = HeroText)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (chip.tone == Tone.DANGER) Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFFFF8FA3)))
                        Text(chip.value, style = CatatType.money, color = Color.White)
                    }
                }
            }
        }
        h.cadangan?.let {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(it, style = CatatType.caption, color = Color.White)
                ThinProgress(h.cadanganFraction, Color(0xFFF2C14E), track = Color.White.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun HeroStatus(text: String, tone: Tone) {
    val dot = when (tone) {
        Tone.SUCCESS -> Color(0xFF3DD598)
        Tone.WARNING -> Color(0xFFF2C14E)
        Tone.DANGER -> Color(0xFFFF8FA3)
        else -> Color.White
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(toneIcon(tone) ?: LucideIcons.Check, contentDescription = null, tint = dot, modifier = Modifier.size(14.dp))
        Text(text, style = CatatType.caption, color = Color.White)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(t: TileUi, modifier: Modifier, onTile: (Long) -> Unit, onTileLong: (Long) -> Unit) {
    val c = CatatTheme.colors
    Column(
        modifier.shadow(4.dp, CatatShapes.card, ambientColor = Color(0x0F1E285A), spotColor = Color(0x0F1E285A))
            .clip(CatatShapes.card).background(c.surface)
            .combinedClickable(role = Role.Button, onLongClickLabel = "Buka detail ${t.name}", onLongClick = { onTileLong(t.categoryId) }) { onTile(t.categoryId) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            CategoryIcon(t.key, 40)
            Spacer(Modifier.weight(1f))
            t.badge?.let { StatusPill(it, t.badgeTone) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.name, style = CatatType.cardTitle, color = c.textPrimary)
            TileStatus(t)
        }
        ThinProgress(t.progress, toneColors(t.progressTone).content)
    }
}

@Composable
private fun TileStatus(t: TileUi) {
    val c = CatatTheme.colors
    val color = if (t.statusTone == Tone.NEUTRAL) c.textSecondary else toneColors(t.statusTone).content
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (t.statusTone != Tone.NEUTRAL) toneIcon(t.statusTone)?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(13.dp)) }
        Text(t.status, style = CatatType.caption.copy(fontWeight = if (t.statusTone == Tone.NEUTRAL) FontWeight.Medium else FontWeight.SemiBold), color = color)
    }
    t.secondLine?.let { Text(it, style = CatatType.caption.copy(fontWeight = FontWeight.Medium), color = c.textSecondary) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WideTile(t: TileUi, onTile: (Long) -> Unit, onTileLong: (Long) -> Unit) {
    val c = CatatTheme.colors
    Column(
        Modifier.fillMaxWidth().shadow(4.dp, CatatShapes.card, ambientColor = Color(0x0F1E285A), spotColor = Color(0x0F1E285A))
            .clip(CatatShapes.card).background(c.surface)
            .combinedClickable(role = Role.Button, onLongClickLabel = "Buka detail ${t.name}", onLongClick = { onTileLong(t.categoryId) }) { onTile(t.categoryId) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryIcon(t.key, 40)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.name, style = CatatType.cardTitle, color = c.textPrimary)
                TileStatus(t)
            }
            t.badge?.let { StatusPill(it, t.badgeTone) }
            Icon(LucideIcons.ChevronRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
        ThinProgress(t.progress, toneColors(t.progressTone).content)
    }
}
