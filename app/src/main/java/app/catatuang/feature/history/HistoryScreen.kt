package app.catatuang.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.Slot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.recordMonth
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.input.appendDigits
import app.catatuang.feature.input.backspace
import app.catatuang.ui.components.CategoryIcon
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.StatusPill
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.digits
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.shortDate
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import java.time.YearMonth

private val SLOT_LABEL = mapOf(Slot.SARAPAN to "Sarapan", Slot.SIANG to "Siang", Slot.MALAM to "Malam", Slot.JAJAN to "Jajan")

/** Satu baris transaksi (Riwayat, Detail). */
@Composable
fun TxRow(tx: Tx, ready: AppState.Ready, onClick: () -> Unit) {
    val c = CatatTheme.colors
    val cats = ready.input.categories
    val key = cats.firstOrNull { it.id == tx.categoryId }?.key ?: "lain"
    val acc = recordMonth(tx, cats)
    val sub = listOfNotNull(
        tx.slot?.let { SLOT_LABEL[it] },
        tx.note,
        if (acc != YearMonth.from(tx.date)) "dihitung ke ${monthName(acc)}" else null,
    ).joinToString(" · ")
    val sign = cashSign(tx)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(key, 36)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(txTitle(tx, cats), style = CatatType.body.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
            if (sub.isNotEmpty()) Text(sub, style = CatatType.caption.copy(fontWeight = FontWeight.Medium), color = c.textSecondary)
        }
        Text(
            when {
                sign > 0 -> "+${rp(sign)}"
                sign < 0 -> rp(sign)
                else -> rp(tx.amount)
            },
            style = CatatType.money.copy(fontSize = 14.sp),
            color = if (sign > 0) c.success else c.textPrimary,
        )
    }
}

@Composable
fun HistoryScreen(ready: AppState.Ready, onEdit: (Long) -> Unit) {
    val c = CatatTheme.colors
    val start = YearMonth.from(ready.input.onboarding.startDate)
    var monthText by rememberSaveable { mutableStateOf(ready.ledger.currentMonth.toString()) }
    val month = YearMonth.parse(monthText)
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    val cats = ready.input.categories
    val groups = historyGroups(ready.input.transactions, cats, month, filter)
    val closed = ready.ledger.months[month]?.closed == true

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Riwayat", style = CatatType.screenTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
                if (closed) StatusPill("Terkunci", Tone.NEUTRAL)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthArrow(LucideIcons.ArrowLeft, "Bulan sebelumnya", enabled = month > start) { monthText = month.minusMonths(1).toString() }
                Text(monthName(month, ready.ledger.currentMonth).let { if (month.year != ready.today.year) it else "$it ${month.year}" },
                    style = CatatType.cardTitle, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                MonthArrow(LucideIcons.ChevronRight, "Bulan berikutnya", enabled = month < ready.ledger.currentMonth) { monthText = month.plusMonths(1).toString() }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("Semua", filter == null) { filter = null }
                cats.filter { it.kind == CategoryKind.DAILY || it.kind == CategoryKind.STOCK }.forEach { cat ->
                    FilterChip(cat.name, filter == cat.id) { filter = cat.id }
                }
            }
        }
        if (groups.isEmpty()) {
            Text("Belum ada transaksi di bulan ini.", style = CatatType.bodySmall, color = c.textSecondary, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups, key = { it.date.toString() }) { g ->
                Column(Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(shortDate(g.date), style = CatatType.caption.copy(fontWeight = FontWeight.Bold), color = c.textSecondary, modifier = Modifier.weight(1f))
                        if (g.spent != 0L) Text("Keluar ${rp(g.spent)}", style = CatatType.caption.copy(fontWeight = FontWeight.Bold), color = c.textSecondary)
                    }
                    g.items.forEach { TxRow(it, ready, onClick = { onEdit(it.id) }) }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = CatatTheme.colors
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = if (enabled) c.textPrimary else c.divider, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = CatatTheme.colors
    Text(
        text,
        style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold),
        color = if (on) Color.White else c.textPrimary,
        modifier = Modifier.clip(CatatShapes.chip).background(if (on) c.primary else c.surface)
            .border(1.dp, if (on) c.primary else c.divider, CatatShapes.chip).clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** R-63 sheet edit: ubah nominal/slot/catatan atau hapus. Bulan tertutup → read-only (R-64). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTxSheet(tx: Tx, ready: AppState.Ready, vm: LedgerViewModel, onDismiss: () -> Unit) {
    val c = CatatTheme.colors
    val cats = ready.input.categories
    val category = cats.firstOrNull { it.id == tx.categoryId }
    val locked = ready.ledger.months[recordMonth(tx, cats)]?.closed == true
    val editable = isEditable(tx) && !locked
    var amount by remember { mutableLongStateOf(tx.amount) }
    var note by remember { mutableStateOf(tx.note.orEmpty()) }
    var slot by remember { mutableStateOf(tx.slot) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = CatatShapes.sheet, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryIcon(category?.key ?: "lain", 44)
                Column(Modifier.weight(1f)) {
                    Text(txTitle(tx, cats), style = CatatType.cardTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = c.textPrimary)
                    Text(shortDate(tx.date), style = CatatType.bodySmall, color = c.textSecondary)
                }
                if (locked) StatusPill("Terkunci", Tone.NEUTRAL)
            }
            Text("Rp ${digits(amount)}", style = CatatType.inputAmount.copy(fontSize = 40.sp), color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            when {
                locked -> NoticeBox("Bulan ini sudah ditutup. Koreksi dicatat di bulan berjalan (tersedia bersama Tutup Buku).", Tone.WARNING)
                !isEditable(tx) -> NoticeBox("Transaksi ini dibuat oleh alurnya sendiri; ubah lewat alur tersebut.", Tone.NEUTRAL)
            }
            if (editable) {
                if (category?.hasSlots == true && tx.type == TxType.EXPENSE) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.background).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Slot.entries.forEach { s ->
                            val on = s == slot
                            Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp)).background(if (on) c.surface else Color.Transparent).clickable { slot = s },
                                contentAlignment = Alignment.Center) {
                                Text(SLOT_LABEL.getValue(s), style = CatatType.bodySmall.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold), color = if (on) c.textPrimary else c.textSecondary)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(80) }, label = { Text("Catatan") }, singleLine = true, shape = CatatShapes.chip,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.divider),
                    modifier = Modifier.fillMaxWidth(),
                )
                Numpad(onDigits = { amount = appendDigits(amount, it) }, onBackspace = { amount = backspace(amount) })
                PrimaryButton("Simpan perubahan", enabled = amount > 0, onClick = {
                    vm.update(tx, tx.copy(amount = amount, note = note.trim().ifEmpty { null }, slot = slot))
                    onDismiss()
                })
                SecondaryButton("Hapus transaksi", onClick = { confirmDelete = true })
            } else {
                SecondaryButton(if (locked) "Buat koreksi (segera)" else "Tutup", enabled = !locked, onClick = onDismiss)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus transaksi ini?") },
            text = { Text("${txTitle(tx, cats)} ${rp(tx.amount)}. Bisa diurungkan 5 detik setelah dihapus.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(tx); onDismiss() }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}
