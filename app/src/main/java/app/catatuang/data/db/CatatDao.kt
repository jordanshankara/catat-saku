package app.catatuang.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatatDao {
    @Query("SELECT * FROM category ORDER BY sortOrder") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category_amount") fun categoryAmounts(): Flow<List<CategoryAmountEntity>>
    @Query("SELECT * FROM month_plan") fun monthPlans(): Flow<List<MonthPlanEntity>>
    @Query("SELECT * FROM month_allocation") fun allocations(): Flow<List<MonthAllocationEntity>>
    @Query("SELECT * FROM tx ORDER BY date, createdAt, id") fun transactions(): Flow<List<TxEntity>>
    @Query("SELECT * FROM fixed_obligation") fun fixedObligations(): Flow<List<FixedObligationEntity>>
    @Query("SELECT * FROM day_mark") fun dayMarks(): Flow<List<DayMarkEntity>>
    @Query("SELECT * FROM month_closure") fun closures(): Flow<List<MonthClosureEntity>>

    @Query("SELECT * FROM category") suspend fun categoriesNow(): List<CategoryEntity>
    @Query("SELECT * FROM category_amount") suspend fun categoryAmountsNow(): List<CategoryAmountEntity>
    @Query("SELECT * FROM month_plan") suspend fun monthPlansNow(): List<MonthPlanEntity>
    @Query("SELECT * FROM month_allocation") suspend fun allocationsNow(): List<MonthAllocationEntity>
    @Query("SELECT * FROM tx") suspend fun transactionsNow(): List<TxEntity>
    @Query("SELECT * FROM fixed_obligation") suspend fun fixedObligationsNow(): List<FixedObligationEntity>
    @Query("SELECT * FROM day_mark") suspend fun dayMarksNow(): List<DayMarkEntity>
    @Query("SELECT * FROM month_closure") suspend fun closuresNow(): List<MonthClosureEntity>

    @Insert suspend fun insertTx(tx: TxEntity): Long
    @Upsert suspend fun upsertTx(tx: TxEntity)
    @Query("DELETE FROM tx WHERE id = :id") suspend fun deleteTx(id: Long)
    @Query("DELETE FROM tx WHERE testMode = 1") suspend fun deleteTestModeTx()

    @Upsert suspend fun upsertCategories(items: List<CategoryEntity>)
    @Insert suspend fun insertCategoryAmounts(items: List<CategoryAmountEntity>)
    @Upsert suspend fun upsertMonthPlan(item: MonthPlanEntity)
    @Upsert suspend fun upsertAllocations(items: List<MonthAllocationEntity>)
    @Query("DELETE FROM month_allocation WHERE yearMonth = :yearMonth") suspend fun deleteAllocations(yearMonth: String)
    @Upsert suspend fun upsertFixedObligations(items: List<FixedObligationEntity>)
    @Upsert suspend fun upsertDayMark(item: DayMarkEntity)
    @Upsert suspend fun upsertClosure(item: MonthClosureEntity)

    @Query("DELETE FROM category") suspend fun clearCategories()
    @Query("DELETE FROM category_amount") suspend fun clearCategoryAmounts()
    @Query("DELETE FROM month_plan") suspend fun clearMonthPlans()
    @Query("DELETE FROM month_allocation") suspend fun clearAllocations()
    @Query("DELETE FROM tx") suspend fun clearTransactions()
    @Query("DELETE FROM fixed_obligation") suspend fun clearFixedObligations()
    @Query("DELETE FROM day_mark") suspend fun clearDayMarks()
    @Query("DELETE FROM month_closure") suspend fun clearClosures()

    @Insert suspend fun insertTxs(items: List<TxEntity>)
    @Insert suspend fun insertMonthPlans(items: List<MonthPlanEntity>)
    @Insert suspend fun insertAllocations(items: List<MonthAllocationEntity>)
    @Insert suspend fun insertFixedObligations(items: List<FixedObligationEntity>)
    @Insert suspend fun insertDayMarks(items: List<DayMarkEntity>)
    @Insert suspend fun insertClosures(items: List<MonthClosureEntity>)
    @Insert suspend fun insertCategories(items: List<CategoryEntity>)

    /** Pulihkan (bab 11): ganti seluruh data dalam satu transaksi database. */
    @Transaction
    suspend fun replaceAll(
        categories: List<CategoryEntity>,
        amounts: List<CategoryAmountEntity>,
        plans: List<MonthPlanEntity>,
        allocations: List<MonthAllocationEntity>,
        txs: List<TxEntity>,
        obligations: List<FixedObligationEntity>,
        dayMarks: List<DayMarkEntity>,
        closures: List<MonthClosureEntity>,
    ) {
        clearTransactions(); clearAllocations(); clearMonthPlans(); clearFixedObligations()
        clearDayMarks(); clearClosures(); clearCategoryAmounts(); clearCategories()
        insertCategories(categories); insertCategoryAmounts(amounts); insertMonthPlans(plans)
        insertAllocations(allocations); insertTxs(txs); insertFixedObligations(obligations)
        insertDayMarks(dayMarks); insertClosures(closures)
    }

    /**
     * Konfirmasi gaji (8.6): transaksi gaji + Nabung rutin, alokasi bulan target, dan `month_plan`
     * dalam satu transaksi database. [allocations] null = tidak mengubah alokasi (gaji tambahan / bulan onboarding).
     */
    @Transaction
    suspend fun saveSalary(txs: List<TxEntity>, month: String, allocations: List<MonthAllocationEntity>?, plan: MonthPlanEntity?): List<Long> {
        val ids = txs.map { insertTx(it) }
        if (allocations != null) { deleteAllocations(month); upsertAllocations(allocations) }
        if (plan != null) upsertMonthPlan(plan.copy(salaryTxId = ids.first()))
        return ids
    }

    @Query("DELETE FROM month_plan WHERE yearMonth = :yearMonth") suspend fun deleteMonthPlan(yearMonth: String)

    /** Urungkan konfirmasi gaji. */
    @Transaction
    suspend fun undoSalary(ids: List<Long>, month: String, allocations: List<MonthAllocationEntity>?, plan: MonthPlanEntity?) {
        ids.forEach { deleteTx(it) }
        deleteAllocations(month)
        if (allocations != null) upsertAllocations(allocations)
        if (plan != null) upsertMonthPlan(plan) else deleteMonthPlan(month)
    }

    /** R-41: bayar pos TETAP — transaksi + status LUNAS kewajibannya. */
    @Transaction
    suspend fun payFixed(tx: TxEntity, obligation: FixedObligationEntity): Long {
        val id = insertTx(tx)
        upsertFixedObligations(listOf(obligation.copy(status = "PAID", paidTxId = id)))
        return id
    }

    @Transaction
    suspend fun unpayFixed(txId: Long, obligation: FixedObligationEntity) {
        deleteTx(txId)
        upsertFixedObligations(listOf(obligation.copy(status = "UNPAID", paidTxId = null)))
    }

    /** Beberapa transaksi dalam satu transaksi database (mis. Mode Darurat, 7.3). */
    @Transaction
    suspend fun insertTxBatch(items: List<TxEntity>): List<Long> = items.map { insertTx(it) }
}
