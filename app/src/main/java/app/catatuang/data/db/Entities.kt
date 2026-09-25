package app.catatuang.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/* Entity Room sesuai 7.4. Tanggal ISO "2026-10-01", bulan "2026-10", enum sebagai nama. */

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val key: String,
    val name: String,
    val kind: String,
    val weekendMode: Boolean,
    val iconKey: String,
    val colorKey: String,
    val sortOrder: Int,
    val activeFrom: String?,
    val archivedFrom: String?,
    val dueDay: Int?,
    val hasSlots: Boolean,
)

/** Nominal pos per periode berlaku (R-95). `effectiveFrom` null = sejak awal. */
@Entity(tableName = "category_amount", indices = [Index("categoryId")])
data class CategoryAmountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val effectiveFrom: String?,
    val dailyAmount: Long?,
    val monthlyAmount: Long?,
)

@Entity(tableName = "month_plan")
data class MonthPlanEntity(
    @PrimaryKey val yearMonth: String,
    val salaryTxId: Long?,
    val noSalary: Boolean,
    val status: String,
    val createdAt: Long,
)

@Entity(tableName = "month_allocation", primaryKeys = ["yearMonth", "categoryId"])
data class MonthAllocationEntity(
    val yearMonth: String,
    val categoryId: Long,
    val dailyAmount: Long?,
    val monthlyAmount: Long,
)

@Entity(tableName = "tx", indices = [Index("date"), Index("categoryId")])
data class TxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
    val type: String,
    val categoryId: Long?,
    val amount: Long,
    val signedAmount: Long?,
    val slot: String?,
    val note: String?,
    val destination: String?,
    val pot: String?,
    val reason: String?,
    val refYearMonth: String?,
    val routine: Boolean,
    val closingOf: String?,
    val incomeKind: String?,
    val testMode: Boolean,
)

@Entity(tableName = "fixed_obligation", primaryKeys = ["yearMonth", "categoryId"])
data class FixedObligationEntity(
    val yearMonth: String,
    val categoryId: Long,
    val estimate: Long,
    val status: String,
    val paidTxId: Long?,
)

@Entity(tableName = "day_mark")
data class DayMarkEntity(
    @PrimaryKey val date: String,
    val doneMarked: Boolean,
)

@Entity(tableName = "month_closure")
data class MonthClosureEntity(
    @PrimaryKey val yearMonth: String,
    val verdict: String,
    val verdictAmount: Long,
    val snapshotJson: String,
    val reconciledActual: Long?,
    val closedAt: Long,
)
