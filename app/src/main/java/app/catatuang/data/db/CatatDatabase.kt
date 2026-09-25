package app.catatuang.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CategoryEntity::class,
        CategoryAmountEntity::class,
        MonthPlanEntity::class,
        MonthAllocationEntity::class,
        TxEntity::class,
        FixedObligationEntity::class,
        DayMarkEntity::class,
        MonthClosureEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CatatDatabase : RoomDatabase() {
    abstract fun dao(): CatatDao

    companion object {
        /** Migrasi skema wajib ditulis di sini (bab 11). Dilarang fallbackToDestructiveMigration. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): CatatDatabase =
            Room.databaseBuilder(context, CatatDatabase::class.java, "catatuang.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
