package app.catatuang.ui.format

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val ID_LOCALE: Locale = Locale.forLanguageTag("id-ID")

private fun groupThousands(value: Long): String {
    val digits = value.toString()
    val sb = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.toString()
}

/** "Rp 45.000", "−Rp 15.000" (8.13 AmountText). */
fun rp(amount: Long): String = if (amount < 0) "−Rp ${groupThousands(-amount)}" else "Rp ${groupThousands(amount)}"

/** Angka tanpa "Rp": "45.000". */
fun digits(amount: Long): String = if (amount < 0) "−${groupThousands(-amount)}" else groupThousands(amount)

/** Versi ringkas: "Rp 45rb", "Rp 1,05 jt", "Rp 500". */
fun rpShort(amount: Long): String {
    val sign = if (amount < 0) "−" else ""
    val a = kotlin.math.abs(amount)
    val body = when {
        a >= 1_000_000 -> {
            val hundredths = a / 10_000
            val whole = hundredths / 100
            val frac = hundredths % 100
            when {
                frac == 0L -> "$whole jt"
                frac % 10 == 0L -> "$whole,${frac / 10} jt"
                else -> "$whole,${"%02d".format(frac)} jt"
            }
        }
        a >= 1_000 && a % 1_000 == 0L -> "${a / 1_000}rb"
        a >= 1_000 -> "${groupThousands(a)}"
        else -> "$a"
    }
    return "${sign}Rp $body"
}

/** "Senin, 28 September". */
fun fullDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ID_LOCALE))

/** "Sab 3 Okt". */
fun shortDate(date: LocalDate): String {
    val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, ID_LOCALE).removeSuffix(".")
    val month = date.month.getDisplayName(TextStyle.SHORT, ID_LOCALE).removeSuffix(".")
    return "$day ${date.dayOfMonth} $month"
}

/** "September", "Oktober 2027" (tahun hanya bila beda dengan [reference]). */
fun monthName(ym: YearMonth, reference: YearMonth? = null): String {
    val name = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, ID_LOCALE).replaceFirstChar { it.uppercase() }
    return if (reference != null && reference.year != ym.year) "$name ${ym.year}" else name
}

/** "Sen", "Sel", … untuk sumbu grafik. */
fun dayShort(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, ID_LOCALE).removeSuffix(".").take(3)
