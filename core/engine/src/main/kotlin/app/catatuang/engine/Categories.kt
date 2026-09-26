package app.catatuang.engine

import java.time.YearMonth

/* R-95 & 8.11: ubah nominal, tambah, dan arsip pos. Semua perubahan berlaku mulai bulan berikutnya. */

/**
 * Bulan pertama tempat perubahan pos berlaku: bulan setelah bulan berjalan. Bila gaji bulan itu sudah
 * di-split (alokasinya sudah tersimpan & tetap), perubahan jatuh ke bulan sesudahnya (D-64).
 */
fun nextEditableMonth(input: LedgerInput, current: YearMonth): YearMonth {
    var m = current.plusMonths(1)
    while (input.plan(m)?.allocation != null || input.plan(m)?.closed == true) m = m.plusMonths(1)
    return m
}

/** Nominal pos mulai [from]; entri lain untuk bulan yang sama diganti. */
fun Category.withAmount(from: YearMonth, dailyAmount: Long?, monthlyAmount: Long?): Category {
    require((dailyAmount ?: 0) >= 0 && (monthlyAmount ?: 0) >= 0) { "Nominal tidak boleh negatif" }
    val entry = if (kind == CategoryKind.DAILY) CategoryAmount(from, dailyAmount = dailyAmount ?: 0) else CategoryAmount(from, monthlyAmount = monthlyAmount ?: 0)
    return copy(amounts = amounts.filter { it.effectiveFrom != from } + entry)
}

/** Pos baru (R-95: hanya STOK atau TETAP), aktif mulai [from]. */
fun newCategory(id: Long, name: String, kind: CategoryKind, monthlyAmount: Long, from: YearMonth, sortOrder: Int, dueDay: Int? = null): Category {
    require(kind == CategoryKind.STOCK || kind == CategoryKind.FIXED) { "Pos baru hanya STOK atau TETAP" }
    require(name.isNotBlank() && monthlyAmount >= 0)
    return Category(
        id = id,
        key = "pos_$id",
        name = name.trim().take(24),
        kind = kind,
        amounts = listOf(CategoryAmount(from, monthlyAmount = monthlyAmount)),
        dueDay = if (kind == CategoryKind.FIXED) (dueDay ?: 1).coerceIn(1, 31) else null,
        sortOrder = sortOrder,
        activeFrom = from,
    )
}

/** Pos yang boleh diarsipkan: STOK (kecuali Transport) & TETAP. Pos HARIAN dan Nabung tidak (D-65). */
fun Category.canArchive(): Boolean = (kind == CategoryKind.STOCK && !weekendMode) || kind == CategoryKind.FIXED

fun Category.archivedFrom(from: YearMonth): Category {
    require(canArchive()) { "Pos ini tidak bisa diarsipkan" }
    return copy(archivedFrom = from)
}

/** Nominal yang berlaku di bulan [ym] (untuk ditampilkan di Pengaturan). */
fun Category.valueIn(ym: YearMonth): Long? {
    val a = amountFor(ym) ?: return null
    return if (kind == CategoryKind.DAILY) a.dailyAmount else a.monthlyAmount
}
