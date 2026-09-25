package app.catatuang.engine

import app.catatuang.engine.Defaults.ID_BUAH
import app.catatuang.engine.Defaults.ID_MAKAN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class InsightsTest {
    private val makan = Defaults.categories.first { it.id == ID_MAKAN }

    @Test @DisplayName("8.2 Chip nominal cepat: fallback lalu 4 nominal tersering 60 hari")
    fun quick() {
        val s = Scenario.funded("2026-11")
        assertEquals(listOf(10_000L, 15_000, 20_000, 25_000), quickAmounts(s.txs, makan, d("2026-11-10")))
        listOf(18_000L, 18_000, 18_000, 22_000, 22_000, 30_000, 12_000, 12_000, 45_000).forEachIndexed { i, a ->
            s.expense("2026-11-%02d".format(i + 1), ID_MAKAN, a)
        }
        s.expense("2026-11-02", ID_BUAH, 99_000)
        assertEquals(listOf(12_000L, 18_000, 22_000, 45_000), quickAmounts(s.txs, makan, d("2026-11-10")))
        assertEquals(listOf(10_000L, 15_000, 20_000, 25_000), quickAmounts(s.txs, makan, d("2027-02-01")), "di luar 60 hari → fallback")
    }

    @Test @DisplayName("R-65 riwayat nominal terbaru untuk anti-typo")
    fun recent() {
        val s = Scenario.funded("2026-11")
        (1..25).forEach { s.expense("2026-11-%02d".format(it), ID_MAKAN, it * 1_000L) }
        val r = recentAmounts(s.txs, ID_MAKAN)
        assertEquals(20, r.size)
        assertEquals(25_000, r.first())
    }

    @Test @DisplayName("8.3 Statistik bulan & 7 hari terakhir pos HARIAN")
    fun dailyStats() {
        val s = Scenario.funded("2026-11").apply {
            expense("2026-11-01", ID_MAKAN, 50_000)
            expense("2026-11-02", ID_MAKAN, 62_000)
            expense("2026-11-03", ID_MAKAN, 40_000)
            expense("2026-11-04", ID_MAKAN, 30_000)
        }
        val st = s.at("2026-11-04")
        assertEquals(DailyMonthStats(savedDays = 1, overDays = 1, toSaku = 0), dailyMonthStats(st, ID_MAKAN))
        val week = lastSevenDays(st, ID_MAKAN)
        assertEquals(5, week.size, "31 Okt (tanggal mulai) s/d 4 Nov; hari sebelum tanggal mulai tidak ada")
        assertEquals(12_000, week[2].over)
        assertEquals(30_000, week.last().used)
    }

    @Test @DisplayName("R-61 date picker dibatasi bulan yang belum ditutup")
    fun dateRange() {
        val r = selectableDateRange(d("2026-10-30"), d("2026-11-02"), setOf(ym("2026-10")))
        assertEquals(listOf(d("2026-11-01"), d("2026-11-02")), r)
    }
}
