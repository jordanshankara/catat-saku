package app.catatuang.engine

import java.time.LocalDate
import java.time.YearMonth

/* Aksi Fase 3 (Gajian, Pemasukan, Tabungan & Dana Darurat, Pos TETAP): menyusun transaksi yang akan
 * disimpan. Tidak ada saldo yang diubah langsung — semua efek tetap hasil computeLedger (prinsip 6). */

/** R-05: bulan target sudah punya gaji. */
fun hasSalary(input: LedgerInput, target: YearMonth): Boolean =
    input.transactions.any { it.type == TxType.SALARY && accountingMonth(it, input.categories) == target }

/**
 * Bawaan untuk preview split bulan [target] (R-14 butir 1): diketahui hanya bila Tutup Buku bulan
 * sebelumnya sudah selesai; null = "Bisa berubah setelah Tutup Buku".
 */
fun knownBawaan(input: LedgerInput, target: YearMonth): Long? {
    val prev = target.minusMonths(1)
    if (prev < YearMonth.from(input.onboarding.startDate)) return 0
    if (input.plan(prev)?.closed != true) return null
    return input.transactions.filter { it.type == TxType.CARRY_OVER && it.refYearMonth == target }.sumOf { it.amount }
}

data class SalaryPrep(
    val target: YearMonth,
    /** Gaji untuk bulan onboarding: menambah Saku Sisa bulan itu penuh, tanpa split (8.12). */
    val onboardingMonth: Boolean,
    /** Alokasi awal (template bulan target, R-95). */
    val allocation: List<AllocationLine>,
    /** Pos yang baru aktif di bulan target (tanda "Baru" di preview split, R-95). */
    val newCategoryIds: Set<Long>,
    val bawaan: Long?,
    /** Rencana split dengan alokasi awal; null untuk bulan onboarding. */
    val plan: SplitPlan?,
)

/** Langkah 1 → 2/3 alur Gajian (8.6). */
fun prepareSalary(input: LedgerInput, amount: Long, target: YearMonth): SalaryPrep {
    val onboarding = target == YearMonth.from(input.onboarding.startDate)
    val allocation = input.template(target)
    val bawaan = knownBawaan(input, target)
    return SalaryPrep(
        target = target,
        onboardingMonth = onboarding,
        allocation = allocation,
        newCategoryIds = input.categories.filter { it.activeFrom == target && it.isActiveIn(target) }.map { it.id }.toSet(),
        bawaan = bawaan,
        plan = if (onboarding) null else planSplit(amount, target, allocation, input.categories, bawaan),
    )
}

/** R-06: tombol lanjut Penyesuaian/ubah alokasi aktif hanya bila KebutuhanStandar hasilnya ≤ gaji. */
fun allocationFits(salary: Long, allocation: List<AllocationLine>, categories: List<Category>): Boolean =
    kebutuhanStandar(allocation, categories) <= salary

/**
 * Transaksi konfirmasi gaji: SALARY (bulan target) + Nabung rutin pada tanggal alokasi aktif (R-07).
 * Gaji tambahan/rapel (R-05) dan gaji bulan onboarding tidak membuat setoran Nabung.
 */
fun salaryTransactions(
    amount: Long,
    received: LocalDate,
    target: YearMonth,
    allocation: List<AllocationLine>?,
    categories: List<Category>,
    extra: Boolean,
): List<Tx> {
    val salary = Tx(
        id = 0,
        date = received,
        type = TxType.SALARY,
        amount = amount,
        refYearMonth = target,
        incomeKind = if (extra) IncomeKind.LAINNYA else null,
        note = if (extra) "Gaji tambahan/rapel" else null,
    )
    if (extra || allocation == null) return listOf(salary)
    return listOfNotNull(salary, routineDepositFor(0, received, target, allocation, categories))
}

/** Pos yang boleh jadi tujuan "tambah budget" pemasukan: STOK aktif selain Transport (6.8). */
fun incomeBudgetCategories(categories: List<Category>, month: YearMonth): List<Category> =
    categories.filter { it.kind == CategoryKind.STOCK && !it.weekendMode && it.isActiveIn(month) }.sortedBy { it.sortOrder }

/** Pos yang boleh menerima Pengembalian: pos HARIAN & STOK aktif (6.8). */
fun refundCategories(categories: List<Category>, month: YearMonth): List<Category> =
    categories.filter { (it.kind == CategoryKind.DAILY || it.kind == CategoryKind.STOCK) && it.isActiveIn(month) }.sortedBy { it.sortOrder }

/** 6.8 Pemasukan tambahan (bukan Pengembalian). */
fun incomeTx(
    kind: IncomeKind,
    amount: Long,
    date: LocalDate,
    destination: Destination,
    pot: Pot? = null,
    categoryId: Long? = null,
    note: String? = null,
): Tx = Tx(
    id = 0,
    date = date,
    type = TxType.INCOME,
    amount = amount,
    incomeKind = kind,
    destination = destination,
    pot = if (destination == Destination.POT) (pot ?: Pot.TABUNGAN) else null,
    categoryId = if (destination == Destination.CATEGORY) categoryId else null,
    note = note,
)

/** 6.8 Pengembalian ke kategori: mengurangi "terpakai" pos asal pada tanggalnya. */
fun refundTx(categoryId: Long, amount: Long, date: LocalDate, note: String? = null): Tx =
    Tx(0, date, TxType.REFUND, amount, categoryId = categoryId, note = note)

/** R-55 setoran manual ke kantong (Saku Sisa −). */
fun depositTx(pot: Pot, amount: Long, date: LocalDate, note: String? = null): Tx =
    Tx(0, date, TxType.SAVING_DEPOSIT, amount, pot = pot, note = note)

/** R-56 ambil dari kantong; uangnya masuk Saku Sisa. */
fun withdrawTx(pot: Pot, amount: Long, reason: WithdrawReason, date: LocalDate, note: String? = null): Tx =
    Tx(0, date, TxType.SAVING_WITHDRAW, amount, pot = pot, reason = reason, note = note)

/**
 * R-52 "Tutup sekarang": Saku Sisa minus bulan berjalan ditutup dengan urutan penutup (Tabungan → Dana
 * Darurat), alasan DARURAT. Kosong bila Saku tidak minus.
 */
fun coverNowTransactions(sakuSisa: Long, tabungan: Long, danaDarurat: Long, date: LocalDate): List<Tx> {
    if (sakuSisa >= 0) return emptyList()
    val cover = coverShortfall(-sakuSisa, 0, tabungan, danaDarurat)
    return listOfNotNull(
        cover.fromTabungan.takeIf { it > 0 }?.let { withdrawTx(Pot.TABUNGAN, it, WithdrawReason.DARURAT, date) },
        cover.fromDanaDarurat.takeIf { it > 0 }?.let { withdrawTx(Pot.DANA_DARURAT, it, WithdrawReason.DARURAT, date) },
    )
}

/**
 * R-59 Pakai Tabungan (Rencana): ambil dari Tabungan saja sebesar `−SisaBebas` setelah transaksi, lalu
 * simpan pengeluarannya. Null bila tidak berlaku atau Tabungan tidak cukup (Dana Darurat tidak disentuh).
 * Tanggal pengambilan mengikuti bulan akuntansi pengeluaran supaya masuk Saku Sisa bulan yang sama.
 */
fun useSavingsTransactions(impact: Impact, draft: Tx): List<Tx>? {
    if (impact.useSavingsAmount <= 0 || !impact.savingsCanCover) return null
    val own = YearMonth.from(draft.date)
    val date = when {
        own == impact.month -> draft.date
        impact.month < own -> impact.month.atEndOfMonth() // Minggu 1 Nov milik akhir pekan Oktober
        else -> impact.month.atDay(1) // Jumat 30 Apr milik akhir pekan Mei
    }
    return listOf(withdrawTx(Pot.TABUNGAN, impact.useSavingsAmount, WithdrawReason.RENCANA, date), draft)
}

/** R-41/R-42: pembayaran pos TETAP bulan berjalan. */
fun fixedPaymentTx(categoryId: Long, amount: Long, date: LocalDate): Tx =
    Tx(0, date, TxType.FIXED_PAYMENT, amount, categoryId = categoryId)
