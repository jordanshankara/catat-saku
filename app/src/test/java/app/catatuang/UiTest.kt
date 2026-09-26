package app.catatuang

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.catatuang.engine.Defaults
import app.catatuang.engine.Slot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.ui.theme.CatatUangTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xxhdpi")
class UiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var env: TestEnv

    private fun start(today: LocalDate): TestEnv {
        env = TestEnv(ApplicationProvider.getApplicationContext(), today)
        return env
    }

    @After fun tearDown() {
        if (::env.isInitialized) env.close()
    }

    private fun show() = rule.setContent { CatatUangTheme { CatatRoot(env.repo, env.settings, env.lock, env.clock) } }

    private fun waitText(text: String, substring: Boolean = false) = rule.waitText(text, substring)

    private fun dismissDayQuestion() {
        if (rule.onAllNodesWithText("Untuk hari ini atau kemarin?").fetchSemanticsNodes().isNotEmpty()) {
            // Tombol dialog (bukan chip tanggal yang juga bertuliskan "Hari ini").
            rule.onAllNodesWithText("Hari ini").let { n -> n[n.fetchSemanticsNodes().lastIndex] }.performClick()
        }
    }

    /** Bab 13 test UI: dari Beranda sampai tersimpan dalam 4 tap (buka → pilih → angka → simpan). */
    @Test fun fourTapsFromHome() {
        val today = LocalDate.of(2026, 11, 4)
        start(today).onboard(LocalDate.of(2026, 11, 1), 3_000_000)
        env.lock.markUnlocked()
        show()
        waitText("Halo, Ko")                                         // tap 1: buka app
        rule.onAllNodesWithText("Makan")[0].performClick()          // tap 2: pilih kategori
        waitText("Simpan")
        dismissDayQuestion()
        rule.onNodeWithText("25rb").performClick()                  // tap 3: angka (chip nominal cepat)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Simpan · Rp 25.000").fetchSemanticsNodes().isNotEmpty() }
        rule.shot("ui-input-makan", containing = "Simpan")
        rule.onNodeWithText("Simpan · Rp 25.000").performScrollTo().performClick()   // tap 4: simpan
        waitText("Makan +Rp 25.000", substring = true)
        rule.shot("ui-beranda-feedback")
        val txs = runBlocking { env.repo.snapshot.first().transactions }
        assertEquals(1, txs.size)
        assertEquals(25_000, txs.single().amount)
    }

    /** Bab 13 test UI: kunci PIN, 5× salah → jeda 30 detik. */
    @Test fun pinLockout() {
        start(LocalDate.of(2026, 11, 4)).onboard(LocalDate.of(2026, 11, 1), 1_000_000, pin = "4821")
        show()
        waitText("Masukkan PIN")
        rule.shot("ui-kunci-pin")
        repeat(5) {
            repeat(4) { rule.onNodeWithText("0").performClick() }
            rule.waitForIdle()
            Thread.sleep(300)
        }
        waitText("Coba lagi dalam", substring = true)
        rule.onNodeWithText("Coba lagi dalam 30 detik", substring = true).assertExists()
    }

    @Test fun pinUnlock() {
        start(LocalDate.of(2026, 11, 4)).onboard(LocalDate.of(2026, 11, 1), 1_000_000, pin = "4821")
        show()
        waitText("Masukkan PIN")
        "4821".forEach { rule.onNodeWithText(it.toString()).performClick() }
        waitText("Halo, Ko")
    }

    /** 8.12 onboarding lengkap dari layar pertama sampai Beranda. */
    @Test fun onboardingEndToEnd() {
        start(LocalDate.of(2026, 9, 16))
        show()
        waitText("Mulai Baru")
        rule.shot("ob-1-awal")
        rule.onNodeWithText("Mulai Baru").performClick()
        rule.onNodeWithText("Lanjut").performClick()
        waitText("Buat PIN 4 digit")
        "12341234".forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.onNodeWithText("Lanjut").performClick()
        waitText("Cek pos & nominal")
        rule.shot("ob-3-pos")
        rule.onNodeWithText("Lanjut").performScrollTo().performClick()
        waitText("Saldo awal")
        rule.onAllNodes(hasText("Uang pegangan lu sekarang berapa?"))[0].performTextInput("900000")
        rule.onNodeWithText("Lanjut").performScrollTo().performClick()
        waitText("Periode awal")
        rule.shot("ob-5-periode")
        rule.onNodeWithText("Lanjut").performScrollTo().performClick()
        waitText("Izin notifikasi")
        rule.onNodeWithText("Lanjut").performScrollTo().performClick()
        waitText("Folder auto-backup")
        rule.onNodeWithText("Mulai pakai").performClick()
        waitText("Halo, Ko")
        val ready = env.ready()
        assertEquals("T-24 lewat UI", -415_000, ready.ledger.current!!.sakuSisaAwal)
        rule.shot("ob-8-beranda")
    }

    /** Isi sheet Input dirender langsung supaya bisa di-screenshot (sheet asli ada di window terpisah). */
    @Test fun inputSheetContent() {
        val today = LocalDate.of(2026, 11, 4)
        start(today).onboard(LocalDate.of(2026, 11, 1), 3_000_000)
        env.add(Tx(0, LocalDate.of(2026, 11, 3), TxType.EXPENSE, 65_000, categoryId = Defaults.ID_MAKAN), Tx(0, today, TxType.EXPENSE, 35_000, categoryId = Defaults.ID_MAKAN, slot = Slot.SIANG))
        val ready = env.ready()
        val vm = app.catatuang.feature.common.LedgerViewModel(env.repo)
        val makan = ready.input.categories.first { it.id == Defaults.ID_MAKAN }
        rule.setContent {
            CatatUangTheme {
                androidx.compose.material3.Surface(color = app.catatuang.ui.theme.CatatTheme.colors.surface) {
                    app.catatuang.feature.input.InputContent(makan, ready, vm, onSaved = {}, nowTime = { java.time.LocalTime.of(19, 30) })
                }
            }
        }
        listOf("2", "5", "000").forEach { rule.onNodeWithText(it).performClick() }
        waitText("hutang makan jadi", substring = true)
        rule.onNodeWithText("Lebih Rp 10.000 dari jatah — hutang makan jadi Rp 25.000").assertExists()
        rule.shot("f2-input-makan")
    }

    /** Screenshot layar Fase 2 dengan data realistis (Jumat, jendela akhir pekan terbuka). */
    @Test fun screensWithData() {
        val today = LocalDate.of(2026, 11, 6)
        start(today).onboard(LocalDate.of(2026, 11, 1), 3_000_000)
        val makan = Defaults.ID_MAKAN
        fun e(day: Int, cat: Long, amount: Long, slot: Slot? = null, note: String? = null) =
            Tx(0, LocalDate.of(2026, 11, day), TxType.EXPENSE, amount, categoryId = cat, slot = slot, note = note)
        env.add(
            e(1, makan, 50_000, Slot.SIANG), e(2, makan, 65_000, Slot.MALAM, "nasi padang"), e(3, makan, 40_000, Slot.SIANG),
            e(4, makan, 72_000, Slot.MALAM), e(5, makan, 43_000, Slot.SIANG), e(6, makan, 12_000, Slot.SARAPAN), e(6, makan, 23_000, Slot.SIANG),
            e(1, Defaults.ID_BUAH, 10_000), e(3, Defaults.ID_BUAH, 8_000), e(6, Defaults.ID_BUAH, 10_000),
            e(4, Defaults.ID_TRANSPORT, 30_000, note = "ojek"), e(6, Defaults.ID_TRANSPORT, 60_000),
            e(2, Defaults.ID_PROTEIN, 93_000), e(5, Defaults.ID_PROTEIN, 72_000), e(3, Defaults.ID_LAIN, 40_000, note = "sabun"),
        )
        env.lock.markUnlocked()
        show()
        waitText("Halo, Ko")
        rule.shot("f2-beranda")
        rule.onAllNodesWithText("Makan")[0].performTouchInput { longClick() }
        waitText("Jatah Makan")
        rule.shot("f2-detail-makan")
        rule.onNodeWithContentDescription("Kembali").performClick()
        waitText("Halo, Ko")
        rule.onAllNodesWithText("Transport OE")[0].performTouchInput { longClick() }
        waitText("Akhir pekan November")
        rule.shot("f2-detail-transport")
        rule.onNodeWithContentDescription("Kembali").performClick()
        waitText("Halo, Ko")
        rule.onNodeWithText("Riwayat").performClick()
        waitText("Keluar", substring = true)
        rule.shot("f2-riwayat")
        rule.onNodeWithContentDescription("Catat cepat").performClick()
        waitText("Catat pengeluaran")
        rule.shot("f2-pilih-kategori", containing = "Catat pengeluaran")
    }
}
