package app.catatuang.engine

import java.time.LocalDate
import java.time.YearMonth

private const val STANDARD_DAYS = 30

/** J: jatah akhir pekan = budget Transport ÷ 4, dibulatkan ke bawah ke ribuan (R-17). */
fun weekendAllowance(transportBudget: Long): Long = floor1000(transportBudget / 4)

private fun kindOf(categories: List<Category>, id: Long): CategoryKind? = categories.firstOrNull { it.id == id }?.kind

/** R-10: Kebutuhan(M) dengan jumlah hari [days]. */
fun kebutuhan(allocation: List<AllocationLine>, categories: List<Category>, days: Int): Long =
    allocation.sumOf { line ->
        when (kindOf(categories, line.categoryId)) {
            CategoryKind.DAILY -> (line.dailyAmount ?: 0) * days
            null -> 0L
            else -> line.monthlyAmount
        }
    }

/** R-06: KebutuhanStandar(M) = alokasi dengan 30 hari. */
fun kebutuhanStandar(allocation: List<AllocationLine>, categories: List<Category>): Long =
    kebutuhan(allocation, categories, STANDARD_DAYS)

private fun transportBudgetOf(allocation: List<AllocationLine>, categories: List<Category>): Long? {
    val id = categories.firstOrNull { it.weekendMode }?.id ?: return null
    return allocation.firstOrNull { it.categoryId == id }?.monthlyAmount
}

/** R-11: TripTambahan(M) = max(0, S(M) − 4) × J. */
fun tripTambahan(ym: YearMonth, allocation: List<AllocationLine>, categories: List<Category>): Long {
    val budget = transportBudgetOf(allocation, categories) ?: return 0
    return maxOf(0, countSaturdays(ym) - 4) * weekendAllowance(budget)
}

sealed interface ReserveReason {
    val amount: Long
    /** Hari > 30: tambahan jatah HARIAN. */
    data class ExtraDays(val days: Int, override val amount: Long) : ReserveReason
    /** Sabtu > 4: trip tambahan. */
    data class ExtraSaturdays(val saturdays: Int, override val amount: Long) : ReserveReason
    /** Sisa gaji di atas/bawah Kebutuhan standar ditambah bawaan (negatif = mengurangi kekurangan). */
    data class SalaryAndCarry(override val amount: Long) : ReserveReason
}

data class SplitLine(
    val categoryId: Long,
    val kind: CategoryKind,
    val dailyAmount: Long?,
    val days: Int?,
    val total: Long,
)

data class SplitPlan(
    val month: YearMonth,
    val salary: Long,
    val lines: List<SplitLine>,
    val kebutuhan: Long,
    val kebutuhanStandar: Long,
    /** R-06: layar Penyesuaian wajib. */
    val needsAdjustment: Boolean,
    /** Usulan otomatis Penyesuaian (null jika tidak perlu). */
    val proposal: List<AllocationLine>?,
    val bawaan: Long,
    /** R-14 butir 1: Tutup Buku bulan lalu belum dilakukan → bawaan dianggap 0, "Bisa berubah". */
    val bawaanUncertain: Boolean,
    val sakuSisaAwal: Long,
    val tripTambahan: Long,
    val weekendAllowance: Long,
    val targetCadangan: Long,
    val reasons: List<ReserveReason>,
)

/**
 * `planSplit` (7.2): rincian alokasi, kebutuhan Penyesuaian, target cadangan beserta alasannya.
 * [bawaan] null = Tutup Buku bulan lalu belum selesai.
 */
fun planSplit(
    salary: Long,
    month: YearMonth,
    allocation: List<AllocationLine>,
    categories: List<Category>,
    bawaan: Long?,
): SplitPlan {
    val days = month.lengthOfMonth()
    val lines = allocation.mapNotNull { line ->
        val kind = kindOf(categories, line.categoryId) ?: return@mapNotNull null
        if (kind == CategoryKind.DAILY) {
            SplitLine(line.categoryId, kind, line.dailyAmount, days, (line.dailyAmount ?: 0) * days)
        } else {
            SplitLine(line.categoryId, kind, null, null, line.monthlyAmount)
        }
    }
    val keb = kebutuhan(allocation, categories, days)
    val kebStd = kebutuhanStandar(allocation, categories)
    val needs = salary < kebStd
    val carry = bawaan ?: 0
    val saku = salary - keb + carry
    val trip = tripTambahan(month, allocation, categories)
    val target = maxOf(0, trip - saku)

    val reasons = buildList {
        val dailySum = allocation.filter { kindOf(categories, it.categoryId) == CategoryKind.DAILY }.sumOf { it.dailyAmount ?: 0 }
        if (days > STANDARD_DAYS) add(ReserveReason.ExtraDays(days, dailySum * (days - STANDARD_DAYS)))
        if (trip > 0) add(ReserveReason.ExtraSaturdays(countSaturdays(month), trip))
        val salaryPart = salary - kebStd + carry
        if (target > 0 && salaryPart != 0L) add(ReserveReason.SalaryAndCarry(salaryPart))
    }
    return SplitPlan(
        month = month,
        salary = salary,
        lines = lines,
        kebutuhan = keb,
        kebutuhanStandar = kebStd,
        needsAdjustment = needs,
        proposal = if (needs) proposeAdjustment(salary, allocation, categories) else null,
        bawaan = carry,
        bawaanUncertain = bawaan == null,
        sakuSisaAwal = saku,
        tripTambahan = trip,
        weekendAllowance = transportBudgetOf(allocation, categories)?.let(::weekendAllowance) ?: 0,
        targetCadangan = target,
        reasons = reasons,
    )
}

/**
 * R-06 usulan otomatis: pos TETAP tidak disentuh; budget STOK & jatah HARIAN dipotong proporsional
 * (dibulatkan ke bawah ke ribuan, R-17); Nabung dipotong terakhir — hanya bila STOK & HARIAN sudah 0.
 */
fun proposeAdjustment(salary: Long, allocation: List<AllocationLine>, categories: List<Category>): List<AllocationLine> {
    val kebStd = kebutuhanStandar(allocation, categories)
    if (salary >= kebStd) return allocation
    fun kind(l: AllocationLine) = kindOf(categories, l.categoryId)
    val fixed = allocation.filter { kind(it) == CategoryKind.FIXED }.sumOf { it.monthlyAmount }
    val saving = allocation.filter { kind(it) == CategoryKind.SAVING }.sumOf { it.monthlyAmount }
    val flexible = allocation.filter { kind(it) == CategoryKind.DAILY || kind(it) == CategoryKind.STOCK }
    val flexStd = flexible.sumOf { if (kind(it) == CategoryKind.DAILY) (it.dailyAmount ?: 0) * STANDARD_DAYS else it.monthlyAmount }

    val flexBudget = salary - fixed - saving
    if (flexBudget >= 0 && flexStd > 0) {
        return allocation.map { line ->
            when (kind(line)) {
                CategoryKind.DAILY -> line.copy(dailyAmount = floor1000((line.dailyAmount ?: 0) * flexBudget / flexStd))
                CategoryKind.STOCK -> line.copy(monthlyAmount = floor1000(line.monthlyAmount * flexBudget / flexStd))
                else -> line
            }
        }
    }
    // STOK & HARIAN habis; baru Nabung yang dipotong.
    val savingBudget = maxOf(0, salary - fixed)
    return allocation.map { line ->
        when (kind(line)) {
            CategoryKind.DAILY -> line.copy(dailyAmount = 0)
            CategoryKind.STOCK -> line.copy(monthlyAmount = 0)
            CategoryKind.SAVING -> line.copy(monthlyAmount = if (saving == 0L) 0 else floor1000(line.monthlyAmount * savingBudget / saving))
            else -> line
        }
    }
}

data class OnboardingPlan(
    val month: YearMonth,
    val remainingDays: Int,
    val allocation: List<AllocationLine>,
    val dailyTotal: Long,
    val transportWindows: Int,
    val transportAmount: Long,
    val stockProrata: Map<Long, Long>,
    val kebutuhan: Long,
    val sakuSisaAwal: Long,
    val tripTambahan: Long,
    val targetCadangan: Long,
)

/** Akhir pekan bulan [ym] yang belum lewat pada [start] (termasuk yang sedang berjalan). */
internal fun windowsFrom(ym: YearMonth, start: LocalDate?): List<WeekendWindow> =
    saturdaysOf(ym).map(::WeekendWindow).filter { start == null || !it.sunday.isBefore(start) }

/**
 * 8.12 langkah 5 — periode awal: HARIAN dari [start] s/d akhir bulan; Transport = akhir pekan tersisa
 * (maks 4) × J; STOK lain prorata sisaHari ÷ D(M) dibulatkan ke bawah; TETAP & Nabung dianggap beres.
 */
fun planOnboarding(start: LocalDate, cash: Long, categories: List<Category>): OnboardingPlan {
    val ym = YearMonth.from(start)
    val d = ym.lengthOfMonth()
    val remaining = d - start.dayOfMonth + 1
    val windows = windowsFrom(ym, start)
    val funded = minOf(4, windows.size)
    val template = templateOf(categories, ym)
    val stock = mutableMapOf<Long, Long>()
    var transportAmount = 0L
    var j = 0L
    val alloc = template.mapNotNull { line ->
        val cat = categories.first { it.id == line.categoryId }
        when (cat.kind) {
            CategoryKind.DAILY -> line
            CategoryKind.STOCK -> if (cat.weekendMode) {
                j = weekendAllowance(line.monthlyAmount)
                transportAmount = funded * j
                line.copy(monthlyAmount = transportAmount)
            } else {
                val p = floor1000(line.monthlyAmount * remaining / d)
                stock[line.categoryId] = p
                line.copy(monthlyAmount = p)
            }
            CategoryKind.FIXED, CategoryKind.SAVING -> null
        }
    }
    val dailyTotal = alloc.filter { categories.first { c -> c.id == it.categoryId }.kind == CategoryKind.DAILY }
        .sumOf { (it.dailyAmount ?: 0) * remaining }
    val keb = dailyTotal + transportAmount + stock.values.sum()
    val saku = cash - keb
    val trip = maxOf(0, windows.size - 4) * j
    return OnboardingPlan(
        month = ym,
        remainingDays = remaining,
        allocation = alloc,
        dailyTotal = dailyTotal,
        transportWindows = funded,
        transportAmount = transportAmount,
        stockProrata = stock,
        kebutuhan = keb,
        sakuSisaAwal = saku,
        tripTambahan = trip,
        targetCadangan = maxOf(0, trip - saku),
    )
}
