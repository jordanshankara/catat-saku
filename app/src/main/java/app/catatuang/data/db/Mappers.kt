package app.catatuang.data.db

import app.catatuang.engine.store.AllocationRecord
import app.catatuang.engine.store.CategoryAmountRecord
import app.catatuang.engine.store.CategoryRecord
import app.catatuang.engine.store.ClosureRecord
import app.catatuang.engine.store.DayMarkRecord
import app.catatuang.engine.store.FixedObligationRecord
import app.catatuang.engine.store.MonthPlanRecord
import app.catatuang.engine.store.TxRecord

/* Entity Room ↔ record engine (1:1). Logika ada di core/engine; lapisan ini hanya memindahkan kolom. */

fun CategoryEntity.toRecord(amounts: List<CategoryAmountEntity>) = CategoryRecord(
    id, key, name, kind, weekendMode, iconKey, colorKey, sortOrder, activeFrom, archivedFrom, dueDay, hasSlots,
    amounts.filter { it.categoryId == id }.map { CategoryAmountRecord(it.effectiveFrom, it.dailyAmount, it.monthlyAmount) },
)

fun CategoryRecord.toEntity() = CategoryEntity(
    id, key, name, kind, weekendMode, iconKey, colorKey, sortOrder, activeFrom, archivedFrom, dueDay, hasSlots,
)

fun CategoryRecord.amountEntities() = amounts.map { CategoryAmountEntity(0, id, it.effectiveFrom, it.dailyAmount, it.monthlyAmount) }

fun MonthPlanEntity.toRecord() = MonthPlanRecord(yearMonth, salaryTxId, noSalary, status, createdAt)
fun MonthPlanRecord.toEntity() = MonthPlanEntity(yearMonth, salaryTxId, noSalary, status, createdAt)

fun MonthAllocationEntity.toRecord() = AllocationRecord(yearMonth, categoryId, dailyAmount, monthlyAmount)
fun AllocationRecord.toEntity() = MonthAllocationEntity(yearMonth, categoryId, dailyAmount, monthlyAmount)

fun TxEntity.toRecord() = TxRecord(
    id, date, createdAt, updatedAt, type, categoryId, amount, signedAmount, slot, note,
    destination, pot, reason, refYearMonth, routine, closingOf, incomeKind, testMode,
)

fun TxRecord.toEntity() = TxEntity(
    id, date, createdAt, updatedAt, type, categoryId, amount, signedAmount, slot, note,
    destination, pot, reason, refYearMonth, routine, closingOf, incomeKind, testMode,
)

fun FixedObligationEntity.toRecord() = FixedObligationRecord(yearMonth, categoryId, estimate, status, paidTxId)
fun FixedObligationRecord.toEntity() = FixedObligationEntity(yearMonth, categoryId, estimate, status, paidTxId)

fun DayMarkEntity.toRecord() = DayMarkRecord(date, doneMarked)
fun DayMarkRecord.toEntity() = DayMarkEntity(date, doneMarked)

fun MonthClosureEntity.toRecord() = ClosureRecord(yearMonth, verdict, verdictAmount, snapshotJson, reconciledActual, closedAt)
fun ClosureRecord.toEntity() = MonthClosureEntity(yearMonth, verdict, verdictAmount, snapshotJson, reconciledActual, closedAt)
