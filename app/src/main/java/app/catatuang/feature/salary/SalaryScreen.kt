package app.catatuang.feature.salary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.catatuang.data.AppState
import app.catatuang.data.CatatRepository
import app.catatuang.engine.AllocationLine
import app.catatuang.engine.SalaryPrep
import app.catatuang.engine.SalaryStatus
import app.catatuang.engine.allocationFits
import app.catatuang.engine.defaultTargetMonth
import app.catatuang.engine.hasSalary
import app.catatuang.engine.kebutuhanStandar
import app.catatuang.engine.planSplit
import app.catatuang.engine.prepareSalary
import app.catatuang.engine.salaryActivationDate
import app.catatuang.engine.salaryTransactions
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.common.UiEvent
import app.catatuang.feature.input.appendDigits
import app.catatuang.feature.input.backspace
import app.catatuang.feature.input.dateLabel
import app.catatuang.ui.components.AmountRow
import app.catatuang.ui.components.BigAmount
import app.catatuang.ui.components.ChoiceChips
import app.catatuang.ui.components.DateChip
import app.catatuang.ui.components.MoneyField
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.RangeDatePickerDialog
import app.catatuang.ui.components.ScreenHeader
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.SectionCard
import app.catatuang.ui.components.StatusPill
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 8.6 Gajian: Nominal → (Penyesuaian) → Preview split → Checklist. */
@Composable
fun SalaryScreen(ready: AppState.Ready, vm: LedgerViewModel, onDone: () -> Unit, initialTarget: YearMonth? = null) {
    val c = CatatTheme.colors
    val input = ready.input
    val today = ready.today
    val cats = input.categories
    val closed = ready.ledger.months.values.filter { it.closed }.map { it.month }.toSet()
    val startMonth = YearMonth.from(input.onboarding.startDate)
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(SalaryStep.NOMINAL) }
    var amount by rememberSaveable { mutableLongStateOf(lastSalaryAmount(input.transactions, input.config.salaryTemplate)) }
    // Dari Tutup Buku (6.10 langkah 1): gaji bulan lampau biasanya diterima akhir bulan sebelumnya.
    val initialReceived = initialTarget?.let { maxOf(input.onboarding.startDate, minOf(today, it.minusMonths(1).atEndOfMonth())) } ?: today
    var received by rememberSaveable { mutableStateOf(initialReceived) }
    var target by rememberSaveable { mutableStateOf(initialTarget ?: defaultTargetMonth(today)) }
    var prep by remember { mutableStateOf<SalaryPrep?>(null) }
    var allocation by remember { mutableStateOf<List<AllocationLine>>(emptyList()) }
    var askExtra by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<CatatRepository.SavedSalary?>(null) }
    var feedback by remember { mutableStateOf("") }
    var deposit by remember { mutableLongStateOf(0L) }
    var transferred by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val options = targetOptions(received, startMonth, closed)
    if (target !in options && options.isNotEmpty()) target = options.last()

    fun back() {
        when (step) {
            SalaryStep.NOMINAL, SalaryStep.CHECKLIST -> onDone()
            SalaryStep.ADJUST -> step = if (prep?.plan?.needsAdjustment == true) SalaryStep.NOMINAL else SalaryStep.PREVIEW
            SalaryStep.PREVIEW -> step = if (prep?.plan?.needsAdjustment == true) SalaryStep.ADJUST else SalaryStep.NOMINAL
        }
    }

    fun proceed() {
        if (hasSalary(input, target)) { askExtra = true; return }
        val p = prepareSalary(input, amount, target)
        prep = p
        allocation = p.plan?.proposal ?: p.allocation
        step = if (p.plan?.needsAdjustment == true) SalaryStep.ADJUST else SalaryStep.PREVIEW
    }

    fun finish(checklistMonth: YearMonth?) {
        val s = saved ?: return
        vm.setTransferChecklist(checklistMonth)
        vm.announce(UiEvent(feedback, Tone.SUCCESS, "Gaji tersimpan", undo = { vm.undoSalary(s); vm.setTransferChecklist(null) }))
        onDone()
    }

    fun confirm() {
        val p = prep ?: return
        if (saving) return
        saving = true
        val txs = salaryTransactions(amount, received, target, if (p.onboardingMonth) null else allocation, cats, extra = false)
        val talanganBack = if (target == ready.ledger.currentMonth && ready.ledger.salaryStatus == SalaryStatus.MISSING) ready.ledger.talangan?.amount ?: 0 else 0
        feedback = salaryFeedback(target, salaryActivationDate(received, target), ready.ledger.currentMonth, talanganBack)
        deposit = txs.filter { it.routine }.sumOf { it.amount }
        scope.launch {
            saved = vm.saveSalary(txs, target, if (p.onboardingMonth) null else allocation)
            saving = false
            if (deposit > 0) step = SalaryStep.CHECKLIST else finish(null)
        }
    }

    val stepNo = when (step) {
        SalaryStep.NOMINAL -> 1
        SalaryStep.ADJUST -> 2
        SalaryStep.PREVIEW -> 3
        SalaryStep.CHECKLIST -> 4
    }
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val title = when (step) {
            SalaryStep.NOMINAL -> "Gajian"
            SalaryStep.ADJUST -> "Penyesuaian"
            SalaryStep.PREVIEW -> "Preview split · ${monthName(target, ready.ledger.currentMonth)}"
            SalaryStep.CHECKLIST -> "Checklist"
        }
        ScreenHeader(title, onBack = ::back, subtitle = "Langkah $stepNo dari 4")

        when (step) {
            SalaryStep.NOMINAL -> {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nominal gaji", style = CatatType.cardTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
                            DateChip("Diterima ${dateLabel(received, today).lowercase()}", onClick = { pickDate = true })
                        }
                        BigAmount(amount)
                        Text("Gaji untuk bulan ${monthName(target, ready.ledger.currentMonth)}?", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                        ChoiceChips(options.map { it to monthName(it, ready.ledger.currentMonth) }, target, onSelect = { target = it })
                        if (target > ready.ledger.currentMonth) {
                            Text("Gaji ini jadi Saldo Pending dan baru aktif tanggal 1 ${monthName(target, ready.ledger.currentMonth)}.", style = CatatType.caption, color = c.textSecondary)
                        }
                    }
                }
                Numpad(onDigits = { amount = appendDigits(amount, it) }, onBackspace = { amount = backspace(amount) })
                PrimaryButton("Lanjut", enabled = amount > 0 && options.isNotEmpty(), onClick = ::proceed)
            }

            SalaryStep.ADJUST -> {
                val p = prep ?: return@Column
                val std = kebutuhanStandar(allocation, cats)
                val fits = allocationFits(amount, allocation, cats)
                if (p.plan?.needsAdjustment == true) {
                    NoticeBox("Gaji ${rp(amount)} di bawah kebutuhan standar ${rp(p.plan!!.kebutuhanStandar)}. Usulan: pos tetap tidak disentuh, budget & jatah dipotong proporsional, Nabung dipotong terakhir.", Tone.WARNING)
                }
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        allocation.forEachIndexed { i, line ->
                            val cat = cats.firstOrNull { it.id == line.categoryId } ?: return@forEachIndexed
                            MoneyField(allocationLabel(line, cat), line.value(cat.kind), onChange = { v ->
                                allocation = allocation.toMutableList().also { it[i] = line.withValue(cat.kind, v) }
                            })
                        }
                    }
                }
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AmountRow("Kebutuhan standar (30 hari)", rp(std))
                        AmountRow("Gaji", rp(amount))
                        if (fits) StatusPill("Pas · sisa ${rp(amount - std)} ke Saku Sisa", Tone.SUCCESS)
                        else StatusPill("Kurang ${rp(std - amount)} — kurangi lagi", Tone.DANGER)
                    }
                }
                if (p.plan?.proposal != null) SecondaryButton("Pakai usulan otomatis", onClick = { allocation = p.plan!!.proposal!! })
                PrimaryButton("Lanjut", enabled = fits, onClick = { step = SalaryStep.PREVIEW })
            }

            SalaryStep.PREVIEW -> {
                val p = prep ?: return@Column
                if (p.onboardingMonth) {
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Bulan pertama", style = CatatType.cardTitle, color = c.textPrimary)
                            Text("Alokasi ${monthName(target)} sudah diatur saat mulai pakai app. Gaji ${rp(amount)} ini langsung menambah Saku Sisa ${monthName(target)} penuh.",
                                style = CatatType.bodySmall, color = c.textSecondary)
                        }
                    }
                } else {
                    val plan = planSplit(amount, target, allocation, cats, p.bawaan)
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            plan.lines.forEach { line ->
                                val cat = cats.first { it.id == line.categoryId }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(splitLineText(line, cat), style = CatatType.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                                    if (line.categoryId in p.newCategoryIds) StatusPill("Baru", Tone.INFO)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                            AmountRow("Total alokasi", rp(plan.kebutuhan), strong = true)
                            AmountRow("Gaji", rp(amount))
                            if (plan.bawaan != 0L) AmountRow("Bawaan bulan lalu", rp(plan.bawaan))
                            AmountRow("Saku Sisa awal", rp(plan.sakuSisaAwal), strong = true, color = if (plan.sakuSisaAwal < 0) c.dangerText else c.success)
                        }
                    }
                    if (plan.targetCadangan > 0) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.warningBg)
                                .border(1.5.dp, c.warning, RoundedCornerShape(18.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(LucideIcons.AlertTriangle, contentDescription = null, tint = c.warningText, modifier = Modifier.size(18.dp))
                                Text("Target Cadangan ${rp(plan.targetCadangan)}", style = CatatType.cardTitle, color = c.warningText)
                            }
                            plan.reasons.forEach { Text("• ${reasonText(it, cats)}", style = CatatType.bodySmall, color = c.warningText) }
                            Text("Kumpulkan lewat hemat supaya Sisa bebas tidak minus di akhir bulan.", style = CatatType.caption, color = c.warningText)
                            if (plan.bawaanUncertain) {
                                Text("Bisa berubah setelah Tutup Buku ${monthName(target.minusMonths(1), ready.ledger.currentMonth)}.", style = CatatType.caption, color = c.warningText)
                            }
                        }
                    }
                    SecondaryButton("Ubah alokasi", onClick = { step = SalaryStep.ADJUST })
                }
                PrimaryButton(if (saving) "Menyimpan…" else "Konfirmasi", enabled = !saving, onClick = ::confirm)
            }

            SalaryStep.CHECKLIST -> {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Checkbox) { transferred = !transferred }
                                .semantics { stateDescription = if (transferred) "Sudah" else "Belum" }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (transferred) c.successFill else c.surface)
                                    .border(2.dp, if (transferred) c.successFill else c.divider, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (transferred) Icon(LucideIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                            Text("Transfer ${rp(deposit)} ke rekening tabungan", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                        }
                        Text("Setoran Nabung rutin sudah dicatat otomatis. Checklist ini cuma pengingat supaya uangnya benar-benar dipindah.",
                            style = CatatType.caption, color = c.textSecondary)
                        if (!transferred) Text("Belum dicentang → besok jam 09:00 diingatkan (maks 3 hari).", style = CatatType.caption, color = c.warningText)
                    }
                }
                PrimaryButton("Selesai", onClick = { finish(if (transferred) null else target) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (askExtra) {
        AlertDialog(
            onDismissRequest = { askExtra = false },
            title = { Text("Ini gaji tambahan/rapel?") },
            text = { Text("${monthName(target, ready.ledger.currentMonth)} sudah punya gaji. Kalau ini tambahan/rapel, ${rp(amount)} dicatat sebagai pemasukan ke Saku Sisa ${monthName(target, ready.ledger.currentMonth)}.") },
            confirmButton = {
                TextButton(onClick = {
                    askExtra = false
                    val txs = salaryTransactions(amount, received, target, null, cats, extra = true)
                    scope.launch {
                        val s = vm.saveSalary(txs, target, null)
                        vm.announce(UiEvent("Gaji tambahan +${rp(amount)} · masuk Saku Sisa ${monthName(target, ready.ledger.currentMonth)}", Tone.SUCCESS, "Tersimpan", undo = { vm.undoSalary(s) }))
                        onDone()
                    }
                }) { Text("Ya, tambahan") }
            },
            dismissButton = { TextButton(onClick = { askExtra = false }) { Text("Bukan") } },
        )
    }

    if (pickDate) {
        RangeDatePickerDialog(received, input.onboarding.startDate, today, onPick = {
            received = it
            targetOptions(it, startMonth, closed).let { opts -> target = defaultTargetMonth(it).takeIf { d -> d in opts } ?: opts.lastOrNull() ?: target }
        }, onDismiss = { pickDate = false })
    }
}
