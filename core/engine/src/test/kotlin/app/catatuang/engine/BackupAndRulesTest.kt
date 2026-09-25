package app.catatuang.engine

import app.catatuang.engine.Defaults.ID_BUAH
import app.catatuang.engine.Defaults.ID_MAKAN
import app.catatuang.engine.Defaults.ID_TRANSPORT
import app.catatuang.engine.store.AllocationRecord
import app.catatuang.engine.store.BackupCodec
import app.catatuang.engine.store.BackupDocument
import app.catatuang.engine.store.BackupReadResult
import app.catatuang.engine.store.DataSnapshot
import app.catatuang.engine.store.DayMarkRecord
import app.catatuang.engine.store.FixedObligationRecord
import app.catatuang.engine.store.MonthPlanRecord
import app.catatuang.engine.store.SettingsRecord
import app.catatuang.engine.store.toLedgerInput
import app.catatuang.engine.store.toRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BackupAndRulesTest {

    private fun sampleSnapshot(): DataSnapshot {
        val s = Scenario.funded("2026-10").apply {
            salary("2026-10-30", "2026-11")
            routineDeposit("2026-10-01", "2026-10")
            spendExactly("2026-10", skipBuahDays = 5)
            expense("2026-10-31", ID_TRANSPORT, 110_000)
            expense("2026-11-02", ID_MAKAN, 80_000, Slot.SIANG)
            income("2026-11-03", 3_000_000, IncomeKind.THR_BONUS)
            withdraw("2026-11-03", Pot.DANA_DARURAT, 50_000, WithdrawReason.RENCANA)
            tx("2026-11-02", TxType.UNRECORDED, 20_000) { copy(closingOf = ym("2026-10")) }
            carry("2026-11-02", "2026-10", 30_000)
        }
        val alloc = templateOf(Defaults.categories, ym("2026-11"))
        return DataSnapshot(
            settings = SettingsRecord(startDate = s.start.toString(), cashStart = s.cash, savingsStart = s.savings, emergencyStart = s.emergency, onboardingDone = true),
            categories = Defaults.categories.map { it.toRecord() },
            monthPlans = listOf(
                MonthPlanRecord("2026-10", salaryTxId = 1, status = "CLOSED"),
                MonthPlanRecord("2026-11", salaryTxId = 2),
            ),
            allocations = alloc.map { AllocationRecord("2026-11", it.categoryId, it.dailyAmount, it.monthlyAmount) },
            transactions = s.txs.map { it.toRecord() },
            fixedObligations = listOf(FixedObligationRecord("2026-11", Defaults.ID_AI, 390_000, "CANCELLED")),
            dayMarks = listOf(DayMarkRecord("2026-11-02", true)),
            closures = emptyList(),
        )
    }

    @Test @DisplayName("T-25 Round-trip backup: ekspor lalu impor → LedgerState identik")
    fun t25() {
        val snapshot = sampleSnapshot()
        val today = d("2026-11-04")
        val before = computeLedger(snapshot.toLedgerInput()!!, today)
        val text = BackupCodec.encode(BackupDocument.of(snapshot, "0.1.0", LocalDateTime.of(2026, 11, 4, 21, 30)))
        val read = BackupCodec.decode(text)
        assertTrue(read is BackupReadResult.Ok, read.toString())
        read as BackupReadResult.Ok
        assertEquals(snapshot, read.document.toSnapshot())
        assertEquals(before, computeLedger(read.document.toSnapshot().toLedgerInput()!!, today))
        assertTrue(before.months.getValue(ym("2026-10")).closed)
        assertFalse(text.contains("pin", ignoreCase = true), "hash PIN tidak ikut backup")
        assertEquals(snapshot.transactions.size, read.preview.transactions)
    }

    @Test @DisplayName("Backup: validasi schemaVersion & JSON rusak")
    fun backupValidation() {
        assertTrue(BackupCodec.decode("bukan json") is BackupReadResult.Invalid)
        assertTrue(BackupCodec.decode("""{"appVersion":"1"}""") is BackupReadResult.Invalid)
        assertTrue(BackupCodec.decode("""{"schemaVersion":99}""") is BackupReadResult.Invalid)
        assertEquals("catatuang-backup-20260923-0905.json", BackupCodec.fileName(LocalDateTime.of(2026, 9, 23, 9, 5)))
    }

    @Test @DisplayName("R-02 Bulan target default gaji")
    fun r02() {
        assertEquals(ym("2026-10"), defaultTargetMonth(d("2026-09-20")))
        assertEquals(ym("2026-10"), defaultTargetMonth(d("2026-10-19")))
        assertEquals(ym("2027-01"), defaultTargetMonth(d("2026-12-30")))
    }

    @Test @DisplayName("R-07 Setoran Nabung rutin bertanggal saat alokasi aktif")
    fun r07() {
        val alloc = templateOf(Defaults.categories, ym("2026-10"))
        val early = routineDepositFor(1, d("2026-09-29"), ym("2026-10"), alloc, Defaults.categories)!!
        assertEquals(d("2026-10-01"), early.date)
        assertEquals(160_000, early.amount)
        assertEquals(d("2026-10-03"), routineDepositFor(1, d("2026-10-03"), ym("2026-10"), alloc, Defaults.categories)!!.date)
        // Setoran bertanggal masa depan belum dihitung.
        val s = Scenario.funded("2026-10").apply { add(early) }
        assertEquals(500_000, s.at("2026-09-30").tabungan)
        assertEquals(660_000, s.at("2026-10-01").tabungan)
    }

    @Test @DisplayName("R-14 butir 4: saran hemat dibulatkan ke atas; tutup buku n hari lagi")
    fun r14() {
        assertEquals(34_000, dailySavingAdvice(100_000, d("2026-10-29")))
        assertEquals(0, dailySavingAdvice(0, d("2026-10-29")))
        assertTrue(isFiveDaysBeforeMonthEnd(d("2026-10-26")))
        assertEquals(3, daysUntilClosing(d("2026-09-28")))
    }

    @Test @DisplayName("R-16 / R-34 / R-17 status & pembulatan")
    fun statuses() {
        assertEquals(SafetyStatus.AMAN, safetyStatus(50_000, 50_000))
        assertEquals(SafetyStatus.WASPADA, safetyStatus(0, 50_000))
        assertEquals(SafetyStatus.MINUS, safetyStatus(-1, 50_000))
        assertEquals(StockStatus.WASPADA, stockStatus(160_000, 200_000))
        assertEquals(StockStatus.NORMAL, stockStatus(159_000, 200_000))
        assertEquals(StockStatus.LEBIH, stockStatus(200_001, 200_000))
        assertEquals(6_000, floor1000(6_666))
        assertEquals(7_000, ceil1000(6_001))
    }

    @Test @DisplayName("8.3 Tahan di Rp X = jatah − hutang, hanya jika terpakai ≤ X")
    fun holdAdvice() {
        assertEquals(35_000, holdTarget(50_000, 15_000, 35_000))
        assertNull(holdTarget(50_000, 15_000, 36_000))
        assertNull(holdTarget(50_000, 60_000, 0))
        assertNull(holdTarget(50_000, 0, 0))
    }

    @Test @DisplayName("Hari ini sementara: hutang proyeksi untuk notifikasi 22:00 (R-21)")
    fun projectedDebt() {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-03", ID_MAKAN, 70_000, Slot.SIANG) }
        val st = s.at("2026-11-03")
        val makan = st.daily.first { it.categoryId == ID_MAKAN }
        assertEquals(0, makan.hutang, "hari ini belum tertutup")
        assertEquals(20_000, makan.projectedHutang)
        assertEquals(mapOf(Slot.SIANG to 70_000L), makan.bySlot)
        assertEquals(st.sakuSisa + 10_000, st.projectedSakuSisa, "buah hemat 10rb masuk saat ditutup")
        assertEquals(20_000, s.at("2026-11-04").hutang[ID_MAKAN])
    }

    @Test @DisplayName("Preview dampak HARIAN: 'hutang makan jadi Rp 25.000'")
    fun previewDaily() {
        val s = Scenario.funded("2026-11").apply {
            expense("2026-11-02", ID_MAKAN, 65_000)
            expense("2026-11-03", ID_MAKAN, 35_000)
        }
        val impact = previewImpact(s.input(), d("2026-11-03"), Tx(999, d("2026-11-03"), TxType.EXPENSE, 25_000, categoryId = ID_MAKAN))
        assertEquals(60_000, impact.dailyUsedAfter)
        assertEquals(25_000, impact.debtAfter)
        assertTrue(ImpactWarning.DAILY_OVER in impact.warnings)
        assertEquals(impact.sakuBefore, impact.sakuAfter, "HARIAN tidak menyentuh Saku Sisa sebelum hari ditutup")
    }

    @Test @DisplayName("Transport: status jendela & trip n dari S(M)")
    fun transportStatus() {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-07", ID_TRANSPORT, 100_000) }
        val sat = s.at("2026-11-07").transport!!
        assertEquals(100_000, sat.openWindow!!.used)
        assertEquals(StockStatus.WASPADA, sat.windowStatus)
        val wed = s.at("2026-11-11").transport!!
        assertNull(wed.openWindow)
        assertEquals(1, wed.tripsTaken)
        assertEquals(4, wed.saturdays)
        assertEquals(360_000, wed.remainingBudget)
    }

    @Test @DisplayName("R-43 Pos TETAP 'Tidak jadi' → estimasi kembali ke Saku Sisa")
    fun r43() {
        val base = Scenario.funded("2026-11")
        val cancelled = Scenario.funded("2026-11").apply { plan("2026-11") { copy(cancelledFixed = setOf(Defaults.ID_AI)) } }
        assertEquals(base.at("2026-12-01").months.getValue(ym("2026-11")).sakuSisa + 390_000,
            cancelled.at("2026-12-01").months.getValue(ym("2026-11")).sakuSisa)
    }

    @Test @DisplayName("R-23 Mode Darurat: Saku Sisa tidak boleh jadi minus")
    fun r23() {
        assertEquals(Cover(0, 30_000, 0, 0), coverShortfall(30_000, -10_000, 500_000, 0))
        assertEquals(Cover(0, 0, 20_000, 0), coverShortfall(20_000, 0, 0, 1_000_000))
        assertEquals(Cover(0, 0, 0, 5_000), coverShortfall(5_000, 0, 0, 0))
    }

    @Test @DisplayName("R-95 Perubahan nominal berlaku mulai bulan berikutnya")
    fun r95() {
        val cats = Defaults.categories.map {
            if (it.id == ID_BUAH) it.copy(amounts = it.amounts + CategoryAmount(ym("2026-12"), dailyAmount = 15_000)) else it
        }
        assertEquals(10_000, templateOf(cats, ym("2026-11")).first { it.categoryId == ID_BUAH }.dailyAmount)
        assertEquals(15_000, templateOf(cats, ym("2026-12")).first { it.categoryId == ID_BUAH }.dailyAmount)
    }
}
