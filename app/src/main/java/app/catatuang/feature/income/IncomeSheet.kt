package app.catatuang.feature.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.engine.Category
import app.catatuang.engine.Destination
import app.catatuang.engine.IncomeKind
import app.catatuang.engine.Pot
import app.catatuang.engine.Tx
import app.catatuang.engine.defaultIncomeDestination
import app.catatuang.engine.incomeBudgetCategories
import app.catatuang.engine.incomeTx
import app.catatuang.engine.refundCategories
import app.catatuang.engine.refundTx
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.input.appendDigits
import app.catatuang.feature.input.backspace
import app.catatuang.feature.input.dateLabel
import app.catatuang.ui.components.BigAmount
import app.catatuang.ui.components.ChoiceChips
import app.catatuang.ui.components.DateChip
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.RangeDatePickerDialog
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.rp
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import java.time.LocalDate
import java.time.YearMonth

/** Jenis di sheet Pemasukan (6.8). Pengembalian dicatat sebagai REFUND. */
enum class IncomeChoice(val label: String, val kind: IncomeKind?) {
    PEMBERIAN("Pemberian", IncomeKind.PEMBERIAN),
    SAMPINGAN("Penghasilan sampingan", IncomeKind.SAMPINGAN),
    THR_BONUS("THR / Bonus", IncomeKind.THR_BONUS),
    PENGEMBALIAN("Pengembalian", null),
    LAINNYA("Lainnya", IncomeKind.LAINNYA),
}

/** Tujuan pemasukan: "SAKU", "POT:TABUNGAN", "POT:DANA_DARURAT", "CAT:<id>". */
fun destinationKey(dest: Destination, pot: Pot?, categoryId: Long? = null): String = when (dest) {
    Destination.SAKU -> "SAKU"
    Destination.POT -> "POT:${pot ?: Pot.TABUNGAN}"
    Destination.CATEGORY -> "CAT:$categoryId"
}

fun destinationOptions(categories: List<Category>, month: YearMonth): List<Pair<String, String>> =
    listOf("SAKU" to "Saku Sisa", "POT:TABUNGAN" to "Tabungan", "POT:DANA_DARURAT" to "Dana Darurat") +
        incomeBudgetCategories(categories, month).map { "CAT:${it.id}" to "Budget ${it.name}" }

/** Transaksi dari pilihan sheet; null bila belum lengkap. */
fun buildIncome(choice: IncomeChoice, amount: Long, date: LocalDate, dest: String?, refundCategory: Long?, note: String?): Tx? {
    if (amount <= 0) return null
    val n = note?.trim()?.ifEmpty { null }
    if (choice.kind == null) return refundCategory?.let { refundTx(it, amount, date, n) }
    val key = dest ?: return null
    return when {
        key == "SAKU" -> incomeTx(choice.kind, amount, date, Destination.SAKU, note = n)
        key.startsWith("POT:") -> incomeTx(choice.kind, amount, date, Destination.POT, Pot.valueOf(key.removePrefix("POT:")), note = n)
        else -> incomeTx(choice.kind, amount, date, Destination.CATEGORY, categoryId = key.removePrefix("CAT:").toLong(), note = n)
    }
}

fun incomeFeedback(tx: Tx, categories: List<Category>): String {
    val name = categories.firstOrNull { it.id == tx.categoryId }?.name
    return when {
        tx.incomeKind == null -> "Pengembalian +${rp(tx.amount)} → terpakai $name berkurang"
        tx.destination == Destination.POT -> "Pemasukan +${rp(tx.amount)} → ${if (tx.pot == Pot.DANA_DARURAT) "Dana Darurat" else "Tabungan"}"
        tx.destination == Destination.CATEGORY -> "Pemasukan +${rp(tx.amount)} → budget $name"
        else -> "Pemasukan +${rp(tx.amount)} → Saku Sisa"
    }
}

/** 8.5 sheet Pemasukan: jenis → nominal → tujuan → catatan → simpan. */
@Composable
fun IncomeContent(ready: AppState.Ready, vm: LedgerViewModel, onSaved: () -> Unit) {
    val c = CatatTheme.colors
    val today = ready.today
    val cats = ready.input.categories
    var choice by rememberSaveable { mutableStateOf(IncomeChoice.PEMBERIAN) }
    var amount by rememberSaveable { mutableLongStateOf(0L) }
    var date by rememberSaveable { mutableStateOf(today) }
    var dest by rememberSaveable { mutableStateOf<String?>("SAKU") }
    var refundCat by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    val month = YearMonth.from(date)

    fun choose(ch: IncomeChoice) {
        choice = ch
        ch.kind?.let { k -> val (d, p) = defaultIncomeDestination(k); dest = destinationKey(d, p) }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pemasukan", style = CatatType.cardTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = c.textPrimary, modifier = Modifier.weight(1f))
            DateChip(dateLabel(date, today), onClick = { pickDate = true })
        }
        ChoiceChips(IncomeChoice.entries.map { it to it.label }, choice, onSelect = ::choose)
        BigAmount(amount)
        if (choice.kind == null) {
            Text("Kembali ke pos mana?", style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
            ChoiceChips(refundCategories(cats, month).map { it.id to it.name }, refundCat, onSelect = { refundCat = it })
            Text("Mengurangi \"terpakai\" pos itu pada tanggal yang dipilih.", style = CatatType.caption, color = c.textSecondary)
        } else {
            Text("Masuk ke", style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
            ChoiceChips(destinationOptions(cats, month), dest, onSelect = { dest = it })
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(80) },
            label = { Text("Catatan (opsional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = CatatShapes.chip,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.divider),
            modifier = Modifier.fillMaxWidth(),
        )
        Numpad(onDigits = { amount = appendDigits(amount, it) }, onBackspace = { amount = backspace(amount) })
        val tx = buildIncome(choice, amount, date, dest, refundCat, note)
        PrimaryButton(
            text = if (amount > 0) "Simpan · ${rp(amount)}" else "Simpan",
            enabled = tx != null,
            color = c.successFill,
            onClick = {
                val t = tx ?: return@PrimaryButton
                vm.saveAll(listOf(t), incomeFeedback(t, cats), Tone.SUCCESS, detailCategoryId = t.categoryId)
                onSaved()
            },
        )
    }

    if (pickDate) {
        val closed = ready.ledger.months.values.filter { it.closed }.map { it.month }.toSet()
        var min = ready.input.onboarding.startDate
        while (YearMonth.from(min) in closed) min = YearMonth.from(min).plusMonths(1).atDay(1)
        RangeDatePickerDialog(date, min, today, onPick = { date = it }, onDismiss = { pickDate = false })
    }
}
