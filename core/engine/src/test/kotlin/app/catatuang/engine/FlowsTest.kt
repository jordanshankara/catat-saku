package app.catatuang.engine

import app.catatuang.engine.Defaults.ID_AI
import app.catatuang.engine.Defaults.ID_LAIN
import app.catatuang.engine.Defaults.ID_PROTEIN
import app.catatuang.engine.Defaults.ID_TRANSPORT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Aksi Fase 3: Gajian, Pemasukan, Tabungan & Dana Darurat, Pos TETAP. */
class FlowsTest {
    private val cats = Defaults.categories

    private fun Scenario.addAll(txs: List<Tx>) = txs.forEach { add(it) }

    @Test @DisplayName("Gaji cepat 29 Sep: pending, Nabung rutin 1 Okt, split Oktober bawaan belum pasti")
    fun earlySalary() {
        val s = Scenario(d("2026-09-01"), cash = 1_500_000)
        val prep = prepareSalary(s.input(), 3_300_000, defaultTargetMonth(d("2026-09-29")))
        assertEquals(ym("2026-10"), prep.target)
        assertNull(prep.bawaan)
        assertEquals(180_000, prep.plan!!.targetCadangan)
        assertFalse(prep.plan!!.needsAdjustment)
        val txs = salaryTransactions(3_300_000, d("2026-09-29"), prep.target, prep.allocation, cats, extra = false)
        assertEquals(listOf(TxType.SALARY, TxType.SAVING_DEPOSIT), txs.map { it.type })
        assertEquals(d("2026-10-01"), txs[1].date)
        s.addAll(txs)
        val sep = s.at("2026-09-29")
        assertEquals(3_300_000, sep.saldoPending)
        assertEquals(500_000, sep.tabungan) // setoran bertanggal 1 Okt belum berlaku (D-18)
        val oct = s.at("2026-10-01")
        assertEquals(660_000, oct.tabungan)
        assertEquals(SalaryStatus.RECEIVED, oct.salaryStatus)
        assertTrue(hasSalary(s.input(), ym("2026-10")))
    }

    @Test @DisplayName("Bawaan diketahui setelah Tutup Buku bulan lalu")
    fun knownCarry() {
        val s = Scenario(d("2026-09-01"), cash = 1_500_000).apply {
            plan("2026-09") { copy(closed = true) }
            carry("2026-10-01", "2026-09", 100_000)
        }
        assertEquals(100_000, knownBawaan(s.input(), ym("2026-10")))
        assertEquals(80_000, prepareSalary(s.input(), 3_300_000, ym("2026-10")).plan!!.targetCadangan)
    }

    @Test @DisplayName("Gaji bulan onboarding: tanpa split & tanpa Nabung, Saku Sisa + penuh (8.12)")
    fun onboardingMonthSalary() {
        val s = Scenario(d("2026-09-25"), cash = 400_000)
        val before = s.at("2026-09-25").sakuSisa
        val prep = prepareSalary(s.input(), 1_000_000, ym("2026-09"))
        assertTrue(prep.onboardingMonth)
        assertNull(prep.plan)
        s.addAll(salaryTransactions(1_000_000, d("2026-09-25"), ym("2026-09"), null, cats, extra = false))
        assertEquals(before + 1_000_000, s.at("2026-09-25").sakuSisa)
        assertEquals(500_000, s.at("2026-09-25").tabungan)
    }

    @Test @DisplayName("R-05 gaji tambahan/rapel: tanpa Nabung, menambah Saku Sisa bulan target")
    fun extraSalary() {
        val s = Scenario.funded("2026-11")
        val before = s.at("2026-11-10")
        s.addAll(salaryTransactions(500_000, d("2026-11-10"), ym("2026-11"), before.current!!.allocation, cats, extra = true))
        val after = s.at("2026-11-10")
        assertEquals(before.sakuSisa + 500_000, after.sakuSisa)
        assertEquals(before.tabungan, after.tabungan)
        assertEquals(500_000, after.current!!.gajiTambahan)
    }

    @Test @DisplayName("R-06 alokasi hasil Penyesuaian hanya boleh bila KebutuhanStandar ≤ gaji")
    fun fits() {
        val t = templateOf(cats, ym("2026-10"))
        assertFalse(allocationFits(3_280_000, t, cats))
        assertTrue(allocationFits(3_280_000, proposeAdjustment(3_280_000, t, cats), cats))
    }

    @Test @DisplayName("Pemasukan: tujuan Saku, kantong, dan tambah budget STOK")
    fun income() {
        val s = Scenario.funded("2026-11")
        val base = s.at("2026-11-05")
        s.add(incomeTx(IncomeKind.PEMBERIAN, 100_000, d("2026-11-05"), Destination.SAKU))
        s.add(incomeTx(IncomeKind.SAMPINGAN, 50_000, d("2026-11-05"), Destination.POT, Pot.DANA_DARURAT))
        s.add(incomeTx(IncomeKind.LAINNYA, 40_000, d("2026-11-05"), Destination.CATEGORY, categoryId = ID_PROTEIN))
        val st = s.at("2026-11-05")
        assertEquals(base.sakuSisa + 100_000, st.sakuSisa)
        assertEquals(base.danaDarurat + 50_000, st.danaDarurat)
        assertEquals(240_000, st.stock.first { it.categoryId == ID_PROTEIN }.budget)
        assertEquals(listOf(ID_PROTEIN, ID_LAIN), incomeBudgetCategories(cats, ym("2026-11")).map { it.id })
        assertTrue(refundCategories(cats, ym("2026-11")).any { it.id == ID_TRANSPORT })
    }

    @Test @DisplayName("Setor & ambil manual: Saku Sisa ∓, RENCANA tidak BONCOS, DARURAT BONCOS")
    fun pots() {
        val s = Scenario.funded("2026-11")
        val base = s.at("2026-11-05")
        s.add(depositTx(Pot.DANA_DARURAT, 30_000, d("2026-11-05")))
        s.add(withdrawTx(Pot.TABUNGAN, 100_000, WithdrawReason.RENCANA, d("2026-11-05")))
        val st = s.at("2026-11-05")
        assertEquals(base.sakuSisa - 30_000 + 100_000, st.sakuSisa)
        assertEquals(base.tabungan - 100_000, st.tabungan)
        assertTrue(st.current!!.verdict.kind != VerdictKind.BONCOS)
        s.add(withdrawTx(Pot.TABUNGAN, 10_000, WithdrawReason.DARURAT, d("2026-11-05")))
        assertEquals(VerdictKind.BONCOS, s.at("2026-11-05").current!!.verdict.kind)
    }

    @Test @DisplayName("R-52 Tutup sekarang: Saku −40rb ditutup dari Tabungan (DARURAT)")
    fun coverNow() {
        val txs = coverNowTransactions(-40_000, 30_000, 1_000_000, d("2026-11-05"))
        assertEquals(listOf(30_000L, 10_000L), txs.map { it.amount })
        assertEquals(listOf(Pot.TABUNGAN, Pot.DANA_DARURAT), txs.map { it.pot })
        assertTrue(txs.all { it.reason == WithdrawReason.DARURAT })
        assertTrue(coverNowTransactions(0, 30_000, 0, d("2026-11-05")).isEmpty())

        val s = Scenario.funded("2026-11").apply { expense("2026-11-01", ID_LAIN, 190_000) }
        val st = s.at("2026-11-01")
        assertEquals(-40_000, st.sakuSisa)
        s.addAll(coverNowTransactions(st.sakuSisa, st.tabungan, st.danaDarurat, d("2026-11-01")))
        val after = s.at("2026-11-01")
        assertEquals(0, after.sakuSisa)
        assertEquals(460_000, after.tabungan)
    }

    @Test @DisplayName("R-59 lewat helper: Sisa bebas kembali 0, Dana Darurat tidak tersentuh")
    fun useSavings() {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-01", ID_LAIN, 170_000) }
        val draft = Tx(0, d("2026-11-01"), TxType.EXPENSE, 30_000, categoryId = ID_LAIN, createdAt = Long.MAX_VALUE)
        val impact = previewImpact(s.input(), d("2026-11-01"), draft)
        assertEquals(50_000, impact.useSavingsAmount) // sudah minus 20rb sebelumnya
        val txs = useSavingsTransactions(impact, draft)!!
        assertEquals(WithdrawReason.RENCANA, txs[0].reason)
        s.addAll(txs.map { it.copy(createdAt = 0) })
        val st = s.at("2026-11-01")
        assertEquals(0, st.sisaBebas)
        assertEquals(1_000_000, st.danaDarurat)
        assertTrue(st.current!!.verdict.kind != VerdictKind.BONCOS)
    }

    @Test @DisplayName("R-59 Transport Minggu 1 Nov milik Oktober: pengambilan dicatat di Oktober")
    fun useSavingsWeekendAcrossMonth() {
        val draft = Tx(0, d("2026-11-01"), TxType.EXPENSE, 60_000, categoryId = ID_TRANSPORT)
        val impact = Impact(
            ym("2026-10"), 0, 0, 0, -60_000, null, null, null, null, null, 0, 60_000, 120_000, null,
            setOf(ImpactWarning.USES_RESERVE), 60_000, true,
        )
        assertEquals(d("2026-10-31"), useSavingsTransactions(impact, draft)!![0].date)
        assertEquals(d("2027-05-01"), useSavingsTransactions(impact.copy(month = ym("2027-05")), draft.copy(date = d("2027-04-30")))!![0].date)
        assertNull(useSavingsTransactions(impact.copy(savingsCanCover = false), draft))
    }

    @Test @DisplayName("R-41/R-42 bayar AI 405rb → Saku −15rb; kewajiban dari alokasi")
    fun fixedPayment() {
        val s = Scenario.funded("2026-11")
        val base = s.at("2026-11-01").sakuSisa
        s.add(fixedPaymentTx(ID_AI, 405_000, d("2026-11-01")))
        val st = s.at("2026-11-01")
        assertEquals(base - 15_000, st.sakuSisa)
        assertTrue(st.current!!.fixed.first { it.categoryId == ID_AI }.isPaid)
        val seeds = fixedObligationsFor(ym("2026-11"), st.current!!.allocation, cats)
        assertEquals(setOf(Defaults.ID_IURAN_MESS, ID_AI), seeds.map { it.categoryId }.toSet())
    }
}
