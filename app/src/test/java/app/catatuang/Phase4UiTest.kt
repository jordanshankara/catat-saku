package app.catatuang

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.catatuang.engine.Defaults
import app.catatuang.engine.Pot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.VerdictKind
import app.catatuang.engine.WithdrawReason
import app.catatuang.ui.theme.CatatUangTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** Fase 4: Tutup Buku, Koreksi, Mode Uji Tanggal. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xxhdpi")
class Phase4UiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var env: TestEnv
    private val sep = YearMonth.of(2026, 9)

    private fun start(today: LocalDate): TestEnv {
        env = TestEnv(ApplicationProvider.getApplicationContext(), today)
        return env
    }

    @After fun tearDown() {
        if (::env.isInitialized) env.close()
    }

    private fun show() {
        env.lock.markUnlocked()
        rule.setContent { CatatUangTheme { CatatRoot(env.repo, env.settings, env.lock, env.clock) } }
    }

    private fun click(text: String, substring: Boolean = false) {
        rule.waitText(text, substring)
        val node = rule.onAllNodes(hasText(text, substring = substring))[0]
        runCatching { node.performScrollTo() }
        node.performClick()
    }

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
        rule.onNodeWithContentDescription(description, substring = true).performScrollTo().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(3_200)
        runCatching { rule.onNodeWithContentDescription(description, substring = true).performTouchInput { up() } }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    /** 6.10 lengkap untuk bulan onboarding (September) yang ditutup 1 Oktober; terbuka otomatis. */
    @Test fun closeOnboardingMonth() {
        start(LocalDate.of(2026, 10, 1)).onboard(LocalDate.of(2026, 9, 25), 500_000)
        env.salary(LocalDate.of(2026, 9, 29), YearMonth.of(2026, 10))
        env.add(Tx(0, LocalDate.of(2026, 9, 26), TxType.EXPENSE, 120_000, categoryId = Defaults.ID_TRANSPORT))
        show()
        rule.waitText("Tutup Buku September")
        rule.shot("f4-tutup-0-cek")
        click("Tidak ada, lanjut")
        rule.waitText("Sementara")
        rule.shot("f4-tutup-2-verdict")
        click("Lanjut")
        rule.waitText("Uang pegangan lu sekarang berapa?")
        val computed = env.ready().ledger.uangPegangan
        rule.onAllNodes(hasText("Uang pegangan sebenarnya"))[0].performTextInput((computed - 20_000).toString())
        click("Cocokkan")
        rule.waitText("Selisih kurang Rp 20.000", substring = true)
        rule.shot("f4-tutup-3-cocokkan")
        click("Lanjut")
        click("Lanjut") // hutang: tidak ada
        click("Lanjut") // tagihan tetap: bulan onboarding tidak punya
        rule.waitText("Mau dikemanakan?")
        click("Bawa ke bulan depan")
        rule.shot("f4-tutup-6-saku")
        click("Simpan")
        click("Tutup buku September")
        rule.waitText("Kirim backup keluar HP")
        click("Lanjut")
        rule.waitText("Final")
        rule.shot("f4-tutup-final")
        click("Selesai")
        rule.waitText("Halo, Ko")

        val ready = env.ready()
        val s = ready.ledger.months.getValue(sep)
        assertTrue(s.closed)
        assertEquals(0, s.sakuSisa)
        assertTrue(ready.ledger.months.getValue(YearMonth.of(2026, 10)).bawaan > 0)
        val snap = runBlocking { env.repo.snapshot.first() }
        assertEquals(1, snap.closures.size)
        assertTrue(snap.transactions.any { it.type == "UNRECORDED" && it.amount == 20_000L && it.closingOf == "2026-09" })
        assertNull(app.catatuang.engine.pendingClosing(ready.ledger))
    }

    /** R-72: Saku Sisa minus wajib ditutup dari Tabungan (tahan 3 detik) → BONCOS. */
    @Test fun closeWithMandatoryCover() {
        start(LocalDate.of(2026, 10, 1)).onboard(LocalDate.of(2026, 9, 25), 500_000)
        env.salary(LocalDate.of(2026, 9, 29), YearMonth.of(2026, 10))
        env.add(Tx(0, LocalDate.of(2026, 9, 25), TxType.EXPENSE, 900_000, categoryId = Defaults.ID_LAIN))
        env.skipAutoClosing()
        show()
        rule.waitText("Tutup buku September belum dilakukan")
        click("Tutup buku September belum dilakukan")
        click("Tidak ada, lanjut")
        click("Lanjut")
        click("Lewati")
        click("Lanjut")
        click("Lanjut")
        rule.waitText("Saku Sisa September minus")
        rule.shot("f4-tutup-6-minus")
        holdToConfirm("Tahan 3 detik · tutup")
        awaitData { env.ready().ledger.months.getValue(sep).sakuSisa == 0L }
        click("Tutup buku September")
        click("Lanjut")
        rule.waitText("BONCOS", substring = true)
        val s = env.ready().ledger.months.getValue(sep)
        assertEquals(VerdictKind.BONCOS, s.verdict.kind)
        val w = env.ready().input.transactions.single { it.type == TxType.SAVING_WITHDRAW }
        assertEquals(Pot.TABUNGAN, w.pot)
        assertEquals(WithdrawReason.DARURAT, w.reason)
    }

    /** R-64: transaksi bulan tertutup read-only; koreksi dicatat di bulan berjalan. */
    @Test fun correctionOnClosedMonth() {
        start(LocalDate.of(2026, 10, 5)).onboard(LocalDate.of(2026, 9, 25), 500_000)
        env.salary(LocalDate.of(2026, 9, 29), YearMonth.of(2026, 10))
        env.add(Tx(0, LocalDate.of(2026, 9, 27), TxType.EXPENSE, 45_000, categoryId = Defaults.ID_MAKAN, note = "nasi padang"))
        runBlocking { env.repo.closeMonth(sep, "PAS_PASAN", 0, "{}", null) }
        show()
        click("Riwayat")
        rule.onNodeWithContentDescription("Bulan sebelumnya").performClick()
        rule.waitText("Terkunci")
        click("nasi padang", substring = true)
        click("Buat koreksi")
        rule.waitText("Koreksi · dicatat", substring = true)
        click("Kurangi terpakai")
        listOf("5", "000").forEach { t -> rule.onAllNodesWithText(t).let { n -> n[n.fetchSemanticsNodes().lastIndex] }.performClick() }
        rule.onAllNodes(hasText("Catatan (wajib)"))[0].performTextInput("harusnya 40rb")
        click("Simpan koreksi")
        awaitData { env.ready().input.transactions.any { it.type == TxType.CORRECTION } }
        val c = env.ready().input.transactions.single { it.type == TxType.CORRECTION }
        assertEquals(-5_000L, c.signedAmount)
        assertEquals(LocalDate.of(2026, 10, 5), c.date)
        assertEquals("harusnya 40rb", c.note)
    }

    /** 8.11 Mode Uji: ketuk versi 7×, majukan tanggal, data uji dihapus saat dimatikan. */
    @Test fun testModeRoundTrip() {
        start(LocalDate.of(2026, 10, 5)).onboard(LocalDate.of(2026, 9, 25), 500_000)
        env.skipAutoClosing()
        show()
        click("Pengaturan")
        repeat(7) { click("Catat Uang versi", substring = true) }
        rule.waitText("Mode Uji Tanggal")
        rule.shot("f4-pengaturan-mode-uji")
        val before = env.ready().input.transactions.size
        runBlocking { env.repo.setTestDate(LocalDate.of(2026, 11, 2)) }
        rule.waitText("MODE UJI", substring = true)
        env.add(Tx(0, LocalDate.of(2026, 11, 2), TxType.EXPENSE, 30_000, categoryId = Defaults.ID_MAKAN))
        assertEquals(before + 1, env.ready().input.transactions.size)
        click("Matikan Mode Uji")
        awaitData { runBlocking { env.repo.snapshot.first() }.transactions.size == before && env.clock.override.value == null }
    }
}
