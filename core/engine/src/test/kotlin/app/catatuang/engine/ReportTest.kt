package app.catatuang.engine

import app.catatuang.engine.Defaults.ID_AI
import app.catatuang.engine.Defaults.ID_BUAH
import app.catatuang.engine.Defaults.ID_MAKAN
import app.catatuang.engine.Defaults.ID_PROTEIN
import app.catatuang.engine.Defaults.ID_TRANSPORT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReportTest {
    /** November 2026: Senin 2 – Minggu 8 Nov. */
    private fun nov() = Scenario.funded("2026-11").apply {
        expense("2026-11-02", ID_MAKAN, 65_000)
        expense("2026-11-03", ID_MAKAN, 40_000)
        expense("2026-11-03", ID_BUAH, 10_000)
        expense("2026-11-07", ID_TRANSPORT, 105_000)
        expense("2026-11-04", ID_PROTEIN, 93_000)
        refund("2026-11-05", ID_PROTEIN, 3_000)
        fixedPay("2026-11-01", ID_AI, 390_000)
    }

    @Test @DisplayName("Mingguan: total per pos, hari paling boros, hutang, akhir pekan, Saku Sisa")
    fun weekly() {
        val s = nov()
        val st = s.at("2026-11-09")
        val w = weeklyReport(s.input(), st, d("2026-11-05"))
        assertEquals(d("2026-11-02"), w.start)
        assertEquals(d("2026-11-08"), w.end)
        assertEquals(mapOf(ID_MAKAN to 105_000L, ID_BUAH to 10_000L, ID_TRANSPORT to 105_000L, ID_PROTEIN to 90_000L), w.perCategory)
        assertEquals(310_000, w.total)
        assertEquals(390_000, w.previousTotal) // Minggu 1 Nov: AI
        assertEquals(-21, w.changePercent)
        assertEquals(d("2026-11-07") to 105_000L, w.worstDay)
        assertEquals(7, w.daily.getValue(ID_MAKAN).size)
        assertEquals(0, w.debtStart)
        assertEquals(0, w.debtEnd)
        val weekend = w.weekends.single()
        assertEquals(15_000L, weekend.toSaku)
        // Saku: 2 Nov hutang 15rb; 3 Nov lunas 5rb... Makan: -15,+10(→hutang 5)… dihitung dari ledger, cukup cek konsisten.
        val before = computeLedger(s.input(), d("2026-11-02")).months.getValue(ym("2026-11")).sakuSisa
        assertEquals(st.months.getValue(ym("2026-11")).sakuSisa - before, w.sakuChange)
    }

    @Test @DisplayName("Mingguan yang melewati pergantian bulan: perubahan Saku tidak dihitung")
    fun weeklyAcrossMonth() {
        val s = Scenario.funded("2026-11").apply { salary("2026-11-29", "2026-12") }
        val w = weeklyReport(s.input(), s.at("2026-12-03"), d("2026-12-01"))
        assertEquals(d("2026-11-30"), w.start)
        assertNull(w.sakuChange)
    }

    @Test @DisplayName("Bulanan: budget vs realisasi & statistik hari")
    fun monthly() {
        val s = nov()
        val r = monthlyReport(s.input(), s.at("2026-11-09"), ym("2026-11"))!!
        assertTrue(r.provisional)
        val rows = r.rows.associateBy { it.categoryId }
        assertEquals(1_500_000, rows.getValue(ID_MAKAN).budget)
        assertEquals(105_000, rows.getValue(ID_MAKAN).used)
        assertEquals(480_000, rows.getValue(ID_TRANSPORT).budget)
        assertEquals(90_000, rows.getValue(ID_PROTEIN).used)
        assertEquals(390_000, rows.getValue(ID_AI).used)
        assertEquals(StockStatus.NORMAL, rows.getValue(ID_PROTEIN).status)
        assertEquals(1, r.dayStats.getValue(ID_MAKAN).overDays)
    }

    @Test @DisplayName("Export: 5 sheet, nominal sebagai angka")
    fun sheets() {
        val s = nov()
        val sheets = exportSheets(s.input(), s.at("2026-11-09"), ExportRange("2026-11", d("2026-11-01"), d("2026-11-30"), ym("2026-11")))
        assertEquals(listOf("Ringkasan", "Transaksi", "Per Kategori", "Harian Makan & Buah", "Akhir Pekan Transport"), sheets.map { it.name })
        val tx = sheets[1].rows
        assertEquals(Cell.Text("Tanggal"), tx[0][0])
        val makan = tx.first { it[2] == Cell.Text("Makan") }
        assertEquals(Cell.Num(-65_000), makan[4])
        assertTrue(sheets[0].rows.any { it[0] == Cell.Text("Verdict") })
        assertEquals(Cell.Num(15_000), sheets[4].rows[1][4])
        assertTrue(sheets[3].rows.size > 1)
    }
}
