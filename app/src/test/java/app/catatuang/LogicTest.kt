package app.catatuang

import app.catatuang.engine.Defaults
import app.catatuang.engine.LedgerInput
import app.catatuang.engine.Onboarding
import app.catatuang.engine.Slot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.computeLedger
import app.catatuang.engine.previewImpact
import app.catatuang.feature.history.cashSign
import app.catatuang.feature.history.historyGroups
import app.catatuang.feature.home.buildHomeUi
import app.catatuang.feature.input.appendDigits
import app.catatuang.feature.input.backspace
import app.catatuang.feature.input.expenseDraft
import app.catatuang.feature.input.feedbackText
import app.catatuang.feature.input.previewLines
import app.catatuang.security.PinAttempts
import app.catatuang.security.PinHasher
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.fullDate
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.rpShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LogicTest {
    private val cats = Defaults.categories
    private val makan = cats.first { it.key == "makan" }
    private val transport = cats.first { it.key == "transport" }
    private val lain = cats.first { it.key == "lain" }

    private fun input(vararg txs: Tx) = LedgerInput(
        cats, Onboarding(LocalDate.of(2026, 10, 31), 0, 500_000, 1_000_000),
        listOf(Tx(1, LocalDate.of(2026, 10, 31), TxType.SALARY, 3_300_000, refYearMonth = YearMonth.of(2026, 11))) + txs,
    )

    @Test fun formatRupiah() {
        assertEquals("Rp 45.000", rp(45_000))
        assertEquals("−Rp 15.000", rp(-15_000))
        assertEquals("Rp 3.300.000", rp(3_300_000))
        assertEquals("Rp 45rb", rpShort(45_000))
        assertEquals("Rp 1,05 jt", rpShort(1_050_000))
        assertEquals("Rp 1,5 jt", rpShort(1_500_000))
        assertEquals("Rp 2 jt", rpShort(2_000_000))
        assertEquals("Senin, 28 September", fullDate(LocalDate.of(2026, 9, 28)))
    }

    @Test fun numpad() {
        assertEquals(25_000, appendDigits(appendDigits(0, "25"), "000"))
        assertEquals(0, appendDigits(0, "000"))
        assertEquals(2_500, backspace(25_000))
        assertEquals("maksimal 12 digit", 999_999_999_999, appendDigits(999_999_999_999, "1"))
    }

    @Test fun pinHashAndLockout() {
        val stored = PinHasher.hash("4821")
        assertTrue(PinHasher.verify("4821", stored))
        assertFalse(PinHasher.verify("4822", stored))
        assertNotEquals("salt acak", stored.hash, PinHasher.hash("4821").hash)
        assertFalse(stored.hash.contains("4821"))
        var now = 0L
        val a = PinAttempts { now }
        repeat(4) { a.onFailure() }
        assertTrue(a.canTry())
        a.onFailure()
        assertEquals("5× salah → jeda 30 detik", 30_000, a.remainingLockMillis())
        now += 30_000
        assertTrue(a.canTry())
        repeat(5) { a.onFailure() }
        assertEquals("berlipat dua", 60_000, a.remainingLockMillis())
    }

    @Test fun inputPreviewAndFeedback() {
        val inp = input(
            Tx(2, LocalDate.of(2026, 11, 2), TxType.EXPENSE, 65_000, categoryId = makan.id, createdAt = 2),
            Tx(3, LocalDate.of(2026, 11, 3), TxType.EXPENSE, 35_000, categoryId = makan.id, createdAt = 3),
        )
        val today = LocalDate.of(2026, 11, 3)
        val draft = expenseDraft(makan, 25_000, today, Slot.MALAM, null)
        val impact = previewImpact(inp, today, draft)
        val lines = previewLines(makan, impact, today, today)
        assertEquals("Lebih Rp 10.000 dari jatah — hutang makan jadi Rp 25.000", lines.first().first)
        val (fb, tone) = feedbackText(makan, 25_000, impact, today, today)
        assertEquals("Makan +Rp 25.000 · Hari ini 60rb / 50rb · Hutang Rp 25.000", fb)
        assertEquals(Tone.DANGER, tone)

        val wed = LocalDate.of(2026, 11, 4)
        val trip = previewImpact(inp, wed, expenseDraft(transport, 50_000, wed, null, null))
        assertEquals("Trip tambahan — diambil dari Saku Sisa", previewLines(transport, trip, wed, wed).first().first)

        val nov1 = LocalDate.of(2026, 11, 1)
        val over = input(Tx(4, nov1, TxType.EXPENSE, 150_000, categoryId = lain.id, createdAt = 4))
        val stock = previewImpact(over, nov1, expenseDraft(lain, 30_000, nov1, null, null))
        val stockLines = previewLines(lain, stock, nov1, nov1).map { it.first }
        assertEquals("Melebihi budget Rp 30.000 — diambil dari Saku Sisa", stockLines[0])
        assertTrue(stockLines.any { it.startsWith("Saku Sisa minus") })
    }

    @Test fun homeTiles() {
        val fri = LocalDate.of(2026, 11, 6)
        val inp = input(Tx(2, LocalDate.of(2026, 11, 5), TxType.EXPENSE, 210_000, categoryId = makan.id, createdAt = 2))
        val ui = buildHomeUi("Ko", inp, computeLedger(inp, fri), fri)
        assertEquals("Jumat: Transport di posisi pertama (R-33)", "transport", ui.tiles.first().key)
        assertEquals("lain", ui.tiles.last().key)
        assertTrue(ui.tiles.last().wide)
        val m = ui.tiles.first { it.key == "makan" }
        assertTrue("R-27", m.badge!!.startsWith("Hutang besar"))
        assertEquals("Akhir pekan ini: Rp 0 / Rp 120rb", ui.tiles.first().status)
        assertEquals("Jumat, 6 November", ui.date)
        assertTrue(ui.hero.chips.any { it.label == "Hutang harian" })
        assertEquals("25 hari lagi", ui.hero.chips.last().value)
        val wed = LocalDate.of(2026, 11, 4)
        assertEquals("makan", buildHomeUi("Ko", inp, computeLedger(inp, wed), wed).tiles.first().key)
    }

    @Test fun historyGrouping() {
        val inp = input(
            Tx(2, LocalDate.of(2026, 10, 30), TxType.EXPENSE, 60_000, categoryId = transport.id, createdAt = 2),
            Tx(3, LocalDate.of(2026, 11, 2), TxType.EXPENSE, 40_000, categoryId = makan.id, createdAt = 3),
            Tx(4, LocalDate.of(2026, 11, 2), TxType.REFUND, 5_000, categoryId = makan.id, createdAt = 4),
        )
        val nov = historyGroups(inp.transactions, cats, YearMonth.of(2026, 11), null)
        assertEquals(listOf(LocalDate.of(2026, 11, 2)), nov.map { it.date })
        assertEquals(35_000, nov.first().spent)
        val oct = historyGroups(inp.transactions, cats, YearMonth.of(2026, 10), transport.id)
        assertEquals("Transport Jumat 30 Okt milik akhir pekan Sabtu 31 Okt (Oktober)", 1, oct.size)
        assertEquals(-60_000, cashSign(inp.transactions[1]))
    }
}
