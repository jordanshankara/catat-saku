package app.catatuang.engine

import app.catatuang.engine.Defaults.ID_AI
import app.catatuang.engine.Defaults.ID_MAKAN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Tutup Buku (6.10) lewat helper transaksi langkah demi langkah. */
class ClosingTest {
    private fun Scenario.addAll(txs: List<Tx>) = txs.forEach { add(it) }

    @Test @DisplayName("Tutup Buku tertunda: bulan tertua yang sudah berakhir")
    fun pending() {
        val s = Scenario.funded("2026-11").apply { salary("2026-11-30", "2026-12") }
        assertNull(pendingClosing(s.at("2026-10-31")))
        assertEquals(ym("2026-10"), pendingClosing(s.at("2026-11-30"))!!.month) // bulan onboarding ikut ditutup
        s.plan("2026-10") { copy(closed = true) }
        assertEquals(ym("2026-11"), pendingClosing(s.at("2026-12-01"))!!.month)
    }

    @Test @DisplayName("Langkah 4: lunasi hutang akhir bulan dari Saku, dibatasi hutang sekarang")
    fun debtPayoff() {
        val s = Scenario.funded("2026-11").apply {
            salary("2026-11-30", "2026-12")
            expense("2026-11-30", ID_MAKAN, 70_000) // hutang 20rb
        }
        val st = s.at("2026-12-01")
        assertEquals(mapOf(ID_MAKAN to 20_000L), closingDebts(s.input(), st, ym("2026-11")))
        val nov = st.months.getValue(ym("2026-11"))
        s.add(debtPayoffTx(ym("2026-11"), ID_MAKAN, 20_000, d("2026-12-01")))
        val after = s.at("2026-12-01")
        val nov2 = after.months.getValue(ym("2026-11"))
        assertEquals(nov.sakuSebelumDistribusi - 20_000, nov2.sakuSebelumDistribusi)
        assertEquals(0, nov2.hutangDibawa)
        assertEquals(0L, after.hutang[ID_MAKAN])
        assertTrue(closingDebts(s.input(), after, ym("2026-11")).isEmpty())

        // 1 Des sudah hemat 15rb → hanya 5rb tersisa untuk dilunasi.
        val late = Scenario.funded("2026-11").apply {
            salary("2026-11-30", "2026-12")
            expense("2026-11-30", ID_MAKAN, 70_000)
            expense("2026-12-01", ID_MAKAN, 35_000)
        }
        assertEquals(mapOf(ID_MAKAN to 5_000L), closingDebts(late.input(), late.at("2026-12-02"), ym("2026-11")))
    }

    @Test @DisplayName("T-12 lewat helper: minus → DARURAT, BONCOS")
    fun mandatoryCover() {
        val s = Scenario.funded("2026-11").apply { spendExactly("2026-11") }
        s.add(reconcileTx(ym("2026-11"), 250_000, 210_000, d("2026-12-01"))!!)
        val st = s.at("2026-12-01")
        val nov = st.months.getValue(ym("2026-11"))
        assertEquals(-40_000, nov.sakuSisa)
        assertEquals(-40_000L, reconciledDiff(s.input(), ym("2026-11")))
        s.addAll(closingDistribution(ym("2026-11"), nov.sakuSisa, ClosingOption.TUTUP_WAJIB, d("2026-12-01"), st.tabungan, st.danaDarurat, noSalary = false))
        val after = s.at("2026-12-01").months.getValue(ym("2026-11"))
        assertEquals(Verdict(VerdictKind.BONCOS, 40_000), after.verdict)
        assertEquals(0, after.sakuSisa)
    }

    @Test @DisplayName("T-28 lewat helper: bulan tanpa gaji ditutup TANPA_GAJI")
    fun noSalaryCover() {
        val txs = closingDistribution(ym("2026-10"), -700_000, ClosingOption.TUTUP_WAJIB, d("2026-11-02"), 500_000, 1_000_000, noSalary = true)
        assertEquals(listOf(500_000L, 200_000L), txs.map { it.amount })
        assertTrue(txs.all { it.reason == WithdrawReason.TANPA_GAJI && it.closingOf == ym("2026-10") })
    }

    @Test @DisplayName("Langkah 6 positif: Tabungan, Dana Darurat, bawa, split")
    fun distribute() {
        fun base() = Scenario.funded("2026-11").apply {
            spendExactly("2026-11", skipBuahDays = 12)
            routineDeposit("2026-11-01", "2026-11")
        }
        val m = ym("2026-11")
        val date = d("2026-12-01")
        val bawa = base()
        bawa.addAll(closingDistribution(m, 120_000, ClosingOption.BAWA_KE_BULAN_DEPAN, date, 0, 0, false))
        val st = bawa.at("2026-12-01")
        assertEquals(0, st.months.getValue(m).sakuSisa)
        assertEquals(120_000, st.months.getValue(m.plusMonths(1)).bawaan)

        val split = base()
        split.addAll(closingDistribution(m, 120_000, ClosingOption.SPLIT, date, 0, 0, false, SplitTargets(50_000, 30_000, 40_000)))
        val sp = split.at("2026-12-01")
        assertEquals(Verdict(VerdictKind.BERHASIL_NABUNG, 160_000 + 80_000), sp.months.getValue(m).verdict)
        assertEquals(40_000, sp.months.getValue(m.plusMonths(1)).bawaan)
        assertThrows(IllegalArgumentException::class.java) {
            closingDistribution(m, 120_000, ClosingOption.SPLIT, date, 0, 0, false, SplitTargets(50_000, 0, 0))
        }
    }

    @Test @DisplayName("R-43 langkah 5: sudah dibayar di bulan lalu / tidak jadi")
    fun fixedAtClosing() {
        val s = Scenario.funded("2026-11").apply { salary("2026-11-30", "2026-12") }
        val base = s.at("2026-12-01").months.getValue(ym("2026-11")).sakuSisa
        s.add(closingFixedPaymentTx(ym("2026-11"), ID_AI, 400_000, d("2026-12-01")))
        s.plan("2026-11") { copy(cancelledFixed = setOf(Defaults.ID_IURAN_MESS)) }
        val nov = s.at("2026-12-01").months.getValue(ym("2026-11"))
        assertEquals(base - 10_000 + 120_000, nov.sakuSisa)
        assertTrue(nov.fixed.first { it.categoryId == ID_AI }.isPaid)
    }

    @Test @DisplayName("Snapshot & koreksi")
    fun snapshotAndCorrection() {
        val s = Scenario.funded("2026-11").apply { spendExactly("2026-11", skipBuahDays = 3) }
        val snap = closureSnapshot(s.at("2026-12-01").months.getValue(ym("2026-11")), reconciled = null)
        assertEquals("PAS_PASAN", snap.verdict)
        val c = correctionTx(ID_MAKAN, -20_000, d("2026-12-05"), "salah input")
        assertEquals(20_000, c.amount)
        assertEquals(-20_000L, c.signedAmount)
        assertThrows(IllegalArgumentException::class.java) { correctionTx(ID_MAKAN, 10_000, d("2026-12-05"), " ") }
    }
}
