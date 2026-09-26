package app.catatuang

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.catatuang.engine.Defaults
import app.catatuang.engine.Pot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.WithdrawReason
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.income.IncomeContent
import app.catatuang.feature.input.InputContent
import app.catatuang.feature.salary.SalaryScreen
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatUangTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Fase 3: Gajian, talangan, Pemasukan, Tabungan & Dana Darurat, Pos TETAP, Pakai Tabungan. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xxhdpi")
class Phase3UiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var env: TestEnv

    private fun start(today: LocalDate): TestEnv {
        env = TestEnv(ApplicationProvider.getApplicationContext(), today)
        return env
    }

    @After fun tearDown() {
        if (::env.isInitialized) env.close()
    }

    private fun show() {
        env.lock.markUnlocked()
        env.skipAutoClosing()
        rule.setContent { CatatUangTheme { CatatRoot(env.repo, env.settings, env.lock, env.clock) } }
    }

    private fun click(text: String, substring: Boolean = false) {
        rule.waitText(text, substring)
        val node = rule.onAllNodes(hasText(text, substring = substring))[0]
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** Tunggu kondisi data; looper utama tetap diputar supaya coroutine ViewModel jalan. */
    private fun awaitData(cond: () -> Boolean) {
        repeat(50) {
            rule.waitForIdle()
            if (cond()) return
            Thread.sleep(200)
        }
        throw AssertionError("Kondisi data tidak tercapai")
    }

    private fun holdToConfirm(description: String) {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription(description, substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(3_200)
        // Setelah 3 detik aksi sudah jalan dan sheet/dialog biasanya sudah tertutup.
        runCatching { rule.onNodeWithContentDescription(description, substring = true).performTouchInput { up() } }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    /** 8.6 gaji cepat 29 Sep: preview Oktober (Target 180rb, "bisa berubah"), checklist, pending. */
    @Test fun salaryEarly() {
        start(LocalDate.of(2026, 9, 29)).onboard(LocalDate.of(2026, 9, 25), 1_000_000)
        show()
        click("Gajian sudah masuk?")
        rule.waitText("Nominal gaji")
        rule.onNodeWithText("3.300.000").assertExists()
        rule.shot("f3-gaji-1-nominal")
        click("Lanjut")
        rule.waitText("Target Cadangan Rp 180.000")
        rule.onNodeWithText("Makan · 50.000 × 31 hari = 1.550.000").assertExists()
        rule.onNodeWithText("Bisa berubah setelah Tutup Buku September.").assertExists()
        rule.shot("f3-gaji-3-preview")
        click("Konfirmasi")
        rule.waitText("Transfer Rp 160.000 ke rekening tabungan")
        rule.shot("f3-gaji-4-checklist")
        click("Selesai")
        rule.waitText("Gaji Oktober tersimpan · aktif 1 Okt")
        rule.waitText("Gaji Oktober aman · aktif tgl 1")
        rule.shot("f3-beranda-pending")

        val snap = runBlocking { env.repo.snapshot.first() }
        assertEquals(listOf(TxType.SALARY, TxType.SAVING_DEPOSIT), snap.transactions.map { TxType.valueOf(it.type) })
        assertEquals("2026-10-01", snap.transactions[1].date)
        assertEquals(8, snap.allocations.count { it.yearMonth == "2026-10" })
        assertEquals("2026-10", runBlocking { env.repo.transferChecklist.first() }.first)
        assertEquals(3_300_000, env.ready().ledger.saldoPending)
    }

    /** R-04 + R-06: gaji telat 3.280.000 → Penyesuaian → talangan dikembalikan. */
    @Test fun salaryLateWithAdjustment() {
        start(LocalDate.of(2026, 10, 3)).onboard(LocalDate.of(2026, 9, 25), 1_000_000)
        env.add(Tx(0, LocalDate.of(2026, 10, 1), TxType.EXPENSE, 45_000, categoryId = Defaults.ID_MAKAN))
        show()
        rule.waitText("Ditalangi Rp 45.000")
        rule.shot("f3-beranda-talangan")
        click("Gaji sudah masuk")
        rule.waitText("Nominal gaji")
        repeat(7) { rule.onNodeWithContentDescription("Hapus angka").performClick() }
        listOf("3", "2", "8", "0", "000").forEach { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithText("3.280.000").assertExists()
        rule.onAllNodesWithText("Oktober")[0].performClick()
        click("Lanjut")
        rule.waitText("Penyesuaian")
        rule.waitText("Pas · sisa", substring = true)
        rule.shot("f3-gaji-2-penyesuaian")
        click("Lanjut")
        rule.waitText("Konfirmasi")
        click("Konfirmasi")
        rule.waitText("Transfer Rp 160.000 ke rekening tabungan")
        click("Transfer Rp 160.000 ke rekening tabungan")
        click("Selesai")
        rule.waitText("Talangan Rp 45.000 dikembalikan · Gaji Oktober tersimpan")

        val ready = env.ready()
        val alloc = ready.ledger.current!!.allocation.associateBy { it.categoryId }
        assertEquals(120_000, alloc.getValue(Defaults.ID_IURAN_MESS).monthlyAmount)
        assertEquals(390_000, alloc.getValue(Defaults.ID_AI).monthlyAmount)
        assertEquals(160_000, alloc.getValue(Defaults.ID_NABUNG).monthlyAmount)
        assertTrue(app.catatuang.engine.kebutuhanStandar(ready.ledger.current!!.allocation, ready.input.categories) <= 3_280_000)
        assertEquals(null, ready.ledger.talangan)
        assertEquals(null, runBlocking { env.repo.transferChecklist.first() }.first)
    }

    /** R-05: bulan target sudah punya gaji → "Ini gaji tambahan/rapel?" → Saku Sisa + penuh, tanpa Nabung. */
    @Test fun salaryDouble() {
        start(LocalDate.of(2026, 11, 10)).onboard(LocalDate.of(2026, 10, 31), 0)
        env.salary(LocalDate.of(2026, 10, 31), YearMonth.of(2026, 11))
        val ready = env.ready()
        val vm = LedgerViewModel(env.repo)
        rule.setContent { CatatUangTheme { SalaryScreen(ready, vm, onDone = {}) } }
        repeat(7) { rule.onNodeWithContentDescription("Hapus angka").performClick() }
        listOf("5", "000", "000").forEach { rule.onNodeWithText(it).performClick() }
        click("Lanjut")
        rule.waitText("Ini gaji tambahan/rapel?")
        click("Ya, tambahan")
        awaitData { env.ready().input.transactions.count { it.type == TxType.SALARY } == 2 }
        val after = env.ready().ledger
        assertEquals(ready.ledger.sakuSisa + 5_000_000, after.sakuSisa)
        assertEquals(ready.ledger.tabungan, after.tabungan)
    }

    /** 8.5 Pemasukan: THR default ke Tabungan (T-23 lewat UI). */
    @Test fun incomeThr() {
        start(LocalDate.of(2026, 11, 5)).onboard(LocalDate.of(2026, 10, 31), 0)
        env.salary(LocalDate.of(2026, 10, 31), YearMonth.of(2026, 11))
        val vm = LedgerViewModel(env.repo)
        val ready = env.ready()
        rule.setContent { CatatUangTheme { Surface(color = CatatTheme.colors.surface) { IncomeContent(ready, vm, onSaved = {}) } } }
        rule.onNodeWithText("THR / Bonus").performClick()
        listOf("3", "000", "000").forEach { rule.onNodeWithText(it).performClick() }
        rule.waitText("Simpan · Rp 3.000.000")
        rule.shot("f3-pemasukan")
        rule.onNodeWithText("Simpan · Rp 3.000.000").performScrollTo().performClick()
        awaitData { env.ready().input.transactions.any { it.type == TxType.INCOME } }
        val after = env.ready().ledger
        assertEquals(ready.ledger.tabungan + 3_000_000, after.tabungan)
        assertEquals(ready.ledger.sakuSisa, after.sakuSisa)
    }

    /** R-40/R-41: kewajiban dibuat otomatis, bayar AI dari Beranda → LUNAS. */
    @Test fun fixedPayment() {
        start(LocalDate.of(2026, 11, 2)).onboard(LocalDate.of(2026, 10, 31), 0)
        env.salary(LocalDate.of(2026, 10, 31), YearMonth.of(2026, 11))
        awaitData { runBlocking { env.repo.snapshot.first() }.fixedObligations.count { it.yearMonth == "2026-11" } == 2 }
        show()
        rule.waitText("Tagihan tetap bulan ini")
        rule.onNodeWithText("Tagihan tetap bulan ini").performScrollTo()
        rule.shot("f3-beranda-tagihan")
        rule.onAllNodesWithText("Bayar")[1].performScrollTo().performClick()
        rule.waitText("AI jatuh tempo. Bayar berapa?")
        rule.shot("f3-bayar-ai", containing = "Bayar berapa")
        click("Bayar · Rp 390.000")
        awaitData { runBlocking { env.repo.snapshot.first() }.fixedObligations.any { it.categoryId == Defaults.ID_AI && it.status == "PAID" } }
        assertTrue(env.ready().ledger.current!!.fixed.first { it.categoryId == Defaults.ID_AI }.isPaid)
    }

    /** 8.8 Tabungan & Dana Darurat: layar, ambil RENCANA dengan tahan 3 detik. */
    @Test fun savingsWithdraw() {
        start(LocalDate.of(2026, 11, 5)).onboard(LocalDate.of(2026, 10, 31), 0)
        env.salary(LocalDate.of(2026, 10, 31), YearMonth.of(2026, 11))
        show()
        rule.waitText("Halo, Ko")
        click("Tabungan")
        rule.waitText("Tabungan & Dana Darurat")
        rule.shot("f3-tabungan")
        rule.onAllNodesWithText("Ambil")[0].performClick()
        rule.waitText("Ambil dari Tabungan")
        click("Rencana")
        listOf("5", "0", "000").forEach { rule.onAllNodesWithText(it).let { n -> n[n.fetchSemanticsNodes().lastIndex] }.performClick() }
        rule.waitText("Tahan 3 detik · ambil Rp 50.000")
        rule.shot("f3-ambil-tabungan", containing = "Ambil dari Tabungan")
        holdToConfirm("Tahan 3 detik · ambil Rp 50.000")
        awaitData { env.ready().input.transactions.any { it.type == TxType.SAVING_WITHDRAW } }
        val w = env.ready().input.transactions.first { it.type == TxType.SAVING_WITHDRAW }
        assertEquals(Pot.TABUNGAN, w.pot)
        assertEquals(WithdrawReason.RENCANA, w.reason)
        assertEquals(50_000, w.amount)
    }

    /** R-59: Pakai Tabungan (Rencana) dari layar input — tahan 3 detik, simpan dua transaksi. */
    @Test fun useSavingsFromInput() {
        val today = LocalDate.of(2026, 11, 5)
        start(today).onboard(LocalDate.of(2026, 10, 31), 0)
        env.salary(LocalDate.of(2026, 10, 31), YearMonth.of(2026, 11))
        env.add(Tx(0, LocalDate.of(2026, 11, 1), TxType.EXPENSE, 420_000, categoryId = Defaults.ID_LAIN))
        val ready = env.ready()
        val vm = LedgerViewModel(env.repo)
        val lain = ready.input.categories.first { it.id == Defaults.ID_LAIN }
        rule.setContent {
            CatatUangTheme { Surface(color = CatatTheme.colors.surface) { InputContent(lain, ready, vm, onSaved = {}, nowTime = { LocalTime.of(19, 0) }) } }
        }
        listOf("5", "0", "000").forEach { rule.onNodeWithText(it).performClick() }
        rule.waitText("Pakai Tabungan (Rencana)", substring = true)
        rule.shot("f3-input-pakai-tabungan")
        rule.onNodeWithText("Pakai Tabungan (Rencana)", substring = true).performScrollTo().performClick()
        rule.waitText("Pakai Tabungan (Rencana)?")
        holdToConfirm("Tahan 3 detik · ambil")
        awaitData { env.ready().input.transactions.size == 5 }
        val l = env.ready().ledger
        assertEquals(0, l.sisaBebas)
        assertEquals(1_000_000, l.danaDarurat)
        assertTrue(env.ready().input.transactions.any { it.type == TxType.SAVING_WITHDRAW && it.reason == WithdrawReason.RENCANA })
    }
}
