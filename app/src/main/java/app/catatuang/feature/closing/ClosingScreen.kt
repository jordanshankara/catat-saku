package app.catatuang.feature.closing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.engine.ClosingOption
import app.catatuang.engine.MonthSummary
import app.catatuang.engine.SplitTargets
import app.catatuang.engine.TxType
import app.catatuang.engine.closingDebts
import app.catatuang.engine.closingDistribution
import app.catatuang.engine.closingFixedPaymentTx
import app.catatuang.engine.closingOptions
import app.catatuang.engine.closureSnapshot
import app.catatuang.engine.coverShortfall
import app.catatuang.engine.debtPayoffTx
import app.catatuang.engine.reconcileTx
import app.catatuang.engine.reconciledDiff
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.ui.components.AmountRow
import app.catatuang.ui.components.ChoiceChips
import app.catatuang.ui.components.HoldToConfirmButton
import app.catatuang.ui.components.MoneyField
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.ScreenHeader
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.SectionCard
import app.catatuang.ui.components.StatusPill
import app.catatuang.ui.components.Tone
import app.catatuang.ui.components.toneColors
import app.catatuang.ui.format.dayMonth
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth

private val DangerScreen = Color(0xFF3A0F1C)
private val DangerScreenText = Color(0xFFFFE3E8)

/** 8.7 Tutup Buku: stepper 6.10, satu keputusan utama per layar. */
@Composable
fun ClosingScreen(
    ready: AppState.Ready,
    vm: LedgerViewModel,
    month: YearMonth,
    onRecordLastDay: (LocalDate) -> Unit,
    onSalary: (YearMonth) -> Unit,
    onDone: () -> Unit,
) {
    val c = CatatTheme.colors
    val m = ready.ledger.months[month] ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val steps = closingSteps(m)
    var step by rememberSaveable { mutableStateOf(ClosingStep.CHECK) }
    var reconciledActual by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    val name = monthName(month, ready.ledger.currentMonth)

    fun next() {
        val i = steps.indexOf(step)
        if (i in 0 until steps.lastIndex) step = steps[i + 1]
    }
    fun back() {
        val i = steps.indexOf(step)
        if (i <= 0 || step == ClosingStep.BACKUP || step == ClosingStep.DONE) onDone() else step = steps[i - 1]
    }
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch { try { block() } finally { busy = false } }
    }

    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val no = steps.indexOf(step) + 1
        ScreenHeader("Tutup Buku $name", onBack = ::back, subtitle = "Langkah $no dari ${steps.size} · ${step.label}")

        when (step) {
            ClosingStep.CHECK -> {
                val last = month.atEndOfMonth()
                Text("Ada catatan kemarin yang belum masuk?", style = CatatType.cardTitle.copy(fontSize = 17.sp), color = c.textPrimary)
                Text("Catat dulu pengeluaran $name yang kelewat sebelum bulan ini dikunci.", style = CatatType.bodySmall, color = c.textSecondary)
                SecondaryButton("Catat pengeluaran ${dayMonth(last)}", onClick = { onRecordLastDay(last) })
                if (!m.lastWindowClosed) {
                    NoticeBox("Menunggu akhir pekan selesai (Senin). Transport akhir pekan terakhir masih dihitung ke $name.", Tone.WARNING)
                }
                PrimaryButton("Tidak ada, lanjut", enabled = m.closable, onClick = ::next)
            }

            ClosingStep.SALARY -> {
                if (m.salary != null) {
                    NoticeBox("Gaji $name sudah tercatat ${rp(m.salary!!)}.", Tone.SUCCESS)
                    PrimaryButton("Lanjut", onClick = ::next)
                } else {
                    Text("$name belum punya gaji.", style = CatatType.cardTitle.copy(fontSize = 17.sp), color = c.textPrimary)
                    Text("Gajinya masuk tanggal berapa? Kalau memang tidak ada gaji, pilih \"Bulan ini tanpa gaji\".", style = CatatType.bodySmall, color = c.textSecondary)
                    if (m.noSalary) {
                        NoticeBox("Ditandai tanpa gaji: tidak ada Nabung rutin, hutang harian di-nol-kan, minus ditutup dari kantong (alasan tanpa gaji, bukan boncos).", Tone.WARNING)
                        PrimaryButton("Lanjut", onClick = ::next)
                        SecondaryButton("Batalkan tanpa gaji", onClick = { act { vm.setNoSalary(month, false) } })
                    } else {
                        PrimaryButton("Gajinya sudah masuk — catat", onClick = { onSalary(month) })
                        SecondaryButton("Bulan ini tanpa gaji", onClick = { act { vm.setNoSalary(month, true) } })
                    }
                }
            }

            ClosingStep.VERDICT -> {
                VerdictCard(m, reconciledDiff(ready.input, month), provisional = true)
                PrimaryButton("Lanjut", onClick = ::next)
            }

            ClosingStep.RECONCILE -> {
                val prev = ready.input.transactions.filter { it.closingOf == month && (it.type == TxType.UNRECORDED || it.type == TxType.SURPLUS_FOUND) }
                val prevDiff = reconciledDiff(ready.input, month) ?: 0
                val computed = ready.ledger.uangPegangan - prevDiff
                var actual by remember { mutableLongStateOf(reconciledActual ?: 0L) }
                Text("Uang pegangan lu sekarang berapa?", style = CatatType.cardTitle.copy(fontSize = 17.sp), color = c.textPrimary)
                Text("Dompet + rekening + e-wallet, di luar tabungan & dana darurat.", style = CatatType.bodySmall, color = c.textSecondary)
                SectionCard { AmountRow("Menurut catatan", rp(computed), strong = true) }
                MoneyField("Uang pegangan sebenarnya", actual, onChange = { actual = it })
                if (reconciledActual != null) {
                    val d = actual - computed
                    NoticeBox(
                        when {
                            d < 0 -> "Selisih kurang ${rp(-d)} → dicatat \"Tidak tercatat\" (Saku Sisa $name −${rp(-d)})."
                            d > 0 -> "Selisih lebih ${rp(d)} → dicatat \"Selisih lebih\" (Saku Sisa $name +${rp(d)})."
                            else -> "Pas. Tidak ada selisih."
                        },
                        if (d == 0L) Tone.SUCCESS else Tone.WARNING,
                    )
                    VerdictCard(m, reconciledDiff(ready.input, month), provisional = true)
                    PrimaryButton("Lanjut", onClick = ::next)
                    SecondaryButton("Cocokkan ulang", enabled = !busy, onClick = {
                        act { vm.replaceTransactions(prev.map { it.id }, listOfNotNull(reconcileTx(month, computed, actual, ready.today))); reconciledActual = actual }
                    })
                } else {
                    PrimaryButton("Cocokkan", enabled = !busy, onClick = {
                        act { vm.replaceTransactions(prev.map { it.id }, listOfNotNull(reconcileTx(month, computed, actual, ready.today))); reconciledActual = actual }
                    })
                    SecondaryButton("Lewati", onClick = ::next)
                }
            }

            ClosingStep.DEBT -> {
                val debts = closingDebts(ready.input, ready.ledger, month)
                if (debts.isEmpty()) {
                    NoticeBox(if (m.hutangDibawa > 0) "Hutang ${rp(m.hutangDibawa)} dibawa ke bulan baru." else "Tidak ada hutang harian tersisa.", Tone.SUCCESS)
                } else {
                    Text("Hutang harian tersisa", style = CatatType.cardTitle.copy(fontSize = 17.sp), color = c.textPrimary)
                    Text("Default: dibawa ke bulan baru dan dilunasi hemat hari berikutnya.", style = CatatType.bodySmall, color = c.textSecondary)
                    debts.forEach { (id, amount) ->
                        val cat = ready.input.categories.first { it.id == id }
                        SectionCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AmountRow("Hutang ${cat.name.lowercase()}", rp(amount), strong = true)
                                SecondaryButton("Lunasi pakai Saku Sisa (${rp(m.sakuSisa)})", enabled = !busy, onClick = {
                                    act { vm.addAll(listOf(debtPayoffTx(month, id, amount, ready.today))) }
                                })
                            }
                        }
                    }
                }
                PrimaryButton(if (debts.isEmpty()) "Lanjut" else "Bawa sisanya ke bulan baru", onClick = ::next)
            }

            ClosingStep.FIXED -> {
                val unpaid = m.fixed.filter { !it.isPaid && !it.cancelled }
                if (unpaid.isEmpty()) NoticeBox("Semua tagihan tetap $name beres.", Tone.SUCCESS)
                val amounts = remember { mutableStateMapOf<Long, Long>() }
                unpaid.forEach { f ->
                    val cat = ready.input.categories.first { it.id == f.categoryId }
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${cat.name} · estimasi ${rp(f.estimate)}", style = CatatType.body.copy(fontWeight = FontWeight.Bold), color = c.textPrimary, modifier = Modifier.weight(1f))
                                StatusPill("Belum bayar", Tone.WARNING)
                            }
                            MoneyField("Sudah dibayar berapa?", amounts[f.categoryId] ?: f.estimate, onChange = { amounts[f.categoryId] = it })
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryButton("Sudah dibayar", modifier = Modifier.weight(1f), enabled = !busy && (amounts[f.categoryId] ?: f.estimate) > 0, onClick = {
                                    val paid = amounts[f.categoryId] ?: f.estimate
                                    act { vm.payFixedNow(month, closingFixedPaymentTx(month, f.categoryId, paid, ready.today), f.estimate) }
                                })
                                SecondaryButton("Tidak jadi", modifier = Modifier.weight(1f), enabled = !busy, onClick = {
                                    act { vm.setFixedCancelled(month, f.categoryId, f.estimate, true) }
                                })
                            }
                        }
                    }
                }
                PrimaryButton("Lanjut", enabled = unpaid.isEmpty(), onClick = ::next)
            }

            ClosingStep.DISTRIBUTE -> Distribute(ready, vm, m, busy, act = ::act, onClosed = {
                act {
                    val latest = m
                    val snapshot = closureSnapshot(latest, reconciledDiff(ready.input, month))
                    vm.closeMonth(month, latest.verdict.kind.name, latest.verdict.amount, Json.encodeToString(app.catatuang.engine.ClosureSnapshot.serializer(), snapshot), reconciledActual)
                    step = ClosingStep.BACKUP
                }
            })

            ClosingStep.BACKUP -> {
                NoticeBox("$name sudah ditutup dan terkunci.", Tone.SUCCESS)
                Text("Kirim backup keluar HP", style = CatatType.cardTitle.copy(fontSize = 17.sp), color = c.textPrimary)
                Text("Simpan file backup ke Google Drive, email, atau chat sendiri. File tidak terenkripsi, jadi simpan di tempat aman.", style = CatatType.bodySmall, color = c.textSecondary)
                PrimaryButton("Kirim backup", onClick = { vm.shareBackup(context) })
                SecondaryButton("Lanjut", onClick = ::next)
            }

            ClosingStep.DONE -> {
                VerdictCard(m, reconciledDiff(ready.input, month), provisional = false)
                PrimaryButton("Selesai", onClick = onDone)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Langkah 6: Saku Sisa akhir minus wajib ditutup (R-72); positif dipilih tujuannya. */
@Composable
private fun Distribute(ready: AppState.Ready, vm: LedgerViewModel, m: MonthSummary, busy: Boolean, act: (suspend () -> Unit) -> Unit, onClosed: () -> Unit) {
    val c = CatatTheme.colors
    val l = ready.ledger
    val saku = m.sakuSisa
    val month = m.month
    val name = monthName(month, l.currentMonth)
    when {
        saku < 0 -> {
            val cover = coverShortfall(-saku, 0, l.tabungan, l.danaDarurat)
            Column(
                Modifier.fillMaxWidth().clip(CatatShapes.card).background(DangerScreen).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Saku Sisa $name minus", style = CatatType.cardTitle.copy(fontSize = 17.sp), color = DangerScreenText)
                Text(rp(saku), style = CatatType.inputAmount.copy(fontSize = 38.sp), color = DangerScreenText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("Wajib ditutup: Tabungan dulu, lalu Dana Darurat.", style = CatatType.bodySmall, color = DangerScreenText)
                if (cover.fromTabungan > 0) AmountRow("Dari Tabungan", rp(cover.fromTabungan), strong = true, color = DangerScreenText)
                if (cover.fromDanaDarurat > 0) AmountRow("Dari Dana Darurat", rp(cover.fromDanaDarurat), strong = true, color = DangerScreenText)
                if (cover.fromDanaDarurat > 0) NoticeBox("Ini dana darurat terakhir lu.", Tone.DANGER)
                NoticeBox(if (m.noSalary) "Bulan tanpa gaji: tercatat alasan tanpa gaji (bukan boncos)." else "Tercatat ambil kantong alasan DARURAT — verdict BONCOS.", Tone.DANGER)
                if (cover.uncovered > 0) NoticeBox("Semua kantong tidak cukup; kurang ${rp(cover.uncovered)} tetap tercatat minus.", Tone.WARNING)
                HoldToConfirmButton("Tahan 3 detik · tutup ${rp(cover.fromPots)}", enabled = cover.fromPots > 0 && !busy, onConfirmed = {
                    act { vm.addAll(closingDistribution(month, saku, ClosingOption.TUTUP_WAJIB, ready.today, l.tabungan, l.danaDarurat, m.noSalary)) }
                })
            }
            if (cover.fromPots == 0L) PrimaryButton("Tutup buku $name", enabled = !busy, onClick = onClosed)
        }
        saku == 0L -> {
            NoticeBox("Saku Sisa $name sudah Rp 0. Siap ditutup.", Tone.SUCCESS)
            VerdictCard(m, reconciledDiff(ready.input, month), provisional = true)
            PrimaryButton("Tutup buku $name", enabled = !busy, onClick = onClosed)
        }
        else -> {
            val options = closingOptions(saku, l.danaDarurat, ready.input.config.emergencyTarget)
            var option by remember(saku) { mutableStateOf(options.first()) }
            var tab by remember(saku) { mutableLongStateOf(saku) }
            var dd by remember(saku) { mutableLongStateOf(0L) }
            var bawa by remember(saku) { mutableLongStateOf(0L) }
            Text("Saku Sisa akhir ${rp(saku)}", style = CatatType.cardTitle.copy(fontSize = 17.sp), color = c.textPrimary)
            Text("Mau dikemanakan?", style = CatatType.bodySmall, color = c.textSecondary)
            ChoiceChips(options.map { it to optionLabel(it) }, option, onSelect = { option = it })
            if (option == ClosingOption.ISI_DANA_DARURAT && l.danaDarurat < ready.input.config.emergencyTarget) {
                NoticeBox("Dana Darurat ${rp(l.danaDarurat)} dari target ${rp(ready.input.config.emergencyTarget)}.", Tone.EMERGENCY)
            }
            if (option == ClosingOption.SPLIT) {
                MoneyField("Ke Tabungan", tab, onChange = { tab = it })
                MoneyField("Ke Dana Darurat", dd, onChange = { dd = it })
                MoneyField("Bawa ke bulan depan", bawa, onChange = { bawa = it })
                val left = saku - tab - dd - bawa
                StatusPill(if (left == 0L) "Pas ${rp(saku)}" else if (left > 0) "Sisa ${rp(left)} belum dibagi" else "Lebih ${rp(-left)}", if (left == 0L) Tone.SUCCESS else Tone.WARNING)
            }
            val split = SplitTargets(tab, dd, bawa)
            PrimaryButton("Simpan", enabled = !busy && (option != ClosingOption.SPLIT || split.total == saku), onClick = {
                act {
                    vm.addAll(closingDistribution(month, saku, option, ready.today, l.tabungan, l.danaDarurat, m.noSalary, split.takeIf { option == ClosingOption.SPLIT }))
                }
            })
        }
    }
}

private fun optionLabel(o: ClosingOption) = when (o) {
    ClosingOption.ISI_DANA_DARURAT -> "Isi Dana Darurat"
    ClosingOption.SEMUA_KE_TABUNGAN -> "Semua ke Tabungan"
    ClosingOption.BAWA_KE_BULAN_DEPAN -> "Bawa ke bulan depan"
    ClosingOption.SPLIT -> "Split"
    ClosingOption.TUTUP_WAJIB -> "Tutup dari kantong"
}

/** Kartu verdict besar (R-80/R-81). */
@Composable
fun VerdictCard(m: MonthSummary, reconciled: Long?, provisional: Boolean) {
    val c = CatatTheme.colors
    val tone = verdictTone(m.verdict)
    val t = toneColors(tone)
    Column(
        Modifier.fillMaxWidth().clip(CatatShapes.card).background(c.surface).border(2.dp, t.content.copy(alpha = 0.5f), CatatShapes.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Verdict ${monthName(m.month)}", style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = c.textSecondary, modifier = Modifier.weight(1f))
            StatusPill(if (provisional) "Sementara" else "Final", if (provisional) Tone.NEUTRAL else tone)
        }
        Text(verdictTitle(m.verdict), style = CatatType.heroAmount.copy(fontSize = 28.sp), color = t.content)
        Spacer(Modifier.height(2.dp))
        verdictLines(m, reconciled).forEach { (label, value) -> AmountRow(label, value) }
    }
}
