package app.catatuang.engine

import app.catatuang.engine.Defaults.ID_AI
import app.catatuang.engine.Defaults.ID_BUAH
import app.catatuang.engine.Defaults.ID_LAIN
import app.catatuang.engine.Defaults.ID_MAKAN
import app.catatuang.engine.Defaults.ID_PROTEIN
import app.catatuang.engine.Defaults.ID_TRANSPORT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalTime

/** Test wajib CLAUDE.md bab 13. Nama test diawali ID-nya. */
class SpecTest {
    private val cats = Defaults.categories
    private fun template(month: String) = templateOf(cats, ym(month))

    @Test @DisplayName("T-01 Hutang Makan: 65rb, 40rb, 35rb")
    fun t01() {
        val s = Scenario.funded("2026-11")
        s.expense("2026-11-02", ID_MAKAN, 65_000) // Senin
        s.expense("2026-11-03", ID_MAKAN, 40_000) // Selasa
        s.expense("2026-11-04", ID_MAKAN, 35_000) // Rabu
        val log = s.at("2026-11-05").dailyLog.filter { it.categoryId == ID_MAKAN }.associateBy { it.date }
        with(log.getValue(d("2026-11-02"))) { assertEquals(15_000, debtAfter); assertEquals(0, toSaku) }
        with(log.getValue(d("2026-11-03"))) { assertEquals(5_000, debtAfter); assertEquals(0, toSaku) }
        with(log.getValue(d("2026-11-04"))) { assertEquals(0, debtAfter); assertEquals(10_000, toSaku) }
    }

    @Test @DisplayName("T-02 Oktober 2026, gaji 3.300.000")
    fun t02() {
        val p = planSplit(3_300_000, ym("2026-10"), template("2026-10"), cats, bawaan = 0)
        assertEquals(31, ym("2026-10").lengthOfMonth())
        assertEquals(5, countSaturdays(ym("2026-10")))
        assertEquals(3_360_000, p.kebutuhan)
        assertEquals(-60_000, p.sakuSisaAwal)
        assertEquals(120_000, p.tripTambahan)
        assertEquals(180_000, p.targetCadangan)
        assertFalse(p.needsAdjustment)
        assertTrue(p.reasons.contains(ReserveReason.ExtraDays(31, 60_000)))
        assertTrue(p.reasons.contains(ReserveReason.ExtraSaturdays(5, 120_000)))
    }

    @Test @DisplayName("T-03 November 2026, gaji 3.300.000")
    fun t03() {
        val p = planSplit(3_300_000, ym("2026-11"), template("2026-11"), cats, bawaan = 0)
        assertEquals(30, ym("2026-11").lengthOfMonth())
        assertEquals(4, countSaturdays(ym("2026-11")))
        assertEquals(3_300_000, p.kebutuhan)
        assertEquals(0, p.sakuSisaAwal)
        assertEquals(0, p.targetCadangan)
    }

    @Test @DisplayName("T-04 Februari 2027, gaji 3.300.000")
    fun t04() {
        val p = planSplit(3_300_000, ym("2027-02"), template("2027-02"), cats, bawaan = 0)
        assertEquals(28, ym("2027-02").lengthOfMonth())
        assertEquals(4, countSaturdays(ym("2027-02")))
        assertEquals(3_180_000, p.kebutuhan)
        assertEquals(120_000, p.sakuSisaAwal)
    }

    @Test @DisplayName("T-05 Oktober 2026, gaji 3.280.000 → Penyesuaian")
    fun t05() {
        val alloc = template("2026-10")
        val p = planSplit(3_280_000, ym("2026-10"), alloc, cats, bawaan = 0)
        assertEquals(3_300_000, p.kebutuhanStandar)
        assertTrue(p.needsAdjustment)
        assertNotNull(p.proposal)
        val proposal = p.proposal!!
        fun amount(list: List<AllocationLine>, id: Long) = list.first { it.categoryId == id }.monthlyAmount
        assertEquals(amount(alloc, Defaults.ID_IURAN_MESS), amount(proposal, Defaults.ID_IURAN_MESS))
        assertEquals(amount(alloc, ID_AI), amount(proposal, ID_AI))
        assertEquals(amount(alloc, Defaults.ID_NABUNG), amount(proposal, Defaults.ID_NABUNG), "Nabung dipotong terakhir")
        assertTrue(kebutuhanStandar(proposal, cats) <= 3_280_000)
        assertTrue(proposal.first { it.categoryId == ID_MAKAN }.dailyAmount!! < 50_000)
        proposal.forEach { assertEquals(0, (it.dailyAmount ?: 0) % 1000); assertEquals(0, it.monthlyAmount % 1000) }
    }

    @Test @DisplayName("T-06 Gaji diterima 29 Sep 2026 → pending sampai 1 Okt")
    fun t06() {
        assertEquals(ym("2026-10"), defaultTargetMonth(d("2026-09-29")))
        val base = Scenario(d("2026-09-01"), cash = 3_000_000)
        val withSalary = Scenario(d("2026-09-01"), cash = 3_000_000).apply { salary("2026-09-29", "2026-10") }
        val before = base.at("2026-09-29")
        val after = withSalary.at("2026-09-29")
        assertEquals(3_300_000, after.saldoPending)
        assertEquals(ym("2026-10"), after.pendingMonth)
        assertEquals(before.sakuSisa, after.sakuSisa, "Saku Sisa September tidak berubah")
        assertEquals(before.uangPegangan + 3_300_000, after.uangPegangan)
        val oct1 = withSalary.at("2026-10-01")
        assertEquals(SalaryStatus.RECEIVED, oct1.salaryStatus)
        assertEquals(0, oct1.saldoPending)
    }

    @Test @DisplayName("T-07 Split Oktober sebelum Tutup Buku September")
    fun t07() {
        val p = planSplit(3_300_000, ym("2026-10"), template("2026-10"), cats, bawaan = null)
        assertEquals(180_000, p.targetCadangan)
        assertTrue(p.bawaanUncertain)
        assertEquals(80_000, planSplit(3_300_000, ym("2026-10"), template("2026-10"), cats, bawaan = 100_000).targetCadangan)

        val s = Scenario.funded("2026-10")
        assertEquals(180_000, s.at("2026-10-01").targetCadangan)
        s.carry("2026-10-01", "2026-09", 100_000)
        assertEquals(80_000, s.at("2026-10-01").targetCadangan)
    }

    @Test @DisplayName("T-08 Hutang terbawa ke November")
    fun t08() {
        val s = Scenario.funded("2026-10").apply { salary("2026-10-30", "2026-11") }
        s.expense("2026-10-31", ID_MAKAN, 70_000)
        s.expense("2026-11-01", ID_MAKAN, 35_000)
        val log = s.at("2026-11-02").dailyLog.filter { it.categoryId == ID_MAKAN }.associateBy { it.date }
        assertEquals(20_000, log.getValue(d("2026-10-31")).debtAfter)
        assertEquals(5_000, log.getValue(d("2026-11-01")).debtAfter)
    }

    @Test @DisplayName("T-09 Mode Darurat: hutang 30rb, Saku 20rb, Tabungan 500rb")
    fun t09() {
        val s = Scenario.funded("2026-11")
        s.expense("2026-11-01", ID_MAKAN, 50_000); s.expense("2026-11-01", ID_BUAH, 10_000)
        s.expense("2026-11-02", ID_MAKAN, 80_000)
        s.income("2026-11-02", 10_000)
        val before = s.at("2026-11-03")
        assertEquals(20_000, before.sakuSisa)
        assertEquals(30_000, before.hutang[ID_MAKAN])

        val cover = coverShortfall(30_000, before.sakuSisa, before.tabungan, before.danaDarurat)
        assertEquals(Cover(20_000, 10_000, 0, 0), cover)
        s.withdraw("2026-11-03", Pot.TABUNGAN, cover.fromTabungan, WithdrawReason.DARURAT)
        s.payoff("2026-11-03", ID_MAKAN, 30_000)
        val after = s.at("2026-11-03")
        assertEquals(0, after.sakuSisa)
        assertEquals(490_000, after.tabungan)
        assertEquals(0, after.hutang[ID_MAKAN])
        assertEquals(Verdict(VerdictKind.BONCOS, 10_000), after.current!!.verdict)
    }

    @Test @DisplayName("T-10 Transport 5 akhir pekan Oktober 2026 @120rb (+ varian 60rb Sabtu 31 Okt)")
    fun t10() {
        fun scenario(fifth: Long?) = Scenario.funded("2026-10").apply {
            listOf("2026-10-03", "2026-10-10", "2026-10-17", "2026-10-24").forEach { expense(it, ID_TRANSPORT, 120_000) }
            if (fifth != null) expense("2026-10-31", ID_TRANSPORT, fifth)
        }
        val none = scenario(null).at("2026-10-31")
        val full = scenario(120_000).at("2026-10-31")
        val half = scenario(60_000).at("2026-10-31")
        assertEquals(120_000, none.reservasi)
        assertEquals(none.sakuSisa - 120_000, full.sakuSisa)
        assertEquals(0, full.reservasi)
        assertEquals(none.sisaBebas, full.sisaBebas)
        assertEquals(none.sakuSisa - 60_000, half.sakuSisa)
        assertEquals(60_000, half.reservasi)
        assertEquals(none.sisaBebas, half.sisaBebas)

        val closed = scenario(120_000).at("2026-11-02")
        val oct = closed.weekendLog.filter { it.window.owner == ym("2026-10") }
        assertEquals(listOf(0L, 0L, 0L, 0L), oct.filter { it.funded }.map { it.toSaku })
        assertEquals(0, closed.months.getValue(ym("2026-10")).reservasi)
    }

    @Test @DisplayName("T-11 Transport Sabtu 31 Okt + Minggu 1 Nov 2026")
    fun t11() {
        val s = Scenario.funded("2026-10")
        val sat = s.expense("2026-10-31", ID_TRANSPORT, 60_000)
        val sun = s.expense("2026-11-01", ID_TRANSPORT, 60_000)
        assertEquals(ym("2026-10"), accountingMonth(sat, cats))
        assertEquals(ym("2026-10"), accountingMonth(sun, cats))
        val oct = s.at("2026-11-01").months.getValue(ym("2026-10"))
        val fifth = oct.windows.last()
        assertEquals(5, fifth.index)
        assertEquals(120_000, fifth.used)
        assertFalse(oct.closable)
        assertTrue(s.at("2026-11-02").months.getValue(ym("2026-10")).closable)
    }

    @Test @DisplayName("T-12 Tutup Buku Saku Sisa −40rb → BONCOS 40rb")
    fun t12() {
        val s = Scenario.funded("2026-11").apply { spendExactly("2026-11") }
        s.tx("2026-12-01", TxType.UNRECORDED, 40_000) { copy(closingOf = ym("2026-11")) }
        val before = s.at("2026-12-01")
        val nov = before.months.getValue(ym("2026-11"))
        assertEquals(-40_000, nov.sakuSebelumDistribusi)
        assertEquals(listOf(ClosingOption.TUTUP_WAJIB), closingOptions(nov.sakuSebelumDistribusi, before.danaDarurat, 1_000_000))
        val cover = coverShortfall(-nov.sakuSebelumDistribusi, 0, before.tabungan, before.danaDarurat)
        assertEquals(Cover(0, 40_000, 0, 0), cover)
        s.withdraw("2026-12-01", Pot.TABUNGAN, 40_000, WithdrawReason.DARURAT, closingOf = "2026-11")
        val after = s.at("2026-12-01")
        assertEquals(460_000, after.tabungan)
        assertEquals(Verdict(VerdictKind.BONCOS, 40_000), after.months.getValue(ym("2026-11")).verdict)
        assertEquals(0, after.months.getValue(ym("2026-11")).sakuSisa)
    }

    @Test @DisplayName("T-13 Saku Sisa akhir 30rb tanpa hutang → PAS-PASAN")
    fun t13() {
        val s = Scenario.funded("2026-11").apply { spendExactly("2026-11", skipBuahDays = 3) }
        val nov = s.at("2026-12-01").months.getValue(ym("2026-11"))
        assertEquals(30_000, nov.sakuSebelumDistribusi)
        assertEquals(0, nov.hutangDibawa)
        assertEquals(VerdictKind.PAS_PASAN, nov.verdict.kind)
    }

    @Test @DisplayName("T-14 Saku Sisa akhir 120rb → BERHASIL NABUNG 160rb + sisa dipindah")
    fun t14() {
        val s = Scenario.funded("2026-11").apply {
            spendExactly("2026-11", skipBuahDays = 12)
            routineDeposit("2026-11-01", "2026-11")
        }
        assertEquals(120_000, s.at("2026-12-01").months.getValue(ym("2026-11")).sakuSebelumDistribusi)
        s.deposit("2026-12-01", Pot.TABUNGAN, 120_000, closingOf = "2026-11")
        val nov = s.at("2026-12-01").months.getValue(ym("2026-11"))
        assertEquals(Verdict(VerdictKind.BERHASIL_NABUNG, 280_000), nov.verdict)
        assertEquals(0, nov.sakuSisa)
    }

    @Test @DisplayName("T-15 Ambil Tabungan RENCANA 300rb tidak memicu BONCOS")
    fun t15() {
        val s = Scenario.funded("2026-11").apply {
            spendExactly("2026-11", skipBuahDays = 12)
            withdraw("2026-11-10", Pot.TABUNGAN, 300_000, WithdrawReason.RENCANA)
        }
        val nov = s.at("2026-12-01").months.getValue(ym("2026-11"))
        assertEquals(300_000, nov.rencanaWithdrawn)
        assertEquals(0, nov.daruratWithdrawn)
        assertTrue(nov.verdict.kind != VerdictKind.BONCOS)
    }

    @Test @DisplayName("T-16 AI estimasi 390rb dibayar 405rb → Saku −15rb")
    fun t16() {
        val base = Scenario.funded("2026-11")
        val paid = Scenario.funded("2026-11").apply { fixedPay("2026-11-01", ID_AI, 405_000) }
        assertEquals(base.at("2026-11-03").sakuSisa - 15_000, paid.at("2026-11-03").sakuSisa)
        assertTrue(paid.at("2026-11-03").current!!.fixed.first { it.categoryId == ID_AI }.isPaid)
    }

    @Test @DisplayName("T-17 Cocokkan saldo: hitung 250rb, aktual 185rb")
    fun t17() {
        assertEquals(Reconciliation(TxType.UNRECORDED, 65_000), reconcile(250_000, 185_000))
        val s = Scenario.funded("2026-11").apply { spendExactly("2026-11", skipBuahDays = 10) }
        val before = s.at("2026-12-01").months.getValue(ym("2026-11"))
        assertEquals(VerdictKind.BERHASIL_NABUNG, before.verdict.kind)
        s.tx("2026-12-01", TxType.UNRECORDED, 65_000) { copy(closingOf = ym("2026-11")) }
        val after = s.at("2026-12-01").months.getValue(ym("2026-11"))
        assertEquals(before.sakuSisa - 65_000, after.sakuSisa)
        assertEquals(VerdictKind.PAS_PASAN, after.verdict.kind, "verdict dihitung ulang")
    }

    @Test @DisplayName("T-18 Transaksi kemarin ditambahkan setelah hari tertutup")
    fun t18() {
        val s = Scenario.funded("2026-11")
        val before = s.at("2026-11-04")
        assertEquals(180_000, before.sakuSisa)
        s.expense("2026-11-03", ID_MAKAN, 80_000)
        val after = s.at("2026-11-04")
        assertEquals(130_000, after.sakuSisa)
        assertEquals(30_000, after.hutang[ID_MAKAN])
    }

    @Test @DisplayName("T-19 defaultSlot")
    fun t19() {
        assertEquals(Slot.SARAPAN, defaultSlot(LocalTime.of(7, 30)))
        assertEquals(Slot.SIANG, defaultSlot(LocalTime.of(12, 0)))
        assertEquals(Slot.MALAM, defaultSlot(LocalTime.of(19, 0)))
        assertEquals(Slot.MALAM, defaultSlot(LocalTime.of(8, 0), isYesterday = true))
        assertEquals(Slot.SIANG, defaultSlot(LocalTime.of(10, 0)))
        assertEquals(Slot.MALAM, defaultSlot(LocalTime.of(15, 0)))
    }

    @Test @DisplayName("T-20 Input pukul 00:30 wajib tanya hari ini/kemarin")
    fun t20() {
        assertTrue(needsDayQuestion(LocalTime.of(0, 30)))
        assertTrue(needsDayQuestion(LocalTime.of(4, 59)))
        assertFalse(needsDayQuestion(LocalTime.of(5, 0)))
    }

    @Test @DisplayName("T-21 Anti-typo: median 45rb")
    fun t21() {
        val history = listOf(45_000L, 40_000, 50_000, 45_000, 45_000, 42_000, 48_000)
        assertTrue(isSuspiciousAmount(450_000, history).suspicious)
        assertEquals(45_000, isSuspiciousAmount(450_000, history).median)
        assertFalse(isSuspiciousAmount(90_000, history).suspicious)
        assertFalse(isSuspiciousAmount(450_000, history.take(4)).suspicious, "minimal 5 data")
    }

    @Test @DisplayName("T-22 Pengembalian 60rb ke Protein")
    fun t22() {
        val s = Scenario.funded("2026-11")
        s.expense("2026-11-02", ID_PROTEIN, 200_000)
        s.refund("2026-11-03", ID_PROTEIN, 60_000)
        assertEquals(140_000, s.at("2026-11-04").stock.first { it.categoryId == ID_PROTEIN }.used)
    }

    @Test @DisplayName("T-23 THR 3jt tujuan default → Tabungan")
    fun t23() {
        val base = Scenario.funded("2026-11").at("2026-11-03")
        val s = Scenario.funded("2026-11").apply { income("2026-11-02", 3_000_000, IncomeKind.THR_BONUS) }
        val st = s.at("2026-11-03")
        assertEquals(base.tabungan + 3_000_000, st.tabungan)
        assertEquals(base.sakuSisa, st.sakuSisa)
    }

    @Test @DisplayName("T-24 Onboarding 16 Sep 2026, uang pegangan 900rb")
    fun t24() {
        val p = planOnboarding(d("2026-09-16"), 900_000, cats)
        assertEquals(15, p.remainingDays)
        assertEquals(15 * 60_000L, p.dailyTotal)
        assertEquals(2, p.transportWindows)
        assertEquals(240_000, p.transportAmount)
        assertEquals(mapOf(ID_PROTEIN to 100_000L, ID_LAIN to 75_000L), p.stockProrata)
        assertEquals(900_000 - (900_000 + 240_000 + 175_000), p.sakuSisaAwal)
        val st = Scenario(d("2026-09-16"), cash = 900_000).at("2026-09-16")
        assertEquals(-415_000, st.current!!.sakuSisaAwal)
        assertEquals(415_000, st.targetCadangan, "R-14 berlaku bila minus")
    }

    @Test @DisplayName("T-26 Gaji telat: talangan bayangan lalu kembali utuh")
    fun t26() {
        fun september() = Scenario(d("2026-09-30"), cash = 131_000).apply {
            expense("2026-09-30", ID_MAKAN, 50_000); expense("2026-09-30", ID_BUAH, 10_000)
            expense("2026-09-30", ID_PROTEIN, 6_000); expense("2026-09-30", ID_LAIN, 5_000)
            plan("2026-09") { copy(closed = true) }
        }
        val late = september()
        assertEquals(60_000, late.at("2026-10-01").months.getValue(ym("2026-09")).sakuSisa)
        late.carry("2026-10-01", "2026-09", 60_000)
        late.expense("2026-10-01", ID_MAKAN, 45_000)
        late.expense("2026-10-02", ID_MAKAN, 45_000)
        val beforeSalary = late.at("2026-10-02")
        assertEquals(SalaryStatus.MISSING, beforeSalary.salaryStatus)
        assertEquals(Talangan(90_000, 60_000, 30_000, 0, 0), beforeSalary.talangan)
        assertEquals(470_000, beforeSalary.tabunganShown)
        assertEquals(500_000, beforeSalary.tabungan)
        assertTrue(late.txs.none { it.type == TxType.SAVING_WITHDRAW })

        late.salary("2026-10-03", "2026-10")
        late.add(routineDepositFor(0, d("2026-10-03"), ym("2026-10"), templateOf(cats, ym("2026-10")), cats)!!)
        val afterSalary = late.at("2026-10-03")
        assertNull(afterSalary.talangan)
        assertEquals(660_000, afterSalary.tabungan)
        assertEquals(60_000, afterSalary.current!!.bawaan)

        val onTime = september().apply {
            carry("2026-10-01", "2026-09", 60_000)
            salary("2026-09-30", "2026-10")
            routineDeposit("2026-10-01", "2026-10")
            expense("2026-10-01", ID_MAKAN, 45_000)
            expense("2026-10-02", ID_MAKAN, 45_000)
        }.at("2026-10-03")
        assertEquals(onTime.sakuSisa, afterSalary.sakuSisa)
        assertEquals(onTime.hutang, afterSalary.hutang)
        assertEquals(onTime.tabungan, afterSalary.tabungan)
        assertEquals(onTime.uangPegangan, afterSalary.uangPegangan)
    }

    @Test @DisplayName("T-27 Talangan melebihi semua kantong")
    fun t27() {
        val s = Scenario(d("2026-09-30"), cash = 71_000, savings = 0, emergency = 0).apply {
            expense("2026-09-30", ID_MAKAN, 50_000); expense("2026-09-30", ID_BUAH, 10_000)
            expense("2026-09-30", ID_PROTEIN, 6_000); expense("2026-09-30", ID_LAIN, 5_000)
        }
        s.expense("2026-10-01", ID_LAIN, 100_000)
        val st = s.at("2026-10-01")
        assertEquals(100_000, st.talangan!!.shortfall)
        val impact = previewImpact(s.input(), d("2026-10-01"), Tx(999, d("2026-10-01"), TxType.EXPENSE, 10_000, categoryId = ID_LAIN))
        assertTrue(ImpactWarning.TALANGAN_EXHAUSTED in impact.warnings)
    }

    private fun septemberCarry(carry: Long, makanSep30: Long = 50_000) =
        Scenario(d("2026-09-30"), cash = 71_000 + carry).apply {
            expense("2026-09-30", ID_MAKAN, makanSep30); expense("2026-09-30", ID_BUAH, 10_000)
            expense("2026-09-30", ID_PROTEIN, 6_000); expense("2026-09-30", ID_LAIN, 5_000)
            carry("2026-10-01", "2026-09", carry)
            plan("2026-09") { copy(closed = true) }
        }

    @Test @DisplayName("T-28 Tutup Buku bulan tanpa gaji")
    fun t28() {
        val s = septemberCarry(100_000).apply {
            (1..8).forEach { expense("2026-10-%02d".format(it), ID_MAKAN, 100_000) }
            plan("2026-10") { copy(noSalary = true) }
        }
        val before = s.at("2026-11-02")
        val oct = before.months.getValue(ym("2026-10"))
        assertEquals(800_000, oct.pengeluaranRiil)
        assertEquals(-700_000, oct.sakuSebelumDistribusi)
        val cover = coverShortfall(700_000, 0, before.tabungan, before.danaDarurat)
        assertEquals(Cover(0, 500_000, 200_000, 0), cover)
        s.withdraw("2026-11-02", Pot.TABUNGAN, 500_000, WithdrawReason.TANPA_GAJI, closingOf = "2026-10")
        s.withdraw("2026-11-02", Pot.DANA_DARURAT, 200_000, WithdrawReason.TANPA_GAJI, closingOf = "2026-10")
        val after = s.at("2026-11-02")
        val octAfter = after.months.getValue(ym("2026-10"))
        assertEquals(0, after.tabungan)
        assertEquals(800_000, after.danaDarurat)
        assertEquals(VerdictKind.TANPA_GAJI, octAfter.verdict.kind)
        assertEquals(0, octAfter.sakuSisa)
    }

    @Test @DisplayName("T-29 Protein 186rb → Saku +14rb saat hari terakhir tertutup")
    fun t29() {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-02", ID_PROTEIN, 186_000) }
        assertEquals(0, s.at("2026-11-30").months.getValue(ym("2026-11")).stock.first { it.categoryId == ID_PROTEIN }.residualToSaku)
        assertEquals(14_000, s.at("2026-12-01").months.getValue(ym("2026-11")).stock.first { it.categoryId == ID_PROTEIN }.residualToSaku)
    }

    @Test @DisplayName("T-30 Hutang Makan 160rb → peringatan hutang besar")
    fun t30() {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-01", ID_MAKAN, 210_000) }
        val st = s.at("2026-11-02")
        assertEquals(160_000, st.hutang[ID_MAKAN])
        assertTrue(ID_MAKAN in st.largeDebt)
    }

    @Test @DisplayName("T-31 Transport hari Jumat masuk akhir pekan Sabtu berikutnya")
    fun t31() {
        assertEquals(d("2026-10-03"), weekendWindowOf(d("2026-10-02"))!!.saturday)
        assertNull(weekendWindowOf(d("2026-10-01")))
    }

    @Test @DisplayName("T-32 Pakai Tabungan (Rencana) dari layar input")
    fun t32() {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-01", ID_LAIN, 150_000) }
        val draft = Tx(999, d("2026-11-01"), TxType.EXPENSE, 30_000, categoryId = ID_LAIN)
        val impact = previewImpact(s.input(), d("2026-11-01"), draft)
        assertTrue(ImpactWarning.USES_RESERVE in impact.warnings)
        assertEquals(30_000, impact.useSavingsAmount)
        assertTrue(impact.savingsCanCover)
        s.withdraw("2026-11-01", Pot.TABUNGAN, impact.useSavingsAmount, WithdrawReason.RENCANA)
        s.add(draft)
        val st = s.at("2026-11-01")
        assertEquals(0, st.sakuSisa)
        assertEquals(1_000_000, st.danaDarurat)
        assertTrue(st.current!!.verdict.kind != VerdictKind.BONCOS)

        val poor = Scenario.funded("2026-11").apply { savings = 10_000; expense("2026-11-01", ID_LAIN, 150_000) }
        assertFalse(previewImpact(poor.input(), d("2026-11-01"), draft).savingsCanCover)
    }

    @Test @DisplayName("T-33 Akhir pekan tanpa Transport → Senin Saku +120rb")
    fun t33() {
        val w = Scenario.funded("2026-11").at("2026-11-09").weekendLog.first { it.window.saturday == d("2026-11-07") }
        assertEquals(120_000, w.toSaku)
    }

    @Test @DisplayName("T-34 Akhir pekan dengan Transport 105rb → Senin Saku +15rb")
    fun t34() {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-07", ID_TRANSPORT, 105_000) }
        assertEquals(15_000, s.at("2026-11-09").weekendLog.first { it.window.saturday == d("2026-11-07") }.toSaku)
    }

    @Test @DisplayName("T-35 Transport hari Rabu 50rb → trip tambahan")
    fun t35() {
        val base = Scenario.funded("2026-11").at("2026-11-04")
        val st = Scenario.funded("2026-11").apply { expense("2026-11-04", ID_TRANSPORT, 50_000) }.at("2026-11-04")
        assertEquals(base.sakuSisa - 50_000, st.sakuSisa)
        assertEquals(480_000, st.current!!.transportBudget)
        assertEquals(base.transport!!.remainingBudget, st.transport!!.remainingBudget)
    }

    @Test @DisplayName("T-36 Kekurangan 700rb ditutup (DARURAT)")
    fun t36() {
        assertEquals(Cover(100_000, 500_000, 100_000, 0), coverShortfall(700_000, 100_000, 500_000, 1_000_000))
        val s = Scenario.funded("2026-11").apply {
            expense("2026-11-01", ID_MAKAN, 750_000); expense("2026-11-01", ID_BUAH, 10_000)
            income("2026-11-01", 100_000)
        }
        val before = s.at("2026-11-02")
        assertEquals(100_000, before.sakuSisa)
        assertEquals(700_000, before.hutang[ID_MAKAN])
        s.withdraw("2026-11-02", Pot.TABUNGAN, 500_000, WithdrawReason.DARURAT)
        s.withdraw("2026-11-02", Pot.DANA_DARURAT, 100_000, WithdrawReason.DARURAT)
        s.payoff("2026-11-02", ID_MAKAN, 700_000)
        val st = s.at("2026-11-02")
        assertEquals(0, st.sakuSisa)
        assertEquals(0, st.tabungan)
        assertEquals(900_000, st.danaDarurat)
        assertEquals(0, st.hutang[ID_MAKAN])
        assertEquals(Verdict(VerdictKind.BONCOS, 600_000), st.current!!.verdict)
        assertEquals(100_000, st.current!!.danaDaruratTerpakai)
    }

    @Test @DisplayName("T-37 Lain-lain 170rb (budget 150rb) → Saku −20rb saat melewati budget")
    fun t37() {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-02", ID_LAIN, 100_000) }
        val before = s.at("2026-11-03")
        val impact = previewImpact(s.input(), d("2026-11-03"), Tx(999, d("2026-11-03"), TxType.EXPENSE, 70_000, categoryId = ID_LAIN))
        assertEquals(20_000, impact.stockOverBy)
        assertTrue(ImpactWarning.STOCK_OVER in impact.warnings)
        s.expense("2026-11-03", ID_LAIN, 70_000)
        assertEquals(before.sakuSisa - 20_000, s.at("2026-11-03").sakuSisa)
    }

    @Test @DisplayName("T-38 Dana Darurat di bawah target → Isi Dana Darurat paling atas")
    fun t38() {
        assertEquals(ClosingOption.ISI_DANA_DARURAT, closingOptions(200_000, 900_000, 1_000_000).first())
        assertEquals(ClosingOption.SEMUA_KE_TABUNGAN, closingOptions(200_000, 1_000_000, 1_000_000).first())
    }

    @Test @DisplayName("T-39 Transport Jumat 30 Apr 2027 dihitung ke akhir pekan ke-1 Mei 2027")
    fun t39() {
        val s = Scenario(d("2027-04-29"), cash = 200_000).apply {
            salary("2027-04-29", "2027-05")
            plan("2027-04") { copy(closed = true) }
        }
        val fri = s.expense("2027-04-30", ID_TRANSPORT, 50_000)
        assertEquals(ym("2027-05"), accountingMonth(fri, cats))
        assertFalse(isLocked(fri, cats, closedMonths = setOf(ym("2027-04"))), "tidak ikut terkunci saat April ditutup")
        val may = s.at("2027-05-03").months.getValue(ym("2027-05"))
        assertEquals(1, may.windows.first().index)
        assertEquals(50_000, may.windows.first().used)
        assertEquals(0, s.at("2027-05-03").months.getValue(ym("2027-04")).pengeluaranRiil)
    }

    @Test @DisplayName("T-40 Tanpa gaji: bawaan 100rb, hutang masuk 20rb, riil 800rb")
    fun t40() {
        val s = septemberCarry(100_000, makanSep30 = 70_000).apply {
            (1..8).forEach { expense("2026-10-%02d".format(it), ID_BUAH, 100_000) }
            plan("2026-10") { copy(noSalary = true) }
        }
        val st = s.at("2026-11-02")
        val oct = st.months.getValue(ym("2026-10"))
        assertEquals(20_000, oct.hutangMasuk)
        assertEquals(800_000, oct.pengeluaranRiil)
        assertEquals(-720_000, oct.sakuSebelumDistribusi)
        assertEquals(0, oct.hutangDibawa)
        assertEquals(0L, st.totalHutang)
    }

    @Test @DisplayName("T-41 AI dibayar sebelum gaji telat masuk")
    fun t41() {
        val seeds = fixedObligationsFor(ym("2026-10"), templateOf(cats, ym("2026-10")), cats)
        assertTrue(seeds.any { it.categoryId == ID_AI && it.estimate == 390_000L && it.dueDate == d("2026-10-01") })
        fun scenario(pay: Boolean) = septemberCarry(0).apply { if (pay) fixedPay("2026-10-01", ID_AI, 400_000) }
        val paid = scenario(true)
        val talangan = paid.at("2026-10-02")
        assertEquals(400_000, talangan.talangan!!.amount)
        assertTrue(talangan.current!!.fixed.first { it.categoryId == ID_AI }.isPaid)
        val unpaid = scenario(false)
        paid.salary("2026-10-03", "2026-10"); unpaid.salary("2026-10-03", "2026-10")
        assertEquals(unpaid.at("2026-10-03").sakuSisa - 10_000, paid.at("2026-10-03").sakuSisa)
    }

    @Test @DisplayName("T-42 Budget Transport 450rb → J 112rb, sisa 2rb dicairkan akhir bulan")
    fun t42() {
        assertEquals(112_000, weekendAllowance(450_000))
        val alloc = templateOf(cats, ym("2026-11")).map { if (it.categoryId == ID_TRANSPORT) it.copy(monthlyAmount = 450_000) else it }
        val s = Scenario.funded("2026-11").apply { plan("2026-11") { copy(allocation = alloc) } }
        assertEquals(112_000, s.at("2026-11-02").current!!.weekendAllowance)
        assertEquals(0, s.at("2026-11-30").months.getValue(ym("2026-11")).transportResidualToSaku)
        assertEquals(2_000, s.at("2026-12-01").months.getValue(ym("2026-11")).transportResidualToSaku)
    }

    @Test @DisplayName("T-43 REFUND Transport di dalam dan di luar jendela")
    fun t43() {
        val s = Scenario.funded("2026-11").apply {
            expense("2026-11-07", ID_TRANSPORT, 120_000)
            refund("2026-11-08", ID_TRANSPORT, 20_000)
        }
        assertEquals(100_000, s.at("2026-11-08").months.getValue(ym("2026-11")).windows.first().used)
        val before = s.at("2026-11-11")
        s.refund("2026-11-11", ID_TRANSPORT, 30_000)
        assertEquals(before.sakuSisa + 30_000, s.at("2026-11-11").sakuSisa)
    }
}
