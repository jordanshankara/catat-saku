package app.catatuang.engine.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** File backup (bab 11). Hash PIN tidak pernah ikut. */
@Serializable
data class BackupDocument(
    val schemaVersion: Int = BackupCodec.SCHEMA_VERSION,
    val appVersion: String,
    val exportedAt: String,
    val settings: SettingsRecord,
    val categories: List<CategoryRecord>,
    val monthPlans: List<MonthPlanRecord>,
    val allocations: List<AllocationRecord>,
    val transactions: List<TxRecord>,
    val fixedObligations: List<FixedObligationRecord>,
    val dayMarks: List<DayMarkRecord>,
    val closures: List<ClosureRecord>,
) {
    fun toSnapshot() = DataSnapshot(settings, categories, monthPlans, allocations, transactions, fixedObligations, dayMarks, closures)

    companion object {
        fun of(snapshot: DataSnapshot, appVersion: String, exportedAt: LocalDateTime) = BackupDocument(
            appVersion = appVersion,
            exportedAt = exportedAt.toString(),
            settings = snapshot.settings,
            categories = snapshot.categories,
            monthPlans = snapshot.monthPlans,
            allocations = snapshot.allocations,
            transactions = snapshot.transactions,
            fixedObligations = snapshot.fixedObligations,
            dayMarks = snapshot.dayMarks,
            closures = snapshot.closures,
        )
    }
}

/** Ringkasan untuk layar konfirmasi Pulihkan: "8 bulan data · 1.240 transaksi · terakhir 23 Sep 2026". */
data class BackupPreview(val months: Int, val transactions: Int, val lastTransaction: LocalDate?)

sealed interface BackupReadResult {
    data class Ok(val document: BackupDocument, val preview: BackupPreview) : BackupReadResult
    data class Invalid(val reason: String) : BackupReadResult
}

object BackupCodec {
    const val SCHEMA_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun encode(doc: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), doc)

    /** Validasi `schemaVersion` & struktur, migrasi JSON versi lama bila perlu (bab 11). */
    fun decode(text: String): BackupReadResult {
        val root: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return BackupReadResult.Invalid("Bukan file backup Catat Uang (JSON tidak valid).")
        }
        val version = try {
            root["schemaVersion"]?.jsonPrimitive?.int
        } catch (e: Exception) {
            null
        } ?: return BackupReadResult.Invalid("schemaVersion tidak ada.")
        if (version > SCHEMA_VERSION) return BackupReadResult.Invalid("Backup dari versi app yang lebih baru (skema $version).")
        if (version < 1) return BackupReadResult.Invalid("schemaVersion tidak dikenal: $version.")
        val migrated = migrate(root, version)
        val doc = try {
            json.decodeFromJsonElement(BackupDocument.serializer(), migrated)
        } catch (e: Exception) {
            return BackupReadResult.Invalid("Struktur backup rusak: ${e.message}")
        }
        return BackupReadResult.Ok(doc, preview(doc))
    }

    /** Tempat migrasi JSON antar-skema. Skema 1 adalah versi pertama. */
    private fun migrate(root: JsonObject, from: Int): JsonObject = when (from) {
        SCHEMA_VERSION -> root
        else -> root
    }

    fun preview(doc: BackupDocument): BackupPreview {
        val dates = doc.transactions.map { LocalDate.parse(it.date) }
        val months = dates.map { it.withDayOfMonth(1) }.distinct().size
        return BackupPreview(months, doc.transactions.size, dates.maxOrNull())
    }

    fun fileName(now: LocalDateTime): String =
        "catatuang-backup-${now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.json"
}
