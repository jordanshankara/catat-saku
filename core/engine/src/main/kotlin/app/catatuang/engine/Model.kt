package app.catatuang.engine

import java.time.LocalDate
import java.time.YearMonth

/** Jenis pos (bagian 4/5). */
enum class CategoryKind { DAILY, STOCK, FIXED, SAVING }

enum class Slot { SARAPAN, SIANG, MALAM, JAJAN }

enum class TxType {
    SALARY, INCOME, REFUND, EXPENSE, FIXED_PAYMENT, SAVING_DEPOSIT, SAVING_WITHDRAW,
    DEBT_PAYOFF, CARRY_OVER, UNRECORDED, SURPLUS_FOUND, CORRECTION,
}

/** Kantong di luar uang pegangan. */
enum class Pot { TABUNGAN, DANA_DARURAT }

enum class WithdrawReason { DARURAT, RENCANA, TANPA_GAJI }

/** Jenis pemasukan tambahan (6.8). Pengembalian memakai [TxType.REFUND]. */
enum class IncomeKind { PEMBERIAN, SAMPINGAN, THR_BONUS, LAINNYA }

/** Tujuan pemasukan: Saku Sisa, kantong ([Tx.pot]), atau tambah budget pos STOK non-Transport ([Tx.categoryId]). */
enum class Destination { SAKU, POT, CATEGORY }

/** Nominal pos yang berlaku mulai [effectiveFrom] (null = sejak awal). Perubahan berlaku bulan berikutnya (R-95). */
data class CategoryAmount(
    val effectiveFrom: YearMonth?,
    val dailyAmount: Long? = null,
    val monthlyAmount: Long? = null,
)

data class Category(
    val id: Long,
    val key: String,
    val name: String,
    val kind: CategoryKind,
    val amounts: List<CategoryAmount>,
    /** Transport OE: mode akhir pekan (R-35–R-38). */
    val weekendMode: Boolean = false,
    val hasSlots: Boolean = false,
    val dueDay: Int? = null,
    val sortOrder: Int = 0,
    /** Pos baru aktif mulai bulan ini (R-95); null = sejak awal. */
    val activeFrom: YearMonth? = null,
    /** Pos diarsipkan mulai bulan ini (R-95); null = aktif. */
    val archivedFrom: YearMonth? = null,
) {
    fun isActiveIn(ym: YearMonth): Boolean =
        (activeFrom == null || activeFrom <= ym) && (archivedFrom == null || ym < archivedFrom)

    fun amountFor(ym: YearMonth): CategoryAmount? =
        amounts.filter { it.effectiveFrom == null || it.effectiveFrom <= ym }
            .maxWithOrNull(compareBy(nullsFirst()) { it.effectiveFrom })
}

/** Satu baris alokasi bulan (snapshot `month_allocation`). */
data class AllocationLine(
    val categoryId: Long,
    /** Jatah per hari, hanya pos HARIAN. */
    val dailyAmount: Long? = null,
    /** Budget/estimasi/setoran per bulan, pos non-HARIAN. */
    val monthlyAmount: Long = 0,
)

/**
 * Transaksi. Semua [amount] positif; arah ditentukan [type] (7.3).
 *
 * @property closingOf diisi untuk transaksi yang dibuat di alur Tutup Buku bulan itu; dihitung ke bulan
 *   tersebut dan diproses setelah bulan itu berakhir (D-07).
 * @property routine true untuk setoran Nabung rutin (R-07).
 * @property refYearMonth SALARY: bulan target. CARRY_OVER: bulan penerima. SAVING_DEPOSIT rutin: bulan alokasi.
 */
data class Tx(
    val id: Long,
    val date: LocalDate,
    val type: TxType,
    val amount: Long,
    val categoryId: Long? = null,
    val signedAmount: Long? = null,
    val slot: Slot? = null,
    val destination: Destination? = null,
    val pot: Pot? = null,
    val reason: WithdrawReason? = null,
    val refYearMonth: YearMonth? = null,
    val routine: Boolean = false,
    val closingOf: YearMonth? = null,
    val incomeKind: IncomeKind? = null,
    val note: String? = null,
    /** Urutan pencatatan (epoch millis); menentukan urutan transaksi di tanggal yang sama. */
    val createdAt: Long = 0,
)

/** Data per bulan yang tersimpan (`month_plan` + `month_allocation` + keputusan Tutup Buku). */
data class MonthPlan(
    val month: YearMonth,
    /** Alokasi hasil split/Penyesuaian. Null = belum split (pakai template). */
    val allocation: List<AllocationLine>? = null,
    val noSalary: Boolean = false,
    val closed: Boolean = false,
    /** Pos TETAP yang dijawab "Tidak jadi" (R-43). */
    val cancelledFixed: Set<Long> = emptySet(),
)

data class Onboarding(
    val startDate: LocalDate,
    val cashStart: Long,
    val savingsStart: Long,
    val emergencyStart: Long,
)

data class EngineConfig(
    val safeThreshold: Long = Defaults.SAFE_THRESHOLD,
    val emergencyTarget: Long = Defaults.EMERGENCY_TARGET,
    val salaryTemplate: Long = Defaults.SALARY_TEMPLATE,
)

data class LedgerInput(
    val categories: List<Category>,
    val onboarding: Onboarding,
    val transactions: List<Tx> = emptyList(),
    val plans: List<MonthPlan> = emptyList(),
    val config: EngineConfig = EngineConfig(),
) {
    fun plan(ym: YearMonth): MonthPlan? = plans.firstOrNull { it.month == ym }

    /** Alokasi template bulan [ym] dari nominal pos yang berlaku (bagian 5, R-95). */
    fun template(ym: YearMonth): List<AllocationLine> = templateOf(categories, ym)
}

fun templateOf(categories: List<Category>, ym: YearMonth): List<AllocationLine> =
    categories.filter { it.isActiveIn(ym) }.sortedBy { it.sortOrder }.mapNotNull { c ->
        val a = c.amountFor(ym) ?: return@mapNotNull null
        if (c.kind == CategoryKind.DAILY) AllocationLine(c.id, dailyAmount = a.dailyAmount ?: 0)
        else AllocationLine(c.id, monthlyAmount = a.monthlyAmount ?: 0)
    }
