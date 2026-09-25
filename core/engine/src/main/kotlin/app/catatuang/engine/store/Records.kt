package app.catatuang.engine.store

import app.catatuang.engine.AllocationLine
import app.catatuang.engine.Category
import app.catatuang.engine.CategoryAmount
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.Defaults
import app.catatuang.engine.Destination
import app.catatuang.engine.EngineConfig
import app.catatuang.engine.IncomeKind
import app.catatuang.engine.LedgerInput
import app.catatuang.engine.MonthPlan
import app.catatuang.engine.Onboarding
import app.catatuang.engine.Pot
import app.catatuang.engine.Slot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.WithdrawReason
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/*
 * Bentuk data tersimpan (7.4) sebagai record datar. Dipakai tiga arah: entity Room ↔ record,
 * record ↔ JSON backup (bab 11), record → LedgerInput untuk core/engine. Tanggal ISO-8601
 * ("2026-10-01"), bulan "2026-10", enum sebagai nama.
 */

@Serializable
data class SettingsRecord(
    val nickname: String = Defaults.NICKNAME,
    val salaryTemplate: Long = Defaults.SALARY_TEMPLATE,
    val expectedPayday: Int = Defaults.EXPECTED_PAYDAY,
    val notificationTime: String = "22:00",
    val safeThreshold: Long = Defaults.SAFE_THRESHOLD,
    val emergencyTarget: Long = Defaults.EMERGENCY_TARGET,
    val theme: String = "LIGHT",
    val startDate: String? = null,
    val cashStart: Long = 0,
    val savingsStart: Long = 0,
    val emergencyStart: Long = 0,
    val lockTimeoutMinutes: Int = Defaults.LOCK_TIMEOUT_MINUTES,
    val blockScreenshots: Boolean = false,
    val onboardingDone: Boolean = false,
)

@Serializable
data class CategoryAmountRecord(
    val effectiveFrom: String? = null,
    val dailyAmount: Long? = null,
    val monthlyAmount: Long? = null,
)

@Serializable
data class CategoryRecord(
    val id: Long,
    val key: String,
    val name: String,
    val kind: String,
    val weekendMode: Boolean = false,
    val iconKey: String = "",
    val colorKey: String = "",
    val sortOrder: Int = 0,
    val activeFrom: String? = null,
    val archivedFrom: String? = null,
    val dueDay: Int? = null,
    val hasSlots: Boolean = false,
    val amounts: List<CategoryAmountRecord> = emptyList(),
)

@Serializable
data class MonthPlanRecord(
    val yearMonth: String,
    val salaryTxId: Long? = null,
    val noSalary: Boolean = false,
    val status: String = "OPEN",
    val createdAt: Long = 0,
)

@Serializable
data class AllocationRecord(
    val yearMonth: String,
    val categoryId: Long,
    val dailyAmount: Long? = null,
    val monthlyAmount: Long = 0,
)

@Serializable
data class TxRecord(
    val id: Long,
    val date: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val type: String,
    val categoryId: Long? = null,
    val amount: Long,
    val signedAmount: Long? = null,
    val slot: String? = null,
    val note: String? = null,
    val destination: String? = null,
    val pot: String? = null,
    val reason: String? = null,
    val refYearMonth: String? = null,
    val routine: Boolean = false,
    val closingOf: String? = null,
    val incomeKind: String? = null,
    /** Data Mode Uji Tanggal (8.11); dihapus saat mode dimatikan. */
    val testMode: Boolean = false,
)

@Serializable
data class FixedObligationRecord(
    val yearMonth: String,
    val categoryId: Long,
    val estimate: Long,
    val status: String = "UNPAID",
    val paidTxId: Long? = null,
)

@Serializable
data class DayMarkRecord(val date: String, val doneMarked: Boolean)

@Serializable
data class ClosureRecord(
    val yearMonth: String,
    val verdict: String,
    val verdictAmount: Long,
    val snapshotJson: String,
    val reconciledActual: Long? = null,
    val closedAt: Long,
)

/** Seluruh data aplikasi (kecuali PIN). Juga isi file backup. */
@Serializable
data class DataSnapshot(
    val settings: SettingsRecord,
    val categories: List<CategoryRecord>,
    val monthPlans: List<MonthPlanRecord>,
    val allocations: List<AllocationRecord>,
    val transactions: List<TxRecord>,
    val fixedObligations: List<FixedObligationRecord>,
    val dayMarks: List<DayMarkRecord>,
    val closures: List<ClosureRecord>,
)

// ---- Konversi record → domain engine ----

fun CategoryRecord.toCategory() = Category(
    id = id,
    key = key,
    name = name,
    kind = CategoryKind.valueOf(kind),
    amounts = amounts.map { CategoryAmount(it.effectiveFrom?.let(YearMonth::parse), it.dailyAmount, it.monthlyAmount) },
    weekendMode = weekendMode,
    hasSlots = hasSlots,
    dueDay = dueDay,
    sortOrder = sortOrder,
    activeFrom = activeFrom?.let(YearMonth::parse),
    archivedFrom = archivedFrom?.let(YearMonth::parse),
)

fun Category.toRecord(iconKey: String = key, colorKey: String = key) = CategoryRecord(
    id = id,
    key = key,
    name = name,
    kind = kind.name,
    weekendMode = weekendMode,
    iconKey = iconKey,
    colorKey = colorKey,
    sortOrder = sortOrder,
    activeFrom = activeFrom?.toString(),
    archivedFrom = archivedFrom?.toString(),
    dueDay = dueDay,
    hasSlots = hasSlots,
    amounts = amounts.map { CategoryAmountRecord(it.effectiveFrom?.toString(), it.dailyAmount, it.monthlyAmount) },
)

fun TxRecord.toTx() = Tx(
    id = id,
    date = LocalDate.parse(date),
    type = TxType.valueOf(type),
    amount = amount,
    categoryId = categoryId,
    signedAmount = signedAmount,
    slot = slot?.let(Slot::valueOf),
    destination = destination?.let(Destination::valueOf),
    pot = pot?.let(Pot::valueOf),
    reason = reason?.let(WithdrawReason::valueOf),
    refYearMonth = refYearMonth?.let(YearMonth::parse),
    routine = routine,
    closingOf = closingOf?.let(YearMonth::parse),
    incomeKind = incomeKind?.let(IncomeKind::valueOf),
    note = note,
    createdAt = createdAt,
)

fun Tx.toRecord(updatedAt: Long = createdAt, testMode: Boolean = false) = TxRecord(
    id = id,
    date = date.toString(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    type = type.name,
    categoryId = categoryId,
    amount = amount,
    signedAmount = signedAmount,
    slot = slot?.name,
    note = note,
    destination = destination?.name,
    pot = pot?.name,
    reason = reason?.name,
    refYearMonth = refYearMonth?.toString(),
    routine = routine,
    closingOf = closingOf?.toString(),
    incomeKind = incomeKind?.name,
    testMode = testMode,
)

/** Record → input `computeLedger`. Null bila onboarding belum selesai (belum ada tanggal mulai). */
fun DataSnapshot.toLedgerInput(): LedgerInput? {
    val start = settings.startDate?.let(LocalDate::parse) ?: return null
    val allocByMonth = allocations.groupBy { it.yearMonth }
    val cancelledByMonth = fixedObligations.filter { it.status == "CANCELLED" }.groupBy { it.yearMonth }
    val months = (monthPlans.map { it.yearMonth } + allocByMonth.keys + cancelledByMonth.keys).distinct()
    val plans = months.map { m ->
        val plan = monthPlans.firstOrNull { it.yearMonth == m }
        MonthPlan(
            month = YearMonth.parse(m),
            allocation = allocByMonth[m]?.map { AllocationLine(it.categoryId, it.dailyAmount, it.monthlyAmount) },
            noSalary = plan?.noSalary == true,
            closed = plan?.status == "CLOSED",
            cancelledFixed = cancelledByMonth[m].orEmpty().map { it.categoryId }.toSet(),
        )
    }
    return LedgerInput(
        categories = categories.map { it.toCategory() },
        onboarding = Onboarding(start, settings.cashStart, settings.savingsStart, settings.emergencyStart),
        transactions = transactions.map { it.toTx() },
        plans = plans,
        config = EngineConfig(settings.safeThreshold, settings.emergencyTarget, settings.salaryTemplate),
    )
}
