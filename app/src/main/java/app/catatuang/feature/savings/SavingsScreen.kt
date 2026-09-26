package app.catatuang.feature.savings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.engine.LedgerState
import app.catatuang.engine.Pot
import app.catatuang.engine.SalaryStatus
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.WithdrawReason
import app.catatuang.engine.coverShortfall
import app.catatuang.engine.depositTx
import app.catatuang.engine.withdrawTx
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.history.txTitle
import app.catatuang.feature.input.appendDigits
import app.catatuang.feature.input.backspace
import app.catatuang.ui.components.AmountRow
import app.catatuang.ui.components.BigAmount
import app.catatuang.ui.components.ChoiceChips
import app.catatuang.ui.components.HoldToConfirmButton
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.components.ScreenHeader
import app.catatuang.ui.components.SecondaryButton
import app.catatuang.ui.components.SectionCard
import app.catatuang.ui.components.ShadowAmount
import app.catatuang.ui.components.ThinProgress
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.shortDate
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

private val DangerScreen = Color(0xFF3A0F1C)
private val DangerScreenText = Color(0xFFFFE3E8)

fun potName(pot: Pot) = if (pot == Pot.DANA_DARURAT) "Dana Darurat" else "Tabungan"

/** Efek transaksi ke kantong [pot]: + masuk, − keluar, null = bukan transaksi kantong ini. */
fun potDelta(tx: Tx, pot: Pot): Long? {
    val p = tx.pot ?: return null
    if (p != pot) return null
    return when (tx.type) {
        TxType.SAVING_DEPOSIT, TxType.INCOME -> tx.amount
        TxType.SAVING_WITHDRAW -> -tx.amount
        else -> null
    }
}

/** Saldo kantong yang tersedia untuk diambil manual. */
fun potBalance(l: LedgerState, pot: Pot) = if (pot == Pot.TABUNGAN) l.tabungan else l.danaDarurat

/** 8.8 Tabungan & Dana Darurat. */
@Composable
fun SavingsScreen(ready: AppState.Ready, vm: LedgerViewModel, onBack: () -> Unit) {
    val c = CatatTheme.colors
    val l = ready.ledger
    val target = ready.input.config.emergencyTarget
    var depositPot by remember { mutableStateOf<Pot?>(null) }
    var withdrawPot by remember { mutableStateOf<Pot?>(null) }
    var coverNow by remember { mutableStateOf(false) }
    var historyPot by rememberSaveable { mutableStateOf(Pot.TABUNGAN) }
    val talangan = l.talangan

    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Tabungan & Dana Darurat", onBack = onBack)

        PotCard(
            title = "Tabungan",
            amount = l.tabunganShown,
            shadow = talangan?.fromTabungan ?: 0,
            tone = Tone.SAVINGS,
            extra = null,
            onDeposit = { depositPot = Pot.TABUNGAN },
            onWithdraw = { withdrawPot = Pot.TABUNGAN },
        )
        PotCard(
            title = "Dana Darurat",
            amount = l.danaDaruratShown,
            shadow = talangan?.fromDanaDarurat ?: 0,
            tone = Tone.EMERGENCY,
            extra = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThinProgress(if (target > 0) l.danaDarurat.toFloat() / target else 1f, c.emergency)
                    Text(
                        if (l.danaDarurat >= target) "Target ${rp(target)} tercapai" else "Target ${rp(target)} · kurang ${rp(target - l.danaDarurat)}",
                        style = CatatType.caption, color = c.emergency,
                    )
                }
            },
            onDeposit = { depositPot = Pot.DANA_DARURAT },
            onWithdraw = { withdrawPot = Pot.DANA_DARURAT },
        )

        if (l.salaryStatus != SalaryStatus.MISSING && l.sakuSisa < 0) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoticeBox("Saku Sisa minus ${rp(-l.sakuSisa)} — akhir bulan ditutup dari Tabungan, lalu Dana Darurat.", Tone.DANGER)
                    SecondaryButton("Tutup sekarang", onClick = { coverNow = true })
                }
            }
        }

        Text("Riwayat", style = CatatType.cardTitle.copy(fontSize = 16.sp), color = c.textPrimary)
        ChoiceChips(listOf(Pot.TABUNGAN to "Tabungan", Pot.DANA_DARURAT to "Dana Darurat"), historyPot, onSelect = { historyPot = it })
        val items = ready.input.transactions.filter { !it.date.isAfter(ready.today) && potDelta(it, historyPot) != null }.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.createdAt })
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (items.isEmpty()) Text("Belum ada catatan.", style = CatatType.bodySmall, color = c.textSecondary)
                items.forEach { tx ->
                    val delta = potDelta(tx, historyPot)!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(txTitle(tx, ready.input.categories), style = CatatType.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                            Text(shortDate(tx.date) + (tx.note?.let { " · $it" } ?: ""), style = CatatType.caption, color = c.textSecondary)
                        }
                        Text(
                            (if (delta >= 0) "+" else "") + rp(delta),
                            style = CatatType.money.copy(fontSize = 14.sp),
                            color = if (delta >= 0) c.success else c.dangerText,
                        )
                    }
                }
            }
        }
    }

    depositPot?.let { pot -> DepositSheet(pot, ready, vm, onDismiss = { depositPot = null }) }
    withdrawPot?.let { pot -> WithdrawSheet(pot, ready, vm, onDismiss = { withdrawPot = null }) }
    if (coverNow) CoverNowSheet(ready, vm, onDismiss = { coverNow = false })
}

@Composable
private fun PotCard(
    title: String,
    amount: Long,
    shadow: Long,
    tone: Tone,
    extra: (@Composable () -> Unit)?,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val c = CatatTheme.colors
    val (fg, bg) = if (tone == Tone.SAVINGS) c.savings to c.savingsBg else c.emergency to c.emergencyBg
    // Isi tombol memakai warna tema terang: teks putih tetap kontras di tema gelap.
    val fill = if (tone == Tone.SAVINGS) app.catatuang.ui.theme.LightCatatColors.savings else app.catatuang.ui.theme.LightCatatColors.emergency
    Column(
        Modifier.fillMaxWidth().clip(CatatShapes.card).background(bg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = fg)
        Text(rp(amount), style = CatatType.heroAmount.copy(fontSize = 30.sp), color = fg)
        if (shadow > 0) ShadowAmount(shadow, fg)
        extra?.invoke()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PotButton("Setor", fill, Modifier.weight(1f), onDeposit)
            PotButton("Ambil", fill, Modifier.weight(1f), onWithdraw)
        }
    }
}

@Composable
private fun PotButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) { PrimaryButton(text, onClick = onClick, color = color) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepositSheet(pot: Pot, ready: AppState.Ready, vm: LedgerViewModel, onDismiss: () -> Unit) {
    val c = CatatTheme.colors
    var amount by remember { mutableLongStateOf(0L) }
    val saku = ready.ledger.sakuSisa
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = CatatShapes.sheet, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Setor ke ${potName(pot)}", style = CatatType.cardTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = c.textPrimary)
            Text("Diambil dari Saku Sisa. Pindahkan juga uangnya ke rekening ${potName(pot).lowercase()}.", style = CatatType.bodySmall, color = c.textSecondary)
            BigAmount(amount)
            if (amount > 0) {
                val after = saku - amount
                NoticeBox("Saku Sisa jadi ${rp(after)}", if (after < 0) Tone.DANGER else Tone.SUCCESS)
            }
            Numpad(onDigits = { amount = appendDigits(amount, it) }, onBackspace = { amount = backspace(amount) })
            PrimaryButton(if (amount > 0) "Setor · ${rp(amount)}" else "Setor", enabled = amount > 0, onClick = {
                vm.saveAll(listOf(depositTx(pot, amount, ready.today)), "Setor ${potName(pot)} +${rp(amount)}", Tone.SUCCESS)
                onDismiss()
            })
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** R-56: ambil kantong wajib pilih alasan; tahan 3 detik; layar merah untuk DARURAT. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawSheet(pot: Pot, ready: AppState.Ready, vm: LedgerViewModel, onDismiss: () -> Unit) {
    val c = CatatTheme.colors
    var amount by remember { mutableLongStateOf(0L) }
    var reason by remember { mutableStateOf<WithdrawReason?>(null) }
    val balance = potBalance(ready.ledger, pot)
    val red = reason == WithdrawReason.DARURAT
    val text = if (red) DangerScreenText else c.textPrimary
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = CatatShapes.sheet,
        containerColor = if (red) DangerScreen else c.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ambil dari ${potName(pot)}", style = CatatType.cardTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = text)
            Text("Saldo ${rp(balance)} · uangnya masuk ke Saku Sisa", style = CatatType.bodySmall, color = text.copy(alpha = 0.8f))
            Text("Alasan (wajib)", style = CatatType.bodySmall.copy(fontWeight = FontWeight.Bold), color = text)
            ChoiceChips(listOf(WithdrawReason.RENCANA to "Rencana", WithdrawReason.DARURAT to "Darurat"), reason, onSelect = { reason = it })
            when (reason) {
                WithdrawReason.RENCANA -> NoticeBox("Rencana: tidak dihitung boncos, tetap tampil di laporan.", Tone.INFO)
                WithdrawReason.DARURAT -> NoticeBox("Darurat: bulan ini tercatat BONCOS.", Tone.DANGER)
                else -> Unit
            }
            if (pot == Pot.DANA_DARURAT) NoticeBox("Ini dana darurat terakhir lu. Pakai Tabungan dulu kalau masih ada.", Tone.DANGER)
            BigAmount(amount, color = text)
            if (amount > balance) NoticeBox("Melebihi saldo ${potName(pot)} ${rp(balance)}.", Tone.WARNING)
            Numpad(onDigits = { amount = appendDigits(amount, it) }, onBackspace = { amount = backspace(amount) })
            val valid = amount > 0 && amount <= balance && reason != null
            HoldToConfirmButton(
                if (valid) "Tahan 3 detik · ambil ${rp(amount)}" else "Isi nominal & alasan",
                enabled = valid,
                danger = red || pot == Pot.DANA_DARURAT,
                onConfirmed = {
                    val r = reason ?: return@HoldToConfirmButton
                    vm.saveAll(listOf(withdrawTx(pot, amount, r, ready.today)), "Ambil ${potName(pot)} ${rp(amount)} → Saku Sisa", if (r == WithdrawReason.DARURAT) Tone.DANGER else Tone.WARNING)
                    onDismiss()
                },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** R-52 "Tutup sekarang": urutan penutup, tahan 3 detik, layar merah. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverNowSheet(ready: AppState.Ready, vm: LedgerViewModel, onDismiss: () -> Unit) {
    val l = ready.ledger
    val cover = coverShortfall(-l.sakuSisa, 0, l.tabungan, l.danaDarurat)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = CatatShapes.sheet, containerColor = DangerScreen) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tutup Saku Sisa minus", style = CatatType.cardTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = DangerScreenText)
            Text(rp(-l.sakuSisa), style = CatatType.inputAmount.copy(fontSize = 40.sp), color = DangerScreenText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (cover.fromTabungan > 0) AmountRow("Dari Tabungan", rp(cover.fromTabungan), strong = true, color = DangerScreenText)
            if (cover.fromDanaDarurat > 0) AmountRow("Dari Dana Darurat", rp(cover.fromDanaDarurat), strong = true, color = DangerScreenText)
            if (cover.fromDanaDarurat > 0) NoticeBox("Ini dana darurat terakhir lu.", Tone.DANGER)
            if (cover.uncovered > 0) NoticeBox("Semua kantong tidak cukup; masih kurang ${rp(cover.uncovered)}.", Tone.WARNING)
            NoticeBox("Tercatat ambil kantong alasan DARURAT — bulan ini BONCOS.", Tone.DANGER)
            HoldToConfirmButton("Tahan 3 detik · tutup ${rp(cover.fromPots)}", enabled = cover.fromPots > 0, onConfirmed = { vm.coverNow(); onDismiss() })
            Spacer(Modifier.height(4.dp))
        }
    }
}
