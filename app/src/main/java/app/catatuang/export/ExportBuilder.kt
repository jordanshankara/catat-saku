package app.catatuang.export

import android.content.Context
import app.catatuang.engine.ExportRange
import app.catatuang.engine.LedgerInput
import app.catatuang.engine.LedgerState
import app.catatuang.engine.VerdictKind
import app.catatuang.engine.exportSheets
import app.catatuang.engine.monthlyReport
import app.catatuang.engine.weekStart
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class ExportFormat(val ext: String, val mime: String) {
    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

private val COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd")

/** Rentang export (bab 10): Minggu ini / Bulan [pilih] / Custom. */
object Ranges {
    fun week(today: LocalDate): ExportRange {
        val s = weekStart(today)
        return ExportRange("minggu-${s.format(COMPACT)}", s, s.plusDays(6), null)
    }

    fun month(ym: YearMonth) = ExportRange(ym.toString(), ym.atDay(1), ym.atEndOfMonth(), ym)

    fun custom(from: LocalDate, to: LocalDate): ExportRange {
        val (a, b) = if (from <= to) from to to else to to from
        return ExportRange("${a.format(COMPACT)}-${b.format(COMPACT)}", a, b, null)
    }
}

/** Nama file bab 10: `catatuang-{rentang}-{yyyyMMdd}.pdf|xlsx`. */
fun exportFileName(range: ExportRange, format: ExportFormat, today: LocalDate): String =
    "catatuang-${range.label}-${today.format(COMPACT)}.${format.ext}"

fun buildExport(context: Context, input: LedgerInput, state: LedgerState, range: ExportRange, format: ExportFormat): ByteArray {
    val sheets = exportSheets(input, state, range)
    return when (format) {
        ExportFormat.XLSX -> Xlsx.write(sheets)
        ExportFormat.PDF -> {
            val report = range.month?.let { monthlyReport(input, state, it) }
            val verdict = report?.let { r ->
                val v = r.summary.verdict
                val label = when (v.kind) {
                    VerdictKind.TANPA_GAJI -> "TANPA GAJI"
                    VerdictKind.BONCOS -> "BONCOS Rp ${v.amount.toString().reversed().chunked(3).joinToString(".").reversed()}"
                    VerdictKind.PAS_PASAN -> "PAS-PASAN"
                    VerdictKind.BERHASIL_NABUNG -> "BERHASIL NABUNG"
                }
                label to r.provisional
            }
            val title = when {
                range.month != null -> "Laporan ${range.month}"
                range.label.startsWith("minggu") -> "Laporan mingguan"
                else -> "Laporan"
            }
            PdfReport.render(context, title, "${range.from} s/d ${range.to}", verdict, sheets)
        }
    }
}
