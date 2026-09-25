package app.catatuang

import app.catatuang.engine.Defaults
import app.catatuang.engine.Destination
import app.catatuang.engine.IncomeKind
import app.catatuang.engine.LedgerInput
import app.catatuang.engine.Onboarding
import app.catatuang.engine.Pot
import app.catatuang.engine.ReserveReason
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.computeLedger
import app.catatuang.engine.planSplit
import app.catatuang.engine.templateOf
import app.catatuang.feature.fixed.fixedDiffText
import app.catatuang.feature.fixed.unpaidFixed
import app.catatuang.feature.history.txTitle
import app.catatuang.feature.home.buildHomeUi
import app.catatuang.feature.income.IncomeChoice
import app.catatuang.feature.income.buildIncome
import app.catatuang.feature.income.destinationOptions
import app.catatuang.feature.salary.lastSalaryAmount
import app.catatuang.feature.salary.reasonText
import app.catatuang.feature.salary.salaryFeedback
import app.catatuang.feature.salary.splitLineText
import app.catatuang.feature.salary.targetOptions
import app.catatuang.feature.savings.potDelta
import app.catatuang.ui.components.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class Phase3LogicTest {
    private val cats = Defaults.categories
    private val oct = YearMonth.of(2026, 10)

    @Test fun splitPreviewTexts() {
        val plan = planSplit(3_300_000, oct, templateOf(cats, oct), cats, bawaan = null)
        val texts = plan.lines.map { l -> splitLineText(l, cats.first { it.id == l.categoryId }) }
        assertEquals("Makan · 50.000 × 31 hari = 1.550.000", texts[0])
        assertTrue(texts.any { it == "Transport OE · 480.000 (120.000 × 4 akhir pekan)" })
        assertTrue(texts.any { it == "AI · 390.000 (tetap)" })
        assertTrue(texts.any { it == "Nabung · 160.000 → Tabungan" })
        val reasons = plan.reasons.map { reasonText(it, cats) }
        assertEquals(listOf("31 hari: +60rb (Makan & Buah)", "5 kali Sabtu: +120rb"), reasons)
        assertEquals("Sisa gaji & bawaan: −40rb", reasonText(ReserveReason.SalaryAndCarry(40_000), cats))
    }

    @Test fun targetMonthAndFeedback() {
        val start = YearMonth.of(2026, 9)
        assertEquals(listOf(YearMonth.of(2026, 9), oct), targetOptions(LocalDate.of(2026, 9, 29), start, emptySet()))
        assertEquals(listOf(oct), targetOptions(LocalDate.of(2026, 9, 29), start, setOf(YearMonth.of(2026, 9))))
        assertEquals("Gaji Oktober tersimpan · aktif 1 Okt", salaryFeedback(oct, LocalDate.of(2026, 10, 1), YearMonth.of(2026, 9), 0))
        assertEquals("Talangan Rp 90.000 dikembalikan · Gaji Oktober tersimpan", salaryFeedback(oct, LocalDate.of(2026, 10, 3), oct, 90_000))
        val salary = Tx(1, LocalDate.of(2026, 9, 29), TxType.SALARY, 3_400_000, refYearMonth = oct)
        assertEquals(3_400_000, lastSalaryAmount(listOf(salary), 3_300_000))
        assertEquals(3_300_000, lastSalaryAmount(emptyList(), 3_300_000))
        assertEquals("Gaji Oktober", txTitle(salary, cats))
        assertEquals("Gaji tambahan Oktober", txTitle(salary.copy(incomeKind = IncomeKind.LAINNYA), cats))
    }

    @Test fun incomeChoices() {
        val date = LocalDate.of(2026, 11, 5)
        val thr = buildIncome(IncomeChoice.THR_BONUS, 3_000_000, date, "POT:TABUNGAN", null, "")!!
        assertEquals(Destination.POT, thr.destination)
        assertEquals(Pot.TABUNGAN, thr.pot)
        assertEquals("THR / Bonus → Tabungan", txTitle(thr, cats))
        val protein = buildIncome(IncomeChoice.PEMBERIAN, 50_000, date, "CAT:${Defaults.ID_PROTEIN}", null, null)!!
        assertEquals(Defaults.ID_PROTEIN, protein.categoryId)
        assertNull("Pengembalian wajib pilih pos", buildIncome(IncomeChoice.PENGEMBALIAN, 60_000, date, "SAKU", null, null))
        val refund = buildIncome(IncomeChoice.PENGEMBALIAN, 60_000, date, null, Defaults.ID_PROTEIN, null)!!
        assertEquals(TxType.REFUND, refund.type)
        // 6.8: Transport bukan tujuan tambah budget.
        assertTrue(destinationOptions(cats, oct).none { it.first == "CAT:${Defaults.ID_TRANSPORT}" })
        assertEquals(3_000_000L, potDelta(thr, Pot.TABUNGAN))
        assertNull(potDelta(thr, Pot.DANA_DARURAT))
    }

    @Test fun fixedObligationsOnHome() {
        val start = LocalDate.of(2026, 10, 31)
        val salary = Tx(1, start, TxType.SALARY, 3_300_000, refYearMonth = YearMonth.of(2026, 11))
        val input = LedgerInput(cats, Onboarding(start, 0, 500_000, 1_000_000), listOf(salary))
        val today = LocalDate.of(2026, 11, 2)
        val state = computeLedger(input, today)
        val due = unpaidFixed(cats, state, today)
        assertEquals(listOf("Iuran Mess", "AI"), due.map { it.name })
        assertEquals("Lewat jatuh tempo 1 Nov", due[0].status)
        val paid = input.copy(transactions = input.transactions + Tx(2, today, TxType.FIXED_PAYMENT, 405_000, categoryId = Defaults.ID_AI))
        assertEquals(listOf("Iuran Mess"), unpaidFixed(cats, computeLedger(paid, today), today).map { it.name })
        assertEquals(Tone.WARNING, fixedDiffText(390_000, 405_000).second)
        // Bulan onboarding: tidak ada kewajiban (D-19).
        assertTrue(unpaidFixed(cats, computeLedger(input, start), start).isEmpty())
    }

    @Test fun cadanganBannerOnFirstDay() {
        val start = LocalDate.of(2026, 9, 30)
        val salary = Tx(1, start, TxType.SALARY, 3_300_000, refYearMonth = oct)
        val input = LedgerInput(cats, Onboarding(start, 0, 500_000, 1_000_000), listOf(salary))
        val first = LocalDate.of(2026, 10, 1)
        val ui = buildHomeUi("Ko", input, computeLedger(input, first), first)
        assertEquals("Cadangan Oktober: Rp 180.000", ui.cadanganBanner!!.title)
        assertTrue(ui.cadanganBanner!!.subtitle.startsWith("31 hari: +60rb (Makan & Buah) · 5 kali Sabtu: +120rb"))
        assertNull(buildHomeUi("Ko", input, computeLedger(input, first), first, cadanganDismissed = "2026-10").cadanganBanner)
        val second = first.plusDays(1)
        assertNull(buildHomeUi("Ko", input, computeLedger(input, second), second).cadanganBanner)
    }
}
