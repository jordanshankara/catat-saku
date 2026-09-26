package app.catatuang.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** R-95: perubahan pos berlaku mulai bulan berikutnya. */
class CategoriesTest {
    @Test @DisplayName("Bulan berlaku: bulan depan, atau sesudahnya bila bulan depan sudah di-split")
    fun effectiveMonth() {
        val s = Scenario.funded("2026-11")
        assertEquals(ym("2026-12"), nextEditableMonth(s.input(), ym("2026-11")))
        s.plan("2026-12") { copy(allocation = templateOf(Defaults.categories, ym("2026-12"))) }
        assertEquals(ym("2027-01"), nextEditableMonth(s.input(), ym("2026-11")))
    }

    @Test @DisplayName("Ubah nominal Makan mulai Desember: November tetap 50rb")
    fun changeAmount() {
        val makan = Defaults.categories.first { it.id == Defaults.ID_MAKAN }.withAmount(ym("2026-12"), 45_000, null)
        assertEquals(50_000L, makan.valueIn(ym("2026-11")))
        assertEquals(45_000L, makan.valueIn(ym("2026-12")))
        val changed = makan.withAmount(ym("2026-12"), 40_000, null)
        assertEquals(2, changed.amounts.size)
        assertEquals(40_000L, changed.valueIn(ym("2027-03")))
    }

    @Test @DisplayName("Pos STOK baru masuk template & ditandai Baru di split bulan berikutnya; ledger ikut R-39")
    fun addCategory() {
        val pos = newCategory(20, "Laundry", CategoryKind.STOCK, 60_000, ym("2026-12"), sortOrder = 9)
        val cats = Defaults.categories + pos
        assertFalse(templateOf(cats, ym("2026-11")).any { it.categoryId == 20L })
        assertTrue(templateOf(cats, ym("2026-12")).any { it.categoryId == 20L && it.monthlyAmount == 60_000L })
        val s = Scenario(d("2026-11-30"), categories = cats).apply { salary("2026-11-30", "2026-12") }
        val prep = prepareSalary(s.input(), 3_300_000, ym("2026-12"))
        assertEquals(setOf(20L), prep.newCategoryIds)
        assertEquals(3_420_000L, prep.plan!!.kebutuhan) // Des 31 hari: 3.360.000 + Laundry 60.000
        s.expense("2026-12-05", 20, 40_000)
        val dec = s.at("2027-01-01").months.getValue(ym("2026-12"))
        assertEquals(20_000L, dec.stock.first { it.categoryId == 20L }.residualToSaku)
        assertThrows(IllegalArgumentException::class.java) { newCategory(21, "Harian", CategoryKind.DAILY, 1, ym("2026-12"), 10) }
    }

    @Test @DisplayName("Arsip pos: hilang dari template mulai bulan itu, riwayat lama tetap")
    fun archive() {
        val ai = Defaults.categories.first { it.id == Defaults.ID_AI }.archivedFrom(ym("2026-12"))
        val cats = Defaults.categories.map { if (it.id == ai.id) ai else it }
        assertTrue(templateOf(cats, ym("2026-11")).any { it.categoryId == Defaults.ID_AI })
        assertFalse(templateOf(cats, ym("2026-12")).any { it.categoryId == Defaults.ID_AI })
        assertEquals(3_300_000L - 390_000 + 60_000, kebutuhan(templateOf(cats, ym("2026-12")), cats, 31))
        assertFalse(Defaults.categories.first { it.id == Defaults.ID_MAKAN }.canArchive())
        assertFalse(Defaults.categories.first { it.id == Defaults.ID_TRANSPORT }.canArchive())
    }
}
