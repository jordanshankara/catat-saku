package app.catatuang.feature.fixed

import app.catatuang.engine.Category
import app.catatuang.engine.LedgerState
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.dayMonth
import app.catatuang.ui.format.rp
import java.time.LocalDate

/** Satu kewajiban pos TETAP bulan berjalan (R-40) untuk kartu Beranda. */
data class FixedDue(
    val categoryId: Long,
    val key: String,
    val name: String,
    val estimate: Long,
    val dueDate: LocalDate,
    val status: String,
    val tone: Tone,
)

/** Kewajiban BELUM BAYAR bulan berjalan; bulan onboarding tidak punya (D-19). */
fun unpaidFixed(categories: List<Category>, state: LedgerState, today: LocalDate): List<FixedDue> {
    val cur = state.current ?: return emptyList()
    if (cur.isOnboarding) return emptyList()
    return cur.fixed.filter { !it.isPaid && !it.cancelled }.mapNotNull { f ->
        val cat = categories.firstOrNull { it.id == f.categoryId } ?: return@mapNotNull null
        val due = cur.month.atDay((cat.dueDay ?: 1).coerceIn(1, cur.month.lengthOfMonth()))
        val (status, tone) = when {
            due.isBefore(today) -> "Lewat jatuh tempo ${dayMonth(due)}" to Tone.DANGER
            due == today -> "Jatuh tempo hari ini" to Tone.WARNING
            else -> "Jatuh tempo ${dayMonth(due)}" to Tone.NEUTRAL
        }
        FixedDue(cat.id, cat.key, cat.name, f.estimate, due, status, tone)
    }.sortedBy { it.dueDate }
}

/** R-42 pratinjau selisih estimasi − aktual ke Saku Sisa. */
fun fixedDiffText(estimate: Long, paid: Long): Pair<String, Tone> {
    val diff = estimate - paid
    return when {
        diff > 0 -> "Hemat ${rp(diff)} dari estimasi → Saku Sisa +${rp(diff)}" to Tone.SUCCESS
        diff < 0 -> "Lebih ${rp(-diff)} dari estimasi → diambil dari Saku Sisa" to Tone.WARNING
        else -> "Pas sesuai estimasi" to Tone.SUCCESS
    }
}
