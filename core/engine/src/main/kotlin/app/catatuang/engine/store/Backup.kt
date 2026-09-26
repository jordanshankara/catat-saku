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
        validate(doc.toSnapshot())?.let { return BackupReadResult.Invalid(it) }
        return BackupReadResult.Ok(doc, preview(doc))
    }

    private const val MAX_TRANSACTIONS = 200_000

    /**
     * Validasi isi (bukan hanya struktur) sebelum Pulihkan: semua enum/tanggal terbaca, nominal tidak negatif,
     * referensi pos ada, dan ledger bisa dihitung. File rusak/buatan tangan tidak boleh membuat app crash
     * setiap dibuka. Null = valid.
     */
    /** Batas sama dengan input (12 digit): mencegah overflow Long saat dijumlah/dikali hari. */
    private fun okAmount(v: Long) = v in 0..999_999_999_999L

    fun validate(snapshot: DataSnapshot): String? {
        return try {
            val s = snapshot.settings
            val start = s.startDate?.let(LocalDate::parse) ?: return "Tanggal mulai tidak ada."
            if (!s.onboardingDone) return "Backup belum selesai onboarding."
            if (listOf(s.cashStart, s.savingsStart, s.emergencyStart, s.safeThreshold, s.emergencyTarget, s.salaryTemplate).any { !okAmount(it) })
                return "Ada nominal pengaturan yang tidak wajar."
            java.time.LocalTime.parse(s.notificationTime)
            if (s.lockTimeoutMinutes !in 1..1440) return "Batas waktu kunci tidak wajar."
            if (snapshot.transactions.size > MAX_TRANSACTIONS) return "Terlalu banyak transaksi."
            val catIds = snapshot.categories.map { it.id }
            if (catIds.toSet().size != catIds.size) return "ID pos ganda."
            val categories = snapshot.categories.map { it.toCategory() }
            if (categories.any { c -> c.amounts.any { !okAmount(it.dailyAmount ?: 0) || !okAmount(it.monthlyAmount ?: 0) } }) return "Nominal pos tidak wajar."
            val known = catIds.toSet()
            snapshot.transactions.forEach { r ->
                val tx = r.toTx()
                if (!okAmount(tx.amount) || !okAmount(kotlin.math.abs(tx.signedAmount ?: 0))) return "Transaksi dengan nominal tidak wajar."
                if (tx.categoryId != null && tx.categoryId !in known) return "Transaksi merujuk pos yang tidak ada."
            }
            if (snapshot.transactions.map { it.id }.toSet().size != snapshot.transactions.size) return "ID transaksi ganda."
            snapshot.monthPlans.forEach { java.time.YearMonth.parse(it.yearMonth); if (it.status !in setOf("OPEN", "CLOSED")) return "Status bulan tidak dikenal." }
            snapshot.allocations.forEach { java.time.YearMonth.parse(it.yearMonth); if (it.categoryId !in known || !okAmount(it.monthlyAmount) || !okAmount(it.dailyAmount ?: 0)) return "Alokasi tidak valid." }
            snapshot.fixedObligations.forEach { java.time.YearMonth.parse(it.yearMonth); if (it.status !in setOf("UNPAID", "PAID", "CANCELLED") || !okAmount(it.estimate)) return "Tagihan tetap tidak valid." }
            snapshot.dayMarks.forEach { LocalDate.parse(it.date) }
            snapshot.closures.forEach { java.time.YearMonth.parse(it.yearMonth) }
            val input = snapshot.toLedgerInput() ?: return "Data tidak lengkap."
            val last = (snapshot.transactions.map { LocalDate.parse(it.date) } + start).max()
            app.catatuang.engine.computeLedger(input, maxOf(last, start))
            null
        } catch (e: Exception) {
            "Isi backup tidak valid: ${e.message ?: e.javaClass.simpleName}"
        }
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

    private val FILE_PATTERN = Regex("""catatuang-backup-\d{8}-\d{4}\.json""")

    /** Auto-backup (bab 11): simpan [keep] file backup terbaru; sisanya (hanya file backup app ini) dihapus. */
    fun backupsToDelete(names: List<String>, keep: Int = 8): List<String> =
        names.filter { FILE_PATTERN.matches(it) }.sortedDescending().drop(keep)

    /** Teks preview Pulihkan, contoh "8 bulan data · 1.240 transaksi · terakhir 23 Sep 2026". */
    fun previewText(p: BackupPreview): String {
        fun group(n: Int) = n.toString().reversed().chunked(3).joinToString(".").reversed()
        val last = p.lastTransaction?.let {
            val m = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")[it.monthValue - 1]
            " · terakhir ${it.dayOfMonth} $m ${it.year}"
        } ?: ""
        return "${p.months} bulan data · ${group(p.transactions)} transaksi$last"
    }

    fun fileName(now: LocalDateTime): String =
        "catatuang-backup-${now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.json"
}
