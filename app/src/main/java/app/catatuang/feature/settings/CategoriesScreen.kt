package app.catatuang.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.catatuang.data.AppState
import app.catatuang.engine.Category
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.archivedFrom
import app.catatuang.engine.canArchive
import app.catatuang.engine.newCategory
import app.catatuang.engine.nextEditableMonth
import app.catatuang.engine.valueIn
import app.catatuang.engine.withAmount
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.ui.components.CategoryIcon
import app.catatuang.ui.components.ChoiceChips
import app.catatuang.ui.components.MoneyField
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.ScreenHeader
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.SectionCard
import app.catatuang.ui.components.StatusPill
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import kotlinx.coroutines.launch
import java.time.YearMonth

private fun kindLabel(k: CategoryKind) = when (k) {
    CategoryKind.DAILY -> "Harian"
    CategoryKind.STOCK -> "Stok (bulanan)"
    CategoryKind.FIXED -> "Tetap (tagihan)"
    CategoryKind.SAVING -> "Tabungan"
}

private fun unit(c: Category) = if (c.kind == CategoryKind.DAILY) "/ hari" else "/ bulan"

/** 8.11 pos & nominal, tambah/arsip pos (R-95), jatuh tempo pos tetap. */
@Composable
fun CategoriesScreen(ready: AppState.Ready, vm: LedgerViewModel, onBack: () -> Unit) {
    val c = CatatTheme.colors
    val current = ready.ledger.currentMonth
    val from = nextEditableMonth(ready.input, current)
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    val cats = ready.input.categories.sortedBy { it.sortOrder }

    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Pos & nominal", onBack = onBack, subtitle = "Perubahan berlaku mulai ${monthName(from, current)}")
        if (from > current.plusMonths(1)) {
            NoticeBox("Gaji ${monthName(current.plusMonths(1), current)} sudah di-split, jadi perubahan baru berlaku ${monthName(from, current)}.", Tone.INFO)
        }
        listOf(CategoryKind.DAILY, CategoryKind.STOCK, CategoryKind.FIXED, CategoryKind.SAVING).forEach { kind ->
            val group = cats.filter { it.kind == kind && (it.archivedFrom == null || it.archivedFrom!! > current) }
            if (group.isEmpty()) return@forEach
            Text(kindLabel(kind), style = CatatType.cardTitle, color = c.textSecondary)
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.forEach { cat -> CategoryRow(cat, current, from) { editing = cat } }
                }
            }
        }
        val archived = cats.filter { it.archivedFrom != null && it.archivedFrom!! <= current }
        if (archived.isNotEmpty()) {
            Text("Diarsipkan", style = CatatType.cardTitle, color = c.textSecondary)
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    archived.forEach { Text("${it.name} · sejak ${monthName(it.archivedFrom!!, current)}", style = CatatType.bodySmall, color = c.textSecondary) }
                }
            }
        }
        PrimaryButton("Tambah pos", onClick = { adding = true })
        Text("Pos baru muncul di preview split Gajian berikutnya. Pos yang diarsipkan tetap tampil di riwayat & laporan lama.",
            style = CatatType.caption, color = c.textSecondary)
    }

    editing?.let { cat -> EditCategorySheet(cat, current, from, vm, onDismiss = { editing = null }) }
    if (adding) AddCategorySheet(ready, from, vm, onDismiss = { adding = false })
}

@Composable
private fun CategoryRow(cat: Category, current: YearMonth, from: YearMonth, onClick: () -> Unit) {
    val c = CatatTheme.colors
    val now = cat.valueIn(current)
    val next = cat.valueIn(from)
    Row(
        Modifier.fillMaxWidth().clip(CatatShapes.chip).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(cat.key, 36)
        Column(Modifier.weight(1f)) {
            Text(cat.name, style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
            val line = when {
                !cat.isActiveIn(current) && cat.activeFrom != null -> "Baru · ${rp(next ?: 0)} ${unit(cat)} mulai ${monthName(cat.activeFrom!!, current)}"
                next != null && next != now -> "${rp(now ?: 0)} → ${rp(next)} ${unit(cat)} mulai ${monthName(from, current)}"
                else -> "${rp(now ?: 0)} ${unit(cat)}"
            }
            Text(line, style = CatatType.caption, color = c.textSecondary)
            if (cat.kind == CategoryKind.FIXED) Text("Jatuh tempo tanggal ${cat.dueDay ?: 1}", style = CatatType.caption, color = c.textSecondary)
            cat.archivedFrom?.let { Text("Diarsipkan mulai ${monthName(it, current)}", style = CatatType.caption, color = c.warningText) }
        }
        Icon(LucideIcons.ChevronRight, contentDescription = null, tint = c.textSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCategorySheet(cat: Category, current: YearMonth, from: YearMonth, vm: LedgerViewModel, onDismiss: () -> Unit) {
    val c = CatatTheme.colors
    var amount by remember { mutableLongStateOf(cat.valueIn(from) ?: 0L) }
    var due by remember { mutableStateOf((cat.dueDay ?: 1).toString()) }
    var confirmArchive by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = CatatShapes.sheet, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(cat.name, style = CatatType.cardTitle, color = c.textPrimary)
            MoneyField("Nominal ${unit(cat)} mulai ${monthName(from, current)}", amount, onChange = { amount = it },
                supporting = "Sekarang ${rp(cat.valueIn(current) ?: 0)} ${unit(cat)}")
            if (cat.kind == CategoryKind.FIXED) {
                OutlinedTextField(
                    value = due, onValueChange = { v -> due = v.filter(Char::isDigit).take(2) },
                    label = { Text("Tanggal jatuh tempo (1–31)") }, singleLine = true, shape = CatatShapes.chip,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.divider),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Tanggal jatuh tempo langsung berlaku (tidak mengubah nominal).", style = CatatType.caption, color = c.textSecondary)
            }
            val dueDay = due.toIntOrNull()?.takeIf { it in 1..31 }
            PrimaryButton("Simpan", enabled = cat.kind != CategoryKind.FIXED || dueDay != null, onClick = {
                var updated = if (amount != cat.valueIn(from)) {
                    if (cat.kind == CategoryKind.DAILY) cat.withAmount(from, amount, null) else cat.withAmount(from, null, amount)
                } else cat
                if (cat.kind == CategoryKind.FIXED) updated = updated.copy(dueDay = dueDay)
                vm.saveCategory(updated, "${cat.name} disimpan · berlaku ${monthName(from, current)}")
                onDismiss()
            })
            if (cat.canArchive() && cat.archivedFrom == null) {
                SecondaryButton("Arsipkan mulai ${monthName(from, current)}", onClick = { confirmArchive = true })
                if (confirmArchive) {
                    NoticeBox("${cat.name} tidak muncul lagi mulai ${monthName(from, current)}. Riwayat & laporan lama tetap ada.", Tone.WARNING)
                    PrimaryButton("Ya, arsipkan", color = c.dangerFill, onClick = {
                        vm.saveCategory(cat.archivedFrom(from), "${cat.name} diarsipkan mulai ${monthName(from, current)}")
                        onDismiss()
                    })
                }
            } else if (cat.archivedFrom != null) {
                StatusPill("Diarsipkan mulai ${monthName(cat.archivedFrom!!, current)}", Tone.WARNING)
                SecondaryButton("Batalkan arsip", onClick = { vm.saveCategory(cat.copy(archivedFrom = null), "Arsip ${cat.name} dibatalkan"); onDismiss() })
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategorySheet(ready: AppState.Ready, from: YearMonth, vm: LedgerViewModel, onDismiss: () -> Unit) {
    val c = CatatTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.STOCK) }
    var amount by remember { mutableLongStateOf(0L) }
    var due by remember { mutableStateOf("1") }
    val current = ready.ledger.currentMonth
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = CatatShapes.sheet, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tambah pos", style = CatatType.cardTitle, color = c.textPrimary)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(24) }, label = { Text("Nama pos") }, singleLine = true, shape = CatatShapes.chip,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.divider), modifier = Modifier.fillMaxWidth(),
            )
            ChoiceChips(listOf(CategoryKind.STOCK to "Stok (budget bulanan)", CategoryKind.FIXED to "Tetap (tagihan)"), kind, onSelect = { kind = it })
            MoneyField(if (kind == CategoryKind.FIXED) "Estimasi per bulan" else "Budget per bulan", amount, onChange = { amount = it })
            if (kind == CategoryKind.FIXED) {
                OutlinedTextField(
                    value = due, onValueChange = { v -> due = v.filter(Char::isDigit).take(2) }, label = { Text("Tanggal jatuh tempo (1–31)") },
                    singleLine = true, shape = CatatShapes.chip,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.divider), modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Aktif mulai ${monthName(from, current)} dan muncul di preview split Gajian berikutnya.", style = CatatType.caption, color = c.textSecondary)
            val dueDay = due.toIntOrNull()?.takeIf { it in 1..31 }
            PrimaryButton("Tambah", enabled = name.isNotBlank() && (kind != CategoryKind.FIXED || dueDay != null), onClick = {
                scope.launch {
                    val id = vm.nextCategoryId()
                    val order = (ready.input.categories.maxOfOrNull { it.sortOrder } ?: 0) + 1
                    vm.saveCategory(newCategory(id, name, kind, amount, from, order, dueDay), "${name.trim()} ditambahkan · mulai ${monthName(from, current)}")
                    onDismiss()
                }
            })
            Spacer(Modifier.height(4.dp))
        }
    }
}
