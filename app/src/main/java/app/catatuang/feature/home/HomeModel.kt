package app.catatuang.feature.home

import app.catatuang.engine.Category
import app.catatuang.engine.CategoryKind
import app.catatuang.engine.LedgerInput
import app.catatuang.engine.LedgerState
import app.catatuang.engine.SafetyStatus
import app.catatuang.engine.SalaryStatus
import app.catatuang.engine.StockStatus
import app.catatuang.engine.daysUntilClosing
import app.catatuang.engine.planSplit
import app.catatuang.feature.fixed.FixedDue
import app.catatuang.feature.fixed.unpaidFixed
import app.catatuang.feature.salary.reasonText
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.fullDate
import app.catatuang.ui.format.monthName
import app.catatuang.ui.format.rp
import app.catatuang.ui.format.rpShort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class HeroChip(val label: String, val value: String, val tone: Tone)

data class HeroUi(
    val talangan: Boolean,
    val label: String,
    val amount: String,
    val month: String,
    val status: String?,
    val statusTone: Tone,
    val chips: List<HeroChip>,
    val cadangan: String?,
    val cadanganFraction: Float,
    val pending: String?,
    val talanganDetail: String?,
    val talanganShortfall: String?,
)

data class BannerUi(val title: String, val subtitle: String)

data class TileUi(
    val categoryId: Long,
    val key: String,
    val name: String,
    val status: String,
    val statusTone: Tone,
    val secondLine: String?,
    val progress: Float,
    val progressTone: Tone,
    val badge: String?,
    val badgeTone: Tone,
    val wide: Boolean,
    val daily: Boolean,
)

/** Ringkasan kantong di Beranda; [*Shadow] = porsi talangan bayangan (R-04). */
data class PotsUi(val tabungan: Long, val tabunganShadow: Long, val danaDarurat: Long, val danaDaruratShadow: Long, val emergencyBelowTarget: Boolean)

data class HomeUi(
    val today: LocalDate,
    val date: String,
    val greeting: String,
    val hero: HeroUi,
    val salaryBanner: BannerUi?,
    val closingBanner: String?,
    /** R-14 butir 2: banner tanggal 1, sekali, bisa ditutup. */
    val cadanganBanner: BannerUi?,
    val pots: PotsUi,
    val fixedDue: List<FixedDue>,
    /** R-57: "Udah transfer Rp 160.000 ke tabungan?" bila checklist belum dicentang. */
    val checklist: String?,
    val tiles: List<TileUi>,
    val notices: List<Pair<String, Tone>>,
)

private fun safetyText(status: SafetyStatus, sisaBebas: Long): Pair<String, Tone> = when (status) {
    SafetyStatus.AMAN -> "Aman · sisa bebas ${rp(sisaBebas)}" to Tone.SUCCESS
    SafetyStatus.WASPADA -> "Waspada · sisa bebas ${rp(sisaBebas)}" to Tone.WARNING
    SafetyStatus.MINUS -> "Minus · sisa bebas ${rp(sisaBebas)}" to Tone.DANGER
}

fun buildHomeUi(
    nickname: String,
    input: LedgerInput,
    state: LedgerState,
    today: LocalDate,
    checklistMonth: String? = null,
    cadanganDismissed: String? = null,
): HomeUi {
    val cur = state.current!!
    val talangan = state.talangan
    val hero = if (state.salaryStatus == SalaryStatus.MISSING && talangan != null) {
        val parts = listOfNotNull(
            "Sisa ${rp(talangan.fromSaku)}".takeIf { talangan.fromSaku > 0 },
            "Tabungan ${rp(talangan.fromTabungan)}".takeIf { talangan.fromTabungan > 0 },
            "Dana Darurat ${rp(talangan.fromDanaDarurat)}".takeIf { talangan.fromDanaDarurat > 0 },
        )
        HeroUi(
            talangan = true,
            label = "Gaji belum masuk",
            amount = "Ditalangi ${rp(talangan.amount)}",
            month = monthName(state.currentMonth),
            status = null,
            statusTone = Tone.WARNING,
            chips = listOf(HeroChip("Tutup buku", "${daysUntilClosing(today)} hari lagi", Tone.NEUTRAL)),
            cadangan = null,
            cadanganFraction = 0f,
            pending = null,
            talanganDetail = parts.joinToString(" · ").ifEmpty { null },
            talanganShortfall = talangan.shortfall.takeIf { it > 0 }?.let { "Dana talangan habis, kurang ${rp(it)}" },
        )
    } else {
        val (statusText, statusTone) = safetyText(state.sisaBebasStatus, state.sisaBebas)
        val chips = buildList {
            if (state.totalHutang > 0) add(HeroChip("Hutang harian", rp(state.totalHutang), Tone.DANGER))
            add(HeroChip("Tutup buku", "${daysUntilClosing(today)} hari lagi", Tone.NEUTRAL))
        }
        val target = state.targetCadangan ?: 0
        HeroUi(
            talangan = false,
            label = "Saku Sisa",
            amount = rp(state.sakuSisa),
            month = monthName(state.currentMonth),
            status = statusText,
            statusTone = statusTone,
            chips = chips,
            cadangan = if (target > 0) "Cadangan: terkumpul ${rp(state.cadanganTerkumpul)} / ${rp(target)}" else null,
            cadanganFraction = if (target > 0) state.cadanganTerkumpul.toFloat() / target else 0f,
            pending = state.pendingMonth?.takeIf { state.saldoPending > 0 }?.let { "Gaji ${monthName(it, state.currentMonth)} aman · aktif tgl 1" },
            talanganDetail = null,
            talanganShortfall = null,
        )
    }

    val next = state.currentMonth.plusMonths(1)
    val salaryBanner = when {
        state.salaryStatus == SalaryStatus.MISSING ->
            BannerUi("Gajian sudah masuk?", "Gaji ${monthName(state.currentMonth)} belum dicatat")
        today.dayOfMonth >= 27 && state.saldoPending == 0L -> {
            val plan = planSplit(input.config.salaryTemplate, next, input.template(next), input.categories, bawaan = null)
            val sub = if (plan.targetCadangan > 0) "${monthName(next, state.currentMonth)} kurang ${rpShort(plan.targetCadangan)} — cek sebelum split"
            else "Catat gaji ${monthName(next, state.currentMonth)} begitu masuk"
            BannerUi("Gajian sudah masuk?", sub)
        }
        else -> null
    }

    val prev = state.months[state.currentMonth.minusMonths(1)]
    val closingBanner = prev?.takeIf { it.monthEnded && !it.closed }?.let { "Tutup buku ${monthName(it.month)} belum dilakukan" }

    val tiles = buildTiles(input.categories, state, today)

    val cadTarget = state.targetCadangan ?: 0
    val cadanganBanner = if (today.dayOfMonth == 1 && cadTarget > 0 && cadanganDismissed != state.currentMonth.toString()) {
        val reasons = cur.salary?.takeIf { !cur.isOnboarding }?.let { salary ->
            planSplit(salary, cur.month, cur.allocation, input.categories, cur.bawaan).reasons.joinToString(" · ") { reasonText(it, input.categories) }
        }
        BannerUi("Cadangan ${monthName(state.currentMonth)}: ${rp(cadTarget)}", listOfNotNull(reasons?.ifEmpty { null }, "kumpulkan lewat hemat biar tabungan aman").joinToString(" — "))
    } else null

    val checklist = checklistMonth?.let(YearMonth::parse)?.let { m ->
        val amount = input.transactions.filter { it.routine && it.refYearMonth == m }.sumOf { it.amount }
        amount.takeIf { it > 0 }?.let { "Udah transfer ${rp(it)} ke tabungan?" }
    }

    val talanganNow = state.talangan
    val pots = PotsUi(
        tabungan = state.tabunganShown,
        tabunganShadow = talanganNow?.fromTabungan ?: 0,
        danaDarurat = state.danaDaruratShown,
        danaDaruratShadow = talanganNow?.fromDanaDarurat ?: 0,
        emergencyBelowTarget = state.danaDarurat < input.config.emergencyTarget,
    )
    val fixedDue = unpaidFixed(input.categories, state, today)

    val notices = buildList {
        state.largeDebt.forEach { id ->
            val name = input.categories.firstOrNull { it.id == id }?.name ?: "pos"
            add("Hutang ${name.lowercase()} sudah lebih dari 3× jatah. Pertimbangkan Mode Darurat." to Tone.DANGER)
        }
        if (state.salaryStatus != SalaryStatus.MISSING && state.sakuSisa < 0) {
            add("Saku Sisa minus — akhir bulan ditutup dari Tabungan, lalu Dana Darurat." to Tone.DANGER)
        }
        hero.talanganShortfall?.let { add(it to Tone.DANGER) }
        closingBanner?.let { add(it to Tone.WARNING) }
        hero.cadangan?.takeIf { state.cadanganTerkumpul < (state.targetCadangan ?: 0) }?.let { add(it to Tone.WARNING) }
        fixedDue.filter { !it.dueDate.isAfter(today) }.forEach { add("${it.name} jatuh tempo — belum dibayar" to Tone.WARNING) }
    }

    return HomeUi(
        today = today,
        date = fullDate(today),
        greeting = "Halo, $nickname",
        hero = hero,
        salaryBanner = salaryBanner,
        closingBanner = closingBanner,
        cadanganBanner = cadanganBanner,
        pots = pots,
        fixedDue = fixedDue,
        checklist = checklist,
        tiles = tiles,
        notices = notices,
    )
}

private val WEEKEND = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** 8.1: grid pos HARIAN & STOK aktif; Jumat–Minggu Transport pertama (R-33); Lain-lain lebar penuh. */
fun buildTiles(categories: List<Category>, state: LedgerState, today: LocalDate): List<TileUi> {
    val active = categories.filter { it.isActiveIn(state.currentMonth) && (it.kind == CategoryKind.DAILY || it.kind == CategoryKind.STOCK) }
        .sortedBy { it.sortOrder }
    val ordered = if (today.dayOfWeek in WEEKEND) active.sortedByDescending { it.weekendMode } else active
    val (wide, grid) = ordered.partition { it.key == "lain" }
    return (grid + wide).mapNotNull { c -> tileFor(c, state, wide = c.key == "lain") }
}

private fun stockTone(status: StockStatus) = when (status) {
    StockStatus.NORMAL -> Tone.INFO
    StockStatus.WASPADA -> Tone.WARNING
    StockStatus.LEBIH -> Tone.DANGER
}

private fun tileFor(c: Category, state: LedgerState, wide: Boolean): TileUi? {
    when {
        c.kind == CategoryKind.DAILY -> {
            val d = state.daily.firstOrNull { it.categoryId == c.id } ?: return null
            val over = d.used - d.jatah
            val (status, tone) = when {
                over > 0 -> "Lebih ${rpShort(over)} hari ini" to Tone.DANGER
                d.used == d.jatah && d.used > 0 -> "Pas hari ini · ${rpShort(d.jatah).removePrefix("Rp ")}" to Tone.SUCCESS
                else -> "Sisa hari ini ${rpShort(d.remaining)}" to Tone.NEUTRAL
            }
            val (badge, badgeTone) = when {
                d.largeDebt -> "Hutang besar ${rpShort(d.hutang).removePrefix("Rp ")}" to Tone.DANGER
                d.hutang > 0 -> "Hutang ${rpShort(d.hutang).removePrefix("Rp ")}" to Tone.DANGER
                else -> null to Tone.NEUTRAL
            }
            return TileUi(
                c.id, c.key, c.name, status, tone, null,
                progress = if (d.jatah > 0) d.used.toFloat() / d.jatah else 0f,
                progressTone = if (over > 0) Tone.DANGER else if (d.used == d.jatah && d.used > 0) Tone.SUCCESS else Tone.INFO,
                badge = badge, badgeTone = badgeTone, wide = wide, daily = true,
            )
        }
        c.weekendMode -> {
            val t = state.transport ?: return null
            val open = t.openWindow
            return if (open != null) {
                val allowance = t.allowance
                val text = if (open.funded) "Akhir pekan ini: ${rpShort(open.used)} / ${rpShort(allowance)}"
                else "Akhir pekan ke-5: ${rpShort(open.used)} (dari reservasi)"
                val status = t.windowStatus ?: StockStatus.NORMAL
                TileUi(
                    c.id, c.key, c.name, text, if (status == StockStatus.NORMAL) Tone.NEUTRAL else stockTone(status), null,
                    progress = if (allowance > 0) open.used.toFloat() / allowance else 0f,
                    progressTone = stockTone(status),
                    badge = "Trip ${t.tripsTaken}/${t.saturdays}", badgeTone = Tone.INFO, wide = wide, daily = false,
                )
            } else {
                val budget = state.current?.transportBudget ?: 0
                TileUi(
                    c.id, c.key, c.name, "Trip ${t.tripsTaken} dari ${t.saturdays}", Tone.NEUTRAL,
                    secondLine = "Sisa budget ${rpShort(t.remainingBudget)}",
                    progress = if (budget > 0) (budget - t.remainingBudget).toFloat() / budget else 0f,
                    progressTone = Tone.INFO,
                    badge = "Trip ${t.tripsTaken}/${t.saturdays}", badgeTone = Tone.INFO, wide = wide, daily = false,
                )
            }
        }
        else -> {
            val s = state.stock.firstOrNull { it.categoryId == c.id }
            val used = s?.used ?: 0
            val budget = s?.budget ?: (state.current?.allocation?.firstOrNull { it.categoryId == c.id }?.monthlyAmount ?: 0)
            val status = s?.status ?: StockStatus.NORMAL
            val prefix = when (status) {
                StockStatus.NORMAL -> ""
                StockStatus.WASPADA -> "Waspada · "
                StockStatus.LEBIH -> "Lebih · "
            }
            return TileUi(
                c.id, c.key, c.name, "${prefix}Terpakai ${rpShort(used)} / ${rpShort(budget)}",
                if (status == StockStatus.NORMAL) Tone.NEUTRAL else stockTone(status), null,
                progress = if (budget > 0) used.toFloat() / budget else 0f,
                progressTone = stockTone(status),
                badge = null, badgeTone = Tone.NEUTRAL, wide = wide, daily = false,
            )
        }
    }
}
