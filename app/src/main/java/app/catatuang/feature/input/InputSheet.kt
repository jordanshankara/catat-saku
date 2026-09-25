package app.catatuang.feature.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.engine.Category
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.Impact
import app.catatuang.engine.ImpactWarning
import app.catatuang.engine.useSavingsTransactions
import app.catatuang.engine.Slot
import app.catatuang.engine.defaultSlot
import app.catatuang.engine.isSuspiciousAmount
import app.catatuang.engine.needsDayQuestion
import app.catatuang.engine.quickAmounts
import app.catatuang.engine.recentAmounts
import app.catatuang.engine.selectableDateRange
import app.catatuang.engine.weekendWindowOf
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.ui.components.CategoryIcon
import app.catatuang.ui.components.DateChip
import app.catatuang.ui.components.HoldToConfirmButton
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.Tone
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.QuickAmountChips
import app.catatuang.ui.format.digits
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.rpShort
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

private val SLOT_LABEL = mapOf(Slot.SARAPAN to "Sarapan", Slot.SIANG to "Siang", Slot.MALAM to "Malam", Slot.JAJAN to "Jajan")

/** Isi sheet input pengeluaran (8.2). Dipasang di dalam ModalBottomSheet oleh pemanggil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputContent(
    category: Category,
    ready: AppState.Ready,
    vm: LedgerViewModel,
    onSaved: () -> Unit,
    nowTime: () -> LocalTime = LocalTime::now,
) {
    val c = CatatTheme.colors
    val today = ready.today
    var amount by rememberSaveable { mutableLongStateOf(0L) }
    var date by rememberSaveable { mutableStateOf(today) }
    var slot by rememberSaveable { mutableStateOf(defaultSlot(nowTime())) }
    var note by rememberSaveable { mutableStateOf("") }
    var askDay by rememberSaveable { mutableStateOf(needsDayQuestion(nowTime())) }
    var confirmTypo by remember { mutableStateOf<Long?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var impact by remember { mutableStateOf<Impact?>(null) }
    var useSavings by remember { mutableStateOf(false) }

    val closed = ready.ledger.months.values.filter { it.closed }.map { it.month }.toSet()
    val quick = remember(ready.input.transactions, category.id) { quickAmounts(ready.input.transactions, category, today) }

    LaunchedEffect(amount, date, slot, ready) {
        impact = vm.preview(expenseDraft(category, amount, date, slot, note))
    }

    fun setDate(d: LocalDate) {
        date = d
        if (category.hasSlots && d.isBefore(today)) slot = defaultSlot(nowTime(), isYesterday = true)
    }

    fun save() {
        val i = impact ?: return
        val draft = expenseDraft(category, amount, date, slot, note)
        val (text, tone) = feedbackText(category, amount, i, date, today)
        vm.saveExpense(draft, text, tone)
        onSaved()
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryIcon(category.key, 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(category.name, style = CatatType.cardTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = c.textPrimary)
                Text(headerSubtitle(category, ready, date), style = CatatType.bodySmall, color = c.textSecondary)
            }
            DateChip(dateLabel(date, today), onClick = { pickDate = true })
        }

        if (category.hasSlots) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.background).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Slot.entries.forEach { s ->
                    val on = s == slot
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (on) c.surface else Color.Transparent)
                            .clickable(role = Role.Tab) { slot = s }.semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(SLOT_LABEL.getValue(s), style = CatatType.bodySmall.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
                            color = if (on) c.textPrimary else c.textSecondary)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Rp", style = CatatType.body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = c.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text(digits(amount), style = CatatType.inputAmount, color = if (amount == 0L) c.divider else c.textPrimary)
            Spacer(Modifier.width(4.dp))
            Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(c.primary))
        }

        val lines = impact?.takeIf { amount > 0 }?.let { previewLines(category, it, date, today) }.orEmpty()
        lines.forEach { (text, tone) -> NoticeBox(text, tone) }
        impact?.takeIf { amount > 0 && ImpactWarning.USES_RESERVE in it.warnings && it.useSavingsAmount > 0 }?.let { i ->
            if (i.savingsCanCover) {
                SecondaryButton("Pakai Tabungan (Rencana) · ${rp(i.useSavingsAmount)}", onClick = { useSavings = true })
            } else {
                NoticeBox("Tabungan tidak cukup untuk menutup ${rp(i.useSavingsAmount)}. Dana Darurat tidak dipakai di sini.", Tone.WARNING)
            }
        }

        QuickAmountChips(quick, selected = amount.takeIf { it in quick }, onPick = { amount = it }, label = { rpShort(it).removePrefix("Rp ") })

        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(80) },
            label = { Text("Catatan (opsional)") },
            placeholder = { Text("mis. nasi padang") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = CatatShapes.chip,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, unfocusedBorderColor = c.divider,
                focusedContainerColor = Color(0xFFFAFBFE), unfocusedContainerColor = Color(0xFFFAFBFE)),
            modifier = Modifier.fillMaxWidth(),
        )

        Numpad(onDigits = { amount = appendDigits(amount, it) }, onBackspace = { amount = backspace(amount) })

        PrimaryButton(
            text = if (amount > 0) "Simpan · ${rp(amount)}" else "Simpan",
            enabled = amount > 0 && impact != null,
            onClick = {
                val check = isSuspiciousAmount(amount, recentAmounts(ready.input.transactions, category.id))
                if (check.suspicious) confirmTypo = check.median else save()
            },
        )
    }

    if (useSavings) {
        val i = impact
        AlertDialog(
            onDismissRequest = { useSavings = false },
            title = { Text("Pakai Tabungan (Rencana)?") },
            text = {
                Text("Ambil ${rp(i?.useSavingsAmount ?: 0)} dari Tabungan supaya Sisa bebas kembali Rp 0, lalu simpan pengeluaran ini. " +
                    "Alasan Rencana: tidak dihitung boncos. Dana Darurat tidak disentuh.")
            },
            confirmButton = {
                HoldToConfirmButton("Tahan 3 detik · ambil ${rp(i?.useSavingsAmount ?: 0)}", danger = false, onConfirmed = {
                    useSavings = false
                    val cur = impact ?: return@HoldToConfirmButton
                    val draft = expenseDraft(category, amount, date, slot, note)
                    val txs = useSavingsTransactions(cur, draft) ?: return@HoldToConfirmButton
                    val (text, tone) = feedbackText(category, amount, cur, date, today)
                    vm.saveAll(txs, "$text · Tabungan −${rp(cur.useSavingsAmount)} (rencana)", tone, detailCategoryId = category.id)
                    onSaved()
                })
            },
            dismissButton = { TextButton(onClick = { useSavings = false }) { Text("Batal") } },
        )
    }

    if (askDay) {
        AlertDialog(
            onDismissRequest = { askDay = false; setDate(today.minusDays(1)) },
            title = { Text("Untuk hari ini atau kemarin?") },
            text = { Text("Sekarang sudah lewat tengah malam. Catatan ini untuk tanggal berapa?") },
            confirmButton = { TextButton(onClick = { askDay = false; setDate(today.minusDays(1)) }) { Text("Kemarin") } },
            dismissButton = { TextButton(onClick = { askDay = false; setDate(today) }) { Text("Hari ini") } },
        )
    }

    confirmTypo?.let { median ->
        AlertDialog(
            onDismissRequest = { confirmTypo = null },
            title = { Text("Yakin ${rp(amount)}?") },
            text = { Text("Biasanya ±${rp(median)}.") },
            confirmButton = { TextButton(onClick = { confirmTypo = null; save() }) { Text("Ya, simpan") } },
            dismissButton = { TextButton(onClick = { confirmTypo = null }) { Text("Ubah") } },
        )
    }

    if (pickDate) {
        val allowed = remember(closed, today) {
            selectableDateRange(ready.input.onboarding.startDate, today, closed).map { it.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.toSet()
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in allowed
                override fun isSelectableYear(year: Int) = year in ready.input.onboarding.startDate.year..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { setDate(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Batal") } },
        ) { DatePicker(state = state) }
    }
}

private fun headerSubtitle(category: Category, ready: AppState.Ready, date: LocalDate): String {
    val l = ready.ledger
    return when {
        category.kind == CategoryKind.DAILY -> {
            val d = l.daily.firstOrNull { it.categoryId == category.id }
            if (date == ready.today && d != null) {
                if (d.remaining >= 0) "Sisa jatah hari ini ${rp(d.remaining)}" else "Lebih ${rp(-d.remaining)} dari jatah hari ini"
            } else {
                "Jatah ${rp(d?.jatah ?: 0)} per hari"
            }
        }
        category.weekendMode -> {
            val w = weekendWindowOf(date)
            val owner = w?.let { l.months[it.owner] }
            val info = owner?.windows?.firstOrNull { it.window.saturday == w.saturday }
            when {
                info == null -> "Di luar akhir pekan · trip tambahan"
                !info.funded -> "Akhir pekan ke-5 · dari reservasi"
                else -> "Akhir pekan ini: sisa ${rp(owner.weekendAllowance - info.used)}"
            }
        }
        else -> {
            val m = l.months[YearMonth.from(date)] ?: l.current
            val s = m?.stock?.firstOrNull { it.categoryId == category.id }
            val budget = s?.budget ?: m?.allocation?.firstOrNull { it.categoryId == category.id }?.monthlyAmount ?: 0
            "Sisa budget ${rp(budget - (s?.used ?: 0))}"
        }
    }
}
