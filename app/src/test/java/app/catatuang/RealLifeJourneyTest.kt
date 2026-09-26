package app.catatuang

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.catatuang.data.AppState
import app.catatuang.engine.Defaults
import app.catatuang.engine.Pot
import app.catatuang.engine.Slot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.VerdictKind
import app.catatuang.engine.WithdrawReason
import app.catatuang.engine.computeLedger
import app.catatuang.engine.valueIn
import app.catatuang.engine.pendingClosing
import app.catatuang.engine.store.BackupCodec
import app.catatuang.engine.store.BackupDocument
import app.catatuang.engine.store.BackupReadResult
import app.catatuang.engine.store.toLedgerInput
import app.catatuang.export.ExportFormat
import app.catatuang.export.Ranges
import app.catatuang.export.buildExport
import app.catatuang.notify.Slot as NSlot
import app.catatuang.notify.dailyNotification
import app.catatuang.notify.reminderNotifications
import app.catatuang.ui.theme.CatatUangTheme
import app.catatuang.widget.WidgetData
import app.catatuang.widget.widgetData
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
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Simulasi hidup nyata pengguna: mulai 27 Sep 2026 dengan uang pegangan 500.000, gaji 29 Sep untuk
 * Oktober, Tutup Buku September 1 Okt, satu bulan Oktober penuh (5 akhir pekan), gaji November 30 Okt,
 * Tutup Buku Oktober 2 Nov. App asli (Room + repository + layar Compose), tanggal dimajukan hari demi hari.
 * Semua angka dihitung manual dari spesifikasi (komentar "hitung:").
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xxhdpi")
class RealLifeJourneyTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var env: TestEnv
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()

    @After fun tearDown() { if (::env.isInitialized) env.close() }

    // ---------- helper ----------

    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d)

    private fun ready(): AppState.Ready = env.ready()
    private fun ledger() = ready().ledger

    /** Majukan "hari ini" (seperti HP melewati tengah malam) dan tunggu ledger ikut. */
    private fun goTo(date: LocalDate) {
        settle()
        checkNoMoneyLeak()
        env.clock.override.value = date
        runBlocking { env.repo.state.first { it is AppState.Ready && it.today == date } }
        rule.waitForIdle()
        checkNoMoneyLeak()
    }

    /**
     * Kekekalan uang: pegangan + Tabungan + Dana Darurat = saldo awal + semua uang masuk − semua uang keluar.
     * Pindah kantong (setor/ambil/Nabung rutin/bawa/pelunasan) tidak boleh menciptakan atau menghilangkan rupiah.
     */
    private fun checkNoMoneyLeak() {
        val r = ready()
        val o = r.input.onboarding
        val txs = r.input.transactions.filter { !it.date.isAfter(r.today) }
        fun sum(t: TxType) = txs.filter { it.type == t }.sumOf { it.amount }
        val expected = o.cashStart + o.savingsStart + o.emergencyStart +
            sum(TxType.SALARY) + sum(TxType.INCOME) + sum(TxType.REFUND) + sum(TxType.SURPLUS_FOUND) -
            sum(TxType.EXPENSE) - sum(TxType.FIXED_PAYMENT) - sum(TxType.UNRECORDED) -
            txs.filter { it.type == TxType.CORRECTION }.sumOf { it.signedAmount ?: 0 }
        val l = r.ledger
        assertEquals("uang bocor pada ${r.today}", expected, l.uangPegangan + l.tabungan + l.danaDarurat)
    }

    private fun awaitData(cond: () -> Boolean) {
        repeat(60) {
            rule.waitForIdle()
            if (cond()) return
            Thread.sleep(200)
        }
        throw AssertionError("Kondisi data tidak tercapai")
    }

    private fun click(text: String, substring: Boolean = false) {
        rule.waitText(text, substring)
        val node = rule.onAllNodes(hasText(text, substring = substring))[0]
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun lastClick(text: String) =
        rule.onAllNodesWithText(text).let { n -> n[n.fetchSemanticsNodes().lastIndex] }.performClick()

    private fun typeAmount(amount: Long) {
        var s = amount.toString()
        val keys = mutableListOf<String>()
        while (s.endsWith("000") && s.length > 3) { keys.add(0, "000"); s = s.dropLast(3) }
        keys.addAll(0, s.map { it.toString() })
        keys.forEach(::lastClick)
    }

    private fun dismissDayQuestion() {
        if (rule.onAllNodesWithText("Untuk hari ini atau kemarin?").fetchSemanticsNodes().isNotEmpty()) lastClick("Hari ini")
    }

    /** Biarkan kartu feedback (3 dtk) & snackbar Urungkan (5 dtk) hilang, seperti orang sungguhan. */
    private fun settle() {
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
    }

    private fun home() {
        settle()
        click("Beranda")
        rule.waitText("Halo, Ko")
    }

    /** Alur 4 tap dari Beranda: tile → nominal → Simpan. */
    private fun uiSpend(tile: String, amount: Long) {
        val before = ready().input.transactions.size
        home()
        rule.onAllNodesWithText(tile)[0].performScrollTo().performClick()
        rule.waitText("Simpan")
        dismissDayQuestion()
        typeAmount(amount)
        val label = "Simpan · ${app.catatuang.ui.format.rp(amount)}"
        rule.waitText(label)
        rule.onNodeWithText(label).performScrollTo().performClick()
        // R-65 anti-typo bisa muncul; di simulasi ini nominalnya wajar.
        awaitData { ready().input.transactions.size == before + 1 }
    }

    private fun spend(date: LocalDate, cat: Long, amount: Long) =
        env.add(Tx(0, date, TxType.EXPENSE, amount, categoryId = cat, slot = if (cat == Defaults.ID_MAKAN) Slot.SIANG else null))

    private fun holdToConfirm(description: String) {
        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(description, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        runCatching { rule.onNodeWithContentDescription(description, substring = true).performScrollTo() }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription(description, substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(3_200)
        runCatching { rule.onNodeWithContentDescription(description, substring = true).performTouchInput { up() } }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    /** Aksesibilitas + tidak crash: setiap elemen yang bisa diketuk punya label. */
    private fun checkScreen(name: String) {
        rule.waitForIdle()
        val unlabeled = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { n ->
            val c = n.config
            c.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.EditableText) == null
        }
        assertTrue("$name: ${unlabeled.size} elemen tanpa label", unlabeled.isEmpty())
        rule.shot("journey-$name")
    }

    // ---------- simulasi ----------

    @Test fun realLifeSeptemberToNovember() {
        env = TestEnv(ctx, day(9, 27))
        env.lock.markUnlocked()
        rule.setContent { CatatUangTheme { CatatRoot(env.repo, env.settings, env.lock, env.clock) } }

        // ===== Minggu 27 Sep: onboarding lewat UI =====
        rule.waitText("Mulai Baru")
        click("Mulai Baru")
        click("Lanjut")
        rule.waitText("Buat PIN 4 digit")
        "48214821".forEach { lastClick(it.toString()) }
        click("Lanjut")
        rule.waitText("Cek pos & nominal")
        click("Lanjut")
        rule.waitText("Saldo awal")
        rule.onAllNodes(hasText("Uang pegangan lu sekarang berapa?"))[0].performTextInput("500000")
        rule.onAllNodes(hasText("Tabungan sekarang berapa?"))[0].performTextInput("500000")
        rule.onAllNodes(hasText("Dana darurat sekarang berapa?"))[0].performTextInput("1000000")
        click("Lanjut")
        rule.waitText("Periode awal")
        rule.shot("journey-onboarding-periode")
        click("Lanjut")
        click("Lanjut")
        click("Mulai pakai")
        rule.waitText("Halo, Ko")

        // hitung (8.12, 27–30 Sep = 4 hari, D=30):
        //   harian (50.000+10.000)×4 = 240.000 · Transport: akhir pekan 25–27 Sep sedang berjalan → 1×120.000
        //   Protein 200.000×4/30 = 26.666 → 26.000 · Lain 150.000×4/30 = 20.000 → total 406.000
        //   SakuSisaAwal = 500.000 − 406.000 = 94.000
        var l = ledger()
        assertEquals(94_000, l.current!!.sakuSisaAwal)
        assertEquals(94_000, l.sakuSisa)
        assertEquals(500_000, l.tabungan)
        assertEquals(1_000_000, l.danaDarurat)
        assertEquals(500_000, l.uangPegangan)
        checkScreen("27sep-beranda")

        // Catat lewat alur 4 tap: Makan 45rb, Buah 10rb, Transport pulang-balik 110rb (Minggu, jendela 25–27 Sep).
        uiSpend("Makan", 45_000)
        uiSpend("Buah", 10_000)
        uiSpend("Transport OE", 110_000)
        l = ledger()
        assertEquals("hari ini masih sementara (R-21)", 94_000, l.sakuSisa)
        assertEquals(5_000, l.daily.first { it.categoryId == Defaults.ID_MAKAN }.remaining)
        // Widget: sisa jatah hari ini, tanpa saldo.
        val w = widgetData(ready()) as WidgetData.Lines
        assertEquals(listOf(5_000L, 0L), w.lines.map { it.remaining })

        // ===== Senin 28 Sep =====
        goTo(day(9, 28))
        // hitung: 27 Sep tutup → Makan hemat 5.000, Buah 0; jendela tutup → 120.000−110.000 = +10.000 → 109.000
        assertEquals(109_000, ledger().sakuSisa)
        uiSpend("Makan", 65_000)

        // ===== Selasa 29 Sep: gaji Oktober masuk =====
        goTo(day(9, 29))
        // hitung: 28 Sep Makan lebih 15.000 → hutang 15.000; Buah tidak dipakai → +10.000 → 119.000
        l = ledger()
        assertEquals(119_000, l.sakuSisa)
        assertEquals(15_000L, l.hutang[Defaults.ID_MAKAN])
        // Notifikasi 12:00 tgl 29: "Gaji sudah masuk?"
        assertTrue(reminderNotifications(NSlot.AT_1200, ready().input, l, day(9, 29), null to 0, null).any { it.title == "Gaji sudah masuk?" })
        click("Gajian sudah masuk?")
        rule.waitText("Nominal gaji")
        click("Lanjut")
        rule.waitText("Target Cadangan Rp 180.000") // bawaan belum ada → 180.000 "bisa berubah" (T-07)
        click("Konfirmasi")
        click("Transfer Rp 160.000 ke rekening tabungan")
        click("Selesai")
        rule.waitText("Gaji Oktober aman · aktif tgl 1")
        l = ledger()
        assertEquals("R-03: gaji cepat tidak menambah Saku September", 119_000, l.sakuSisa)
        assertEquals(3_300_000, l.saldoPending)
        assertEquals(500_000, l.tabungan) // Nabung rutin baru tercatat 1 Okt (R-07)
        spend(day(9, 29), Defaults.ID_MAKAN, 50_000)
        spend(day(9, 29), Defaults.ID_BUAH, 10_000)

        // ===== Rabu 30 Sep =====
        goTo(day(9, 30))
        // hitung: 29 Sep Makan pas (hutang tetap 15.000), Buah pas → 119.000
        assertEquals(119_000, ledger().sakuSisa)
        assertEquals(15_000L, ledger().hutang[Defaults.ID_MAKAN])
        uiSpend("Protein", 93_000)
        // hitung R-31: budget Protein onboarding 26.000 → lebih 67.000 langsung dari Saku → 52.000
        assertEquals(52_000, ledger().sakuSisa)
        uiSpend("Lain-lain", 25_000) // budget 20.000 → −5.000 → 47.000
        assertEquals(47_000, ledger().sakuSisa)
        uiSpend("Makan", 35_000) // hemat 15.000 → lunasi hutang saat hari ditutup
        // Notifikasi 22:00 hanya preview (R-21).
        val n = dailyNotification(ready().input, ledger(), day(9, 30), doneMarked = false)!!
        assertTrue(n.text, n.text.contains("Makan"))

        // ===== Kamis 1 Okt: Tutup Buku September terbuka otomatis =====
        goTo(day(10, 1))
        l = ledger()
        // hitung: 30 Sep Makan hemat 15.000 → hutang 0 (Saku +0); Buah tidak dipakai +10.000 → 57.000
        //   R-39: Protein & Lain sudah lebih → tidak ada sisa dicairkan.
        assertEquals(57_000, l.months.getValue(sep).sakuSisa)
        assertEquals(0L, l.hutang[Defaults.ID_MAKAN] ?: 0L)
        // hitung Oktober: Kebutuhan 3.360.000, gaji 3.300.000, bawaan belum → −60.000; Target 180.000
        assertEquals(-60_000, l.months.getValue(oct).sakuSisaAwal)
        assertEquals(660_000, l.tabungan) // 500.000 + Nabung rutin 160.000 pada 1 Okt (R-07)
        assertEquals(0, l.saldoPending)
        // hitung uang pegangan: 500.000 − pengeluaran 443.000 + gaji 3.300.000 − setoran Nabung 160.000
        assertEquals(3_197_000, l.uangPegangan)
        assertTrue(reminderNotifications(NSlot.AT_0700, ready().input, l, day(10, 1), null to 0, null).any { it.title == "September selesai. Yuk tutup buku." })

        rule.waitText("Tutup Buku September")
        click("Tidak ada, lanjut")
        rule.waitText("Sementara")
        rule.waitText("BERHASIL NABUNG", substring = true) // Saku 57.000 ≥ ambang 50.000, tanpa hutang
        click("Lanjut")
        rule.waitText("Uang pegangan lu sekarang berapa?")
        rule.onAllNodes(hasText("Uang pegangan sebenarnya"))[0].performTextInput((3_197_000 - 7_000).toString())
        click("Cocokkan")
        rule.waitText("Selisih kurang Rp 7.000", substring = true) // UNRECORDED 7.000 → Saku 50.000 (T-17)
        click("Lanjut")
        click("Lanjut") // hutang: tidak ada
        click("Lanjut") // tagihan: bulan onboarding tidak punya (D-19)
        rule.waitText("Mau dikemanakan?")
        click("Bawa ke bulan depan")
        click("Simpan")
        click("Tutup buku September")
        click("Lanjut") // backup
        rule.waitText("Final")
        rule.waitText("BERHASIL NABUNG", substring = true) // 50.000 masih ≥ ambang
        checkScreen("1okt-verdict-september")
        click("Selesai")
        rule.waitText("Halo, Ko")
        l = ledger()
        assertTrue(l.months.getValue(sep).closed)
        assertEquals(VerdictKind.BERHASIL_NABUNG, l.months.getValue(sep).verdict.kind)
        // hitung: bawaan 50.000 → SakuSisaAwal Okt −10.000; Target = 120.000 − (−10.000) = 130.000 (T-07)
        assertEquals(50_000, l.months.getValue(oct).bawaan)
        assertEquals(-10_000, l.months.getValue(oct).sakuSisaAwal)
        assertEquals(130_000L, l.targetCadangan)
        assertEquals(-10_000, l.sakuSisa)
        // R-40: kewajiban TETAP Oktober dibuat tanggal 1.
        awaitData { runBlocking { env.repo.snapshot.first() }.fixedObligations.count { it.yearMonth == "2026-10" } == 2 }

        // Bayar AI 405.000 (kurs naik) dari Beranda → selisih −15.000 (T-16); Iuran Mess 120.000 pas.
        rule.waitText("Tagihan tetap bulan ini")
        rule.onAllNodesWithText("Bayar")[1].performScrollTo().performClick()
        rule.waitText("AI jatuh tempo. Bayar berapa?")
        repeat(7) { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Hapus angka")).let { n -> n[n.fetchSemanticsNodes().lastIndex] }.performClick() }
        typeAmount(405_000)
        click("Bayar · Rp 405.000")
        awaitData { ledger().current!!.fixed.first { it.categoryId == Defaults.ID_AI }.isPaid }
        rule.waitForIdle()
        rule.onAllNodesWithText("Bayar")[0].performScrollTo().performClick()
        click("Bayar · Rp 120.000")
        awaitData { ledger().current!!.fixed.all { it.isPaid } }
        assertEquals(-25_000, ledger().sakuSisa)
        spend(day(10, 1), Defaults.ID_MAKAN, 50_000); spend(day(10, 1), Defaults.ID_BUAH, 10_000)

        // ===== Oktober: hari-hari biasa (Makan 50.000 + Buah 10.000 = pas jatah) =====
        fun routine(d: Int) { spend(day(10, d), Defaults.ID_MAKAN, 50_000); spend(day(10, d), Defaults.ID_BUAH, 10_000) }

        goTo(day(10, 2)) // Jumat: jendela akhir pekan 1 terbuka; Transport di posisi pertama (R-33)
        routine(2)
        spend(day(10, 2), Defaults.ID_TRANSPORT, 60_000) // T-31: Jumat masuk akhir pekan Sabtu 3 Okt
        assertEquals("Akhir pekan ini: Rp 60rb / Rp 120rb", app.catatuang.feature.home.buildHomeUi("Ko", ready().input, ledger(), day(10, 2))
            .tiles.first().status)
        goTo(day(10, 3)); routine(3)
        goTo(day(10, 4)); routine(4)
        spend(day(10, 4), Defaults.ID_TRANSPORT, 55_000)
        goTo(day(10, 5)); routine(5)
        // hitung: jendela 1 = 115.000 → +5.000 → −25.000 + 5.000 = −20.000
        assertEquals(-20_000, ledger().sakuSisa)
        goTo(day(10, 6)); routine(6)
        spend(day(10, 6), Defaults.ID_PROTEIN, 93_000)
        goTo(day(10, 7)); routine(7)
        spend(day(10, 7), Defaults.ID_TRANSPORT, 50_000) // T-35 trip tambahan Rabu → −50.000 langsung
        assertEquals(-70_000, ledger().sakuSisa)
        goTo(day(10, 8)); routine(8)
        env.add(Tx(0, day(10, 8), TxType.REFUND, 30_000, categoryId = Defaults.ID_TRANSPORT)) // refund di luar jendela → +30.000
        assertEquals(-40_000, ledger().sakuSisa)
        goTo(day(10, 9)); routine(9)
        goTo(day(10, 10)); routine(10)
        // Pemasukan lewat UI: Pemberian 100.000 ke Saku Sisa.
        click("Pemasukan")
        rule.waitText("Pemberian")
        click("Pemberian")
        typeAmount(100_000)
        click("Simpan · Rp 100.000")
        awaitData { ready().input.transactions.any { it.type == TxType.INCOME } }
        assertEquals(60_000, ledger().sakuSisa)
        goTo(day(10, 11)); routine(11)
        goTo(day(10, 12)); routine(12)
        // hitung: akhir pekan 2 tanpa pulang → +120.000 (T-33) → 180.000
        assertEquals(180_000, ledger().sakuSisa)

        // Selasa 13 Okt: makan kebanyakan lewat UI → hutang (preview di layar input).
        goTo(day(10, 13))
        spend(day(10, 13), Defaults.ID_BUAH, 10_000)
        uiSpend("Makan", 95_000)
        goTo(day(10, 14)); spend(day(10, 14), Defaults.ID_BUAH, 10_000)
        spend(day(10, 14), Defaults.ID_MAKAN, 40_000)
        assertEquals(45_000L, ledger().hutang[Defaults.ID_MAKAN])
        goTo(day(10, 15)); routine(15)
        // hitung: 14 Okt hemat 10.000 → hutang 35.000, Saku tetap 180.000
        assertEquals(35_000L, ledger().hutang[Defaults.ID_MAKAN])
        assertEquals(180_000, ledger().sakuSisa)
        // Mode Darurat dari Detail Makan (tahan tile) → lunasi 35.000 dari Saku Sisa (R-23 langkah 1).
        home()
        rule.onAllNodesWithText("Makan")[0].performScrollTo().performTouchInput { longClick() }
        rule.waitText("Mode darurat")
        checkScreen("15okt-detail-makan")
        click("Mode darurat")
        click("Lunasi Rp 35.000 dari Saku Sisa")
        awaitData { (ledger().hutang[Defaults.ID_MAKAN] ?: 0L) == 0L }
        assertEquals(145_000, ledger().sakuSisa)
        assertEquals(660_000, ledger().tabungan) // kantong tidak tersentuh
        rule.onNodeWithContentDescription("Kembali").performClick()

        goTo(day(10, 16)); routine(16)
        spend(day(10, 16), Defaults.ID_TRANSPORT, 70_000)
        goTo(day(10, 17)); routine(17)
        spend(day(10, 17), Defaults.ID_LAIN, 100_000)
        goTo(day(10, 18)); routine(18)
        spend(day(10, 18), Defaults.ID_TRANSPORT, 60_000)
        goTo(day(10, 19)); routine(19)
        // hitung: akhir pekan 3 = 130.000 → −10.000 → 135.000
        assertEquals(135_000, ledger().sakuSisa)

        // Selasa 20 Okt: ambil Tabungan RENCANA 100.000 (tahan 3 detik) → Saku +100.000, bukan BONCOS (T-15).
        goTo(day(10, 20)); routine(20)
        spend(day(10, 20), Defaults.ID_PROTEIN, 93_000)
        home()
        click("Tabungan")
        rule.waitText("Tabungan & Dana Darurat")
        checkScreen("20okt-tabungan")
        rule.onAllNodesWithText("Ambil")[0].performClick()
        rule.waitText("Ambil dari Tabungan")
        click("Rencana")
        typeAmount(100_000)
        holdToConfirm("Tahan 3 detik · ambil Rp 100.000")
        awaitData { ready().input.transactions.any { it.type == TxType.SAVING_WITHDRAW } }
        assertEquals(560_000, ledger().tabungan)
        assertEquals(235_000, ledger().sakuSisa)
        rule.onNodeWithContentDescription("Kembali").performClick()

        goTo(day(10, 21)); routine(21)
        goTo(day(10, 22)); routine(22)
        // Salah ketik Lain-lain 700.000 → anti-typo tidak ada data; langsung edit dari Riwayat jadi 70.000 (R-63).
        spend(day(10, 22), Defaults.ID_LAIN, 700_000)
        home()
        click("Riwayat")
        click("Lain-lain", substring = false)
        rule.waitForIdle()
        checkScreen("22okt-riwayat")
        val wrong = ready().input.transactions.single { it.amount == 700_000L }
        runBlocking { env.repo.updateTransaction(wrong.copy(amount = 70_000)) }
        awaitData { ready().input.transactions.none { it.amount == 700_000L } }
        // hitung: Lain 100.000 + 70.000 = 170.000, budget 150.000 → −20.000 (T-37) → 215.000
        assertEquals(215_000, ledger().sakuSisa)
        goTo(day(10, 23)); routine(23)
        spend(day(10, 23), Defaults.ID_TRANSPORT, 120_000)
        goTo(day(10, 24)); routine(24)
        env.add(Tx(0, day(10, 24), TxType.REFUND, 20_000, categoryId = Defaults.ID_TRANSPORT)) // refund dalam jendela (T-43)
        goTo(day(10, 25)); routine(25)
        goTo(day(10, 26)); routine(26)
        // hitung: akhir pekan 4 = 100.000 → +20.000 → 235.000
        assertEquals(235_000, ledger().sakuSisa)
        // H-5 akhir Oktober (26 Okt): cadangan sudah terkumpul (Sisa bebas ≥ 0) → tidak ada notifikasi cadangan.
        l = ledger()
        assertEquals(120_000, l.reservasi) // akhir pekan ke-5 dicadangkan (R-15)
        assertEquals(115_000, l.sisaBebas)
        assertEquals(130_000, l.cadanganTerkumpul)
        assertTrue(reminderNotifications(NSlot.AT_0900, ready().input, l, day(10, 26), null to 0, null).none { it.title == "Cadangan belum terkumpul" })

        goTo(day(10, 27)); routine(27)
        goTo(day(10, 28)); routine(28)
        goTo(day(10, 29)); routine(29)
        // Gaji November masuk 30 Okt → pending (R-03); alokasi November sama dengan template.
        goTo(day(10, 30)); routine(30)
        env.salary(day(10, 30), nov)
        assertEquals(3_300_000, ledger().saldoPending)
        assertEquals(235_000, ledger().sakuSisa)
        goTo(day(10, 31)); routine(31)
        uiSpend("Transport OE", 60_000) // Sabtu 31 Okt: akhir pekan ke-5, jendela masih terbuka (T-10 varian)
        l = ledger()
        assertEquals(175_000, l.sakuSisa)
        assertEquals(60_000, l.reservasi)
        assertEquals(115_000, l.sisaBebas) // Sisa bebas tidak berubah akibat akhir pekan 5

        // ===== Minggu 1 Nov: bulan baru, jendela Oktober masih terbuka → Tutup Buku harus menunggu (R-32) =====
        goTo(day(11, 1))
        spend(day(11, 1), Defaults.ID_TRANSPORT, 60_000) // T-11: dihitung ke Oktober
        l = ledger()
        // hitung Oktober setelah 31 Okt tertutup: Protein 186.000 → sisa 14.000 dicairkan (T-29) → 175.000 + 14.000 − 60.000 (Minggu) = 129.000
        assertEquals(129_000, l.months.getValue(oct).sakuSisa)
        assertEquals(720_000, l.tabungan) // 560.000 + Nabung rutin November 160.000 (1 Nov)
        assertEquals(oct, pendingClosing(l)!!.month)
        rule.waitText("Tutup Buku Oktober") // terbuka otomatis (D-50)
        rule.waitText("Menunggu akhir pekan selesai (Senin)", substring = true)
        checkScreen("1nov-tutup-menunggu")
        rule.onNodeWithContentDescription("Kembali").performClick()

        // ===== Senin 2 Nov: Tutup Buku Oktober =====
        goTo(day(11, 2))
        l = ledger()
        // hitung manual seluruh Oktober:
        //   −10.000 awal · −15.000 AI · +5.000 WE1 · −50.000 trip Rabu · +30.000 refund · +100.000 pemberian
        //   +120.000 WE2 · −35.000 Mode Darurat · −10.000 WE3 · +100.000 ambil RENCANA · −20.000 Lain
        //   +20.000 WE4 · −120.000 WE5 (reservasi) · +14.000 Protein = 129.000
        assertEquals(129_000, l.months.getValue(oct).sakuSisa)
        assertEquals(0, l.months.getValue(oct).reservasi)
        assertEquals(VerdictKind.BERHASIL_NABUNG, l.months.getValue(oct).verdict.kind)
        click("Tutup buku Oktober belum dilakukan")
        click("Tidak ada, lanjut")
        rule.waitText("BERHASIL NABUNG", substring = true)
        click("Lanjut")
        click("Lewati") // cocokkan saldo
        click("Lanjut") // hutang
        click("Lanjut") // tagihan sudah lunas
        rule.waitText("Mau dikemanakan?")
        click("Semua ke Tabungan")
        click("Simpan")
        click("Tutup buku Oktober")
        click("Lanjut")
        rule.waitText("Final")
        checkScreen("2nov-verdict-oktober")
        click("Selesai")
        rule.waitText("Halo, Ko")
        l = ledger()
        assertTrue(l.months.getValue(oct).closed)
        assertEquals(849_000, l.tabungan) // 720.000 + 129.000
        assertEquals(1_000_000, l.danaDarurat) // tidak pernah disentuh
        assertEquals(0, l.months.getValue(nov).bawaan)
        assertEquals(0, l.months.getValue(nov).sakuSisaAwal) // November: 30 hari, 4 Sabtu → pas
        assertNull(pendingClosing(l))
        // Riwayat: transaksi Minggu 1 Nov diberi label "dihitung ke Oktober" & bulan Oktober terkunci.
        click("Riwayat")
        rule.waitText("November 2026") // bulan baru → Riwayat kembali ke bulan berjalan
        click("Semua")
        rule.onNodeWithContentDescription("Bulan sebelumnya").performClick()
        rule.waitText("Oktober 2026")
        rule.waitText("Terkunci")
        rule.waitText("dihitung ke Oktober", substring = true)
        checkScreen("2nov-riwayat-oktober")

        // ===== Laporan & export =====
        click("Laporan")
        rule.waitText("Total pengeluaran")
        checkScreen("2nov-laporan")
        val r = ready()
        val xlsx = buildExport(ctx, r.input, r.ledger, Ranges.month(oct), ExportFormat.XLSX)
        assertTrue(xlsx.size > 1_000)

        // ===== Backup → HP baru → Pulihkan: seluruh ledger identik (T-25) =====
        val text = runBlocking { BackupCodec.encode(BackupDocument.of(env.repo.exportSnapshot(), "0.6.0", LocalDateTime.of(2026, 11, 2, 21, 0))) }
        val decoded = BackupCodec.decode(text) as BackupReadResult.Ok
        val phone = TestEnv(ctx, day(11, 2))
        try {
            runBlocking { phone.repo.restoreFromBackup(decoded.document.toSnapshot()) }
            val a = runBlocking { env.repo.snapshot.first() }.toLedgerInput()!!
            val b = runBlocking { phone.repo.snapshot.first() }.toLedgerInput()!!
            assertEquals(computeLedger(a, day(11, 2)), computeLedger(b, day(11, 2)))
        } finally { phone.close() }

        // Rekap akhir: tabungan naik dari 500.000 jadi 849.000, dana darurat utuh.
        val all = ready().input.transactions
        assertTrue(all.none { it.type == TxType.SAVING_WITHDRAW && it.reason == WithdrawReason.DARURAT })
        assertEquals(1, all.count { it.type == TxType.SAVING_WITHDRAW && it.pot == Pot.TABUNGAN })
    }

    /**
     * Bulan-bulan sulit: ubah pos di Pengaturan, gaji telat (talangan), gaji kurang (Penyesuaian), boros
     * sampai Saku minus (wajib tutup → BONCOS), lalu satu bulan tanpa gaji sama sekali (TANPA GAJI).
     */
    @Test fun hardTimesNovemberToFebruary() {
        env = TestEnv(ctx, LocalDate.of(2026, 11, 30))
        env.onboard(LocalDate.of(2026, 11, 30), 100_000) // Tabungan 500.000, Dana Darurat 1.000.000
        env.lock.markUnlocked()
        rule.setContent { CatatUangTheme { CatatRoot(env.repo, env.settings, env.lock, env.clock) } }
        rule.waitText("Halo, Ko")
        val dec = YearMonth.of(2026, 12)
        val jan = YearMonth.of(2027, 1)
        fun d(y: Int, m: Int, dd: Int) = LocalDate.of(y, m, dd)

        // hitung 30 Nov (1 hari): harian 60.000 + Transport 0 (tak ada akhir pekan tersisa) + Protein 6.000 + Lain 5.000
        //   → SakuSisaAwal = 100.000 − 71.000 = 29.000
        assertEquals(29_000, ledger().current!!.sakuSisaAwal)
        spend(d(2026, 11, 30), Defaults.ID_MAKAN, 50_000)
        spend(d(2026, 11, 30), Defaults.ID_BUAH, 10_000)

        // ===== Pengaturan lewat UI: Makan jadi 45.000/hari & tambah pos "Galon" 40.000 mulai Desember (R-95) =====
        settle()
        click("Pengaturan")
        click("Pos & nominal")
        rule.waitText("Perubahan berlaku mulai Desember")
        click("Makan")
        rule.waitText("Nominal / hari mulai Desember")
        rule.onAllNodes(hasText("Nominal / hari mulai Desember"))[0].performTextInput("")
        val field = rule.onAllNodes(hasText("Nominal / hari mulai Desember"))[0]
        field.performClick()
        field.performTextReplacementSafe("45000")
        click("Simpan")
        rule.waitText("Rp 50.000 → Rp 45.000 / hari mulai Desember")
        settle()
        click("Tambah pos")
        rule.waitText("Nama pos")
        rule.onAllNodes(hasText("Nama pos"))[0].performTextInput("Galon")
        rule.onAllNodes(hasText("Budget per bulan"))[0].performTextInput("40000")
        click("Tambah")
        rule.waitText("Baru · Rp 40.000 / bulan mulai Desember")
        checkScreen("30nov-pos")
        var cats = ready().input.categories
        val makan = cats.first { it.id == Defaults.ID_MAKAN }
        assertEquals(50_000L, makan.valueIn(YearMonth.of(2026, 11)))
        assertEquals(45_000L, makan.valueIn(dec))
        val galon = cats.first { it.name == "Galon" }
        assertTrue(!galon.isActiveIn(YearMonth.of(2026, 11)) && galon.isActiveIn(dec))
        rule.onNodeWithContentDescription("Kembali").performClick()

        // ===== Selasa 1 Des: Tutup Buku November (PAS-PASAN), gaji Desember belum masuk =====
        goTo(d(2026, 12, 1))
        // hitung: 30 Nov pas; R-39 sisa Protein 6.000 + Lain 5.000 dicairkan → 40.000 < ambang 50.000 → PAS-PASAN
        assertEquals(40_000, ledger().months.getValue(YearMonth.of(2026, 11)).sakuSisa)
        settle()
        click("Beranda") // Tutup Buku terbuka otomatis saat Beranda dibuka (D-50)
        rule.waitText("Tutup Buku November")
        click("Tidak ada, lanjut")
        rule.waitText("PAS-PASAN", substring = true)
        click("Lanjut")
        click("Lewati")
        click("Lanjut")
        click("Lanjut")
        rule.waitText("Mau dikemanakan?")
        click("Bawa ke bulan depan")
        click("Simpan")
        click("Tutup buku November")
        click("Lanjut")
        rule.waitText("Final")
        click("Selesai")
        rule.waitText("Halo, Ko")
        assertEquals(VerdictKind.PAS_PASAN, ledger().months.getValue(YearMonth.of(2026, 11)).verdict.kind)

        // Gaji telat: bayar AI 390.000 tanggal 1 + makan 2 hari → semuanya "ditalangi" (bayangan, R-04).
        awaitData { runBlocking { env.repo.snapshot.first() }.fixedObligations.count { it.yearMonth == "2026-12" } == 2 }
        runBlocking { env.repo.payFixed(dec, app.catatuang.engine.fixedPaymentTx(Defaults.ID_AI, 390_000, d(2026, 12, 1)), 390_000) }
        spend(d(2026, 12, 1), Defaults.ID_MAKAN, 45_000); spend(d(2026, 12, 1), Defaults.ID_BUAH, 10_000)
        goTo(d(2026, 12, 2))
        spend(d(2026, 12, 2), Defaults.ID_MAKAN, 45_000); spend(d(2026, 12, 2), Defaults.ID_BUAH, 10_000)
        goTo(d(2026, 12, 3))
        var l = ledger()
        // hitung talangan: 390.000 + 55.000 + 55.000 = 500.000 → Sisa bawaan 40.000, Tabungan 460.000 (bayangan)
        assertEquals(500_000, l.talangan!!.amount)
        assertEquals(500_000, l.tabungan) // uang sebenarnya tidak bergerak
        assertEquals(40_000, l.tabunganShown)
        assertTrue(l.input().transactions.none { it.type == TxType.SAVING_WITHDRAW })
        rule.waitText("Ditalangi Rp 500.000")
        checkScreen("3des-talangan")

        // ===== 3 Des: gaji 3.100.000 masuk (lewat UI) → Penyesuaian wajib (R-06) =====
        // hitung KebutuhanStandar Des: 45.000×30 + 10.000×30 + 480.000 + 200.000 + 150.000 + 40.000 (Galon)
        //   + 120.000 + 390.000 + 160.000 = 3.190.000 > 3.100.000
        click("Gaji sudah masuk")
        rule.waitText("Nominal gaji")
        repeat(7) { rule.onNodeWithContentDescription("Hapus angka").performClick() }
        typeAmount(3_100_000)
        rule.onAllNodesWithText("Desember")[0].performClick()
        click("Lanjut")
        rule.waitText("Penyesuaian")
        checkScreen("3des-penyesuaian")
        click("Lanjut")
        rule.waitText("Baru") // Galon ditandai Baru di preview split (R-95)
        checkScreen("3des-preview-split")
        click("Konfirmasi")
        click("Selesai")
        rule.waitText("Talangan Rp 500.000 dikembalikan", substring = true)
        l = ledger()
        val m = l.months.getValue(dec)
        assertNull(l.talangan)
        val alloc = m.allocation.associateBy { it.categoryId }
        assertEquals(120_000, alloc.getValue(Defaults.ID_IURAN_MESS).monthlyAmount) // TETAP tidak disentuh
        assertEquals(390_000, alloc.getValue(Defaults.ID_AI).monthlyAmount)
        assertEquals(160_000, alloc.getValue(Defaults.ID_NABUNG).monthlyAmount) // Nabung dipotong terakhir saja
        assertTrue(app.catatuang.engine.kebutuhanStandar(m.allocation, ready().input.categories) <= 3_100_000)
        assertEquals(3_100_000 - m.kebutuhan + 40_000, m.sakuSisaAwal) // R-12
        assertEquals(660_000, l.tabungan) // 500.000 kembali utuh + Nabung rutin 160.000 (R-07)

        // ===== Desember: catat pas jatah tiap hari, lalu belanja besar 600.000 (Saku jadi minus) =====
        val jMakan = alloc.getValue(Defaults.ID_MAKAN).dailyAmount!!
        val jBuah = alloc.getValue(Defaults.ID_BUAH).dailyAmount!!
        val jWeekend = m.weekendAllowance
        runBlocking { env.repo.payFixed(dec, app.catatuang.engine.fixedPaymentTx(Defaults.ID_IURAN_MESS, 120_000, d(2026, 12, 3)), 120_000) }
        // 1–2 Des sudah dicatat 45.000/10.000 sebelum Penyesuaian memotong jatah → selisihnya jadi hutang/hemat.
        for (day in 3..31) {
            val date = d(2026, 12, day)
            if (day > 3) goTo(date)
            spend(date, Defaults.ID_MAKAN, jMakan); spend(date, Defaults.ID_BUAH, jBuah)
            if (date.dayOfWeek == java.time.DayOfWeek.SATURDAY) spend(date, Defaults.ID_TRANSPORT, jWeekend)
            if (day == 10) {
                spend(date, Defaults.ID_PROTEIN, alloc.getValue(Defaults.ID_PROTEIN).monthlyAmount)
                spend(date, galon.id, alloc.getValue(galon.id).monthlyAmount)
                spend(date, Defaults.ID_LAIN, 600_000)
            }
        }
        goTo(d(2027, 1, 1))
        l = ledger()
        val decEnd = l.months.getValue(dec)
        // hitung: semua pos pas kecuali Lain (600.000 − budget); sisa pembagian Transport dicairkan (R-17).
        //   1–2 Des dicatat 45.000/10.000 sebelum jatah dipotong → selisihnya jadi hutang per pos (R-20/R-22), Saku tidak kena.
        val lainBudget = alloc.getValue(Defaults.ID_LAIN).monthlyAmount
        val trRemainder = decEnd.transportBudget - 4 * jWeekend
        val hutangMakan = 2 * maxOf(0L, 45_000 - jMakan)
        val hutangBuah = 2 * maxOf(0L, 10_000 - jBuah)
        val hutangDes = hutangMakan + hutangBuah
        // println("DES jMakan=$jMakan jBuah=$jBuah J=$jWeekend lain=$lainBudget sakuAwal=${m.sakuSisaAwal} tr=$trRemainder saku=${decEnd.sakuSisa}")
        assertTrue("Saku Desember harus minus", decEnd.sakuSisa < 0)
        assertEquals(m.sakuSisaAwal - (600_000 - lainBudget) + trRemainder, decEnd.sakuSisa)
        assertEquals(hutangMakan, l.hutang[Defaults.ID_MAKAN] ?: 0L)
        assertEquals(hutangBuah, l.hutang[Defaults.ID_BUAH] ?: 0L)
        val shortfall = -decEnd.sakuSisa

        // ===== Jumat 1 Jan: Tutup Buku Desember → Saku minus WAJIB ditutup dari Tabungan (R-72) → BONCOS =====
        rule.waitText("Tutup Buku Desember 2026") // beda tahun → nama bulan diberi tahun
        click("Tidak ada, lanjut")
        click("Lanjut") // verdict
        click("Lewati")
        rule.waitText("Hutang", substring = true)
        click("Bawa sisanya ke bulan baru") // hutang dibawa (default, R-24)
        click("Lanjut") // tagihan lunas
        rule.waitText("Saku Sisa Desember 2026 minus")
        checkScreen("1jan-wajib-tutup")
        holdToConfirm("Tahan 3 detik · tutup")
        awaitData { ledger().months.getValue(dec).sakuSisa == 0L }
        click("Tutup buku Desember 2026")
        click("Lanjut")
        rule.waitText("BONCOS", substring = true)
        click("Selesai")
        rule.waitText("Halo, Ko")
        l = ledger()
        assertEquals(VerdictKind.BONCOS, l.months.getValue(dec).verdict.kind)
        assertEquals(shortfall, l.months.getValue(dec).verdict.amount)
        assertEquals(660_000 - shortfall, l.tabungan)
        assertEquals(1_000_000, l.danaDarurat)

        // ===== Januari 2027: gaji tidak pernah masuk =====
        runBlocking { env.repo.payFixed(jan, app.catatuang.engine.fixedPaymentTx(Defaults.ID_AI, 390_000, d(2027, 1, 1)), 390_000) }
        runBlocking { env.repo.payFixed(jan, app.catatuang.engine.fixedPaymentTx(Defaults.ID_IURAN_MESS, 120_000, d(2027, 1, 1)), 120_000) }
        goTo(d(2027, 1, 5)); spend(d(2027, 1, 5), Defaults.ID_MAKAN, 300_000)
        goTo(d(2027, 1, 20)); spend(d(2027, 1, 20), Defaults.ID_LAIN, 100_000)
        l = ledger()
        assertEquals(910_000, l.talangan!!.amount)
        assertTrue(l.months.getValue(jan).nabungRutin == 0L) // R-07: tanpa gaji tidak ada Nabung

        // ===== Senin 1 Feb: Tutup Buku Januari → "Bulan ini tanpa gaji" =====
        goTo(d(2027, 2, 1))
        rule.waitText("Tutup Buku Januari")
        click("Tidak ada, lanjut")
        rule.waitText("Januari belum punya gaji.")
        click("Bulan ini tanpa gaji")
        rule.waitText("Ditandai tanpa gaji", substring = true)
        l = ledger()
        // hitung 6.10 langkah 1 (T-40): bawaan 0 − hutang masuk Des + 0 − pengeluaran riil 910.000
        val janSum = l.months.getValue(jan)
        assertEquals(-(hutangDes) - 910_000, janSum.sakuSisa)
        assertEquals(0L, l.hutang[Defaults.ID_MAKAN] ?: 0L) // semua hutang harian di-nol-kan
        click("Lanjut")
        rule.waitText("TANPA GAJI", substring = true)
        click("Lanjut")
        click("Lewati")
        click("Lanjut")
        click("Lanjut")
        rule.waitText("Saku Sisa Januari minus")
        rule.waitText("Ini dana darurat terakhir lu.") // Tabungan tidak cukup → Dana Darurat ikut
        checkScreen("1feb-tanpa-gaji")
        holdToConfirm("Tahan 3 detik · tutup")
        awaitData { ledger().months.getValue(jan).sakuSisa == 0L }
        click("Tutup buku Januari")
        click("Lanjut")
        rule.waitText("TANPA GAJI", substring = true)
        click("Selesai")
        l = ledger()
        val tabBefore = 660_000 - shortfall
        val needed = hutangDes + 910_000
        assertEquals(VerdictKind.TANPA_GAJI, l.months.getValue(jan).verdict.kind)
        assertEquals(0, l.tabungan)
        assertEquals(1_000_000 - (needed - tabBefore), l.danaDarurat)
        val w = ready().input.transactions.filter { it.type == TxType.SAVING_WITHDRAW && it.closingOf == jan }
        assertTrue(w.isNotEmpty() && w.all { it.reason == WithdrawReason.TANPA_GAJI }) // bukan BONCOS (T-28)
        checkNoMoneyLeak()
    }

    /** Alur kecil harian: Urungkan (R-62), anti-typo (R-65), dan input lewat tengah malam (R-61). */
    @Test fun everydayUndoTypoAndMidnight() {
        env = TestEnv(ctx, day(11, 12))
        env.onboard(day(11, 1), 2_000_000)
        env.salary(day(10, 31), nov)
        // 6 catatan Makan 45.000 sebelumnya → median 45.000 (R-65 butuh minimal 5 data).
        (5..10).forEach { spend(day(11, it), Defaults.ID_MAKAN, 45_000) }
        env.skipAutoClosing()
        env.lock.markUnlocked()
        rule.setContent { CatatUangTheme { CatatRoot(env.repo, env.settings, env.lock, env.clock) } }
        rule.waitText("Halo, Ko")

        // R-62: simpan lalu Urungkan → transaksi hilang, saldo kembali.
        val before = ready().input.transactions.size
        uiSpend("Makan", 25_000)
        click("Urungkan")
        awaitData { ready().input.transactions.size == before }

        // R-65: 450.000 (> 3× median 45.000) → "Yakin Rp 450.000? Biasanya ±Rp 45.000." → Ubah, lalu Ya, simpan.
        settle()
        rule.onAllNodesWithText("Makan")[0].performScrollTo().performClick()
        rule.waitText("Simpan")
        dismissDayQuestion()
        typeAmount(450_000)
        click("Simpan · Rp 450.000")
        rule.waitText("Yakin Rp 450.000?")
        rule.waitText("Biasanya ±Rp 45.000.")
        rule.shot("journey-anti-typo", containing = "Yakin")
        click("Ubah")
        assertEquals(before, ready().input.transactions.size) // belum tersimpan
        click("Simpan · Rp 450.000")
        click("Ya, simpan")
        awaitData { ready().input.transactions.any { it.amount == 450_000L } }
        runBlocking { env.repo.deleteTransaction(ready().input.transactions.first { it.amount == 450_000L }.id) }
        awaitData { ready().input.transactions.none { it.amount == 450_000L } }
    }

    /** R-61: catat jam 00:30 → wajib tanya, default Kemarin → tanggal kemarin & slot Malam (R-26). */
    @Test fun midnightEntryGoesToYesterday() {
        env = TestEnv(ctx, day(11, 12))
        env.onboard(day(11, 1), 2_000_000)
        env.salary(day(10, 31), nov)
        val r = ready()
        val vm = app.catatuang.feature.common.LedgerViewModel(env.repo)
        val makan = r.input.categories.first { it.id == Defaults.ID_MAKAN }
        rule.setContent {
            CatatUangTheme {
                androidx.compose.material3.Surface {
                    app.catatuang.feature.input.InputContent(makan, r, vm, onSaved = {}, nowTime = { java.time.LocalTime.of(0, 30) })
                }
            }
        }
        rule.waitText("Untuk hari ini atau kemarin?")
        rule.shot("journey-tengah-malam", containing = "kemarin")
        click("Kemarin")
        typeAmount(30_000)
        click("Simpan · Rp 30.000")
        awaitData { ready().input.transactions.any { it.amount == 30_000L } }
        val t = ready().input.transactions.single { it.amount == 30_000L }
        assertEquals(day(11, 11), t.date)
        assertEquals(Slot.MALAM, t.slot)
    }

    private fun app.catatuang.engine.LedgerState.input() = ready().input

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTextReplacementSafe(text: String) {
        performTextClearance()
        performTextInput(text)
    }
}
