package app.catatuang.feature.closing

import app.catatuang.engine.MonthSummary
import app.catatuang.engine.Verdict
import app.catatuang.engine.VerdictKind
import app.catatuang.ui.components.Tone
import app.catatuang.ui.format.rp

/** Langkah Tutup Buku (6.10); DONE = verdict final. */
enum class ClosingStep(val label: String) {
    CHECK("Catatan terakhir"),
    SALARY("Status gaji"),
    VERDICT("Verdict sementara"),
    RECONCILE("Cocokkan saldo"),
    DEBT("Hutang harian"),
    FIXED("Tagihan tetap"),
    DISTRIBUTE("Saku Sisa akhir"),
    BACKUP("Backup"),
    DONE("Selesai"),
}

/** Langkah yang berlaku untuk bulan ini: Status gaji hanya bila bulan itu tidak punya gaji (bukan bulan onboarding). */
fun closingSteps(month: MonthSummary): List<ClosingStep> =
    ClosingStep.entries.filter { it != ClosingStep.SALARY || (month.salary == null && !month.isOnboarding) }

fun verdictTitle(v: Verdict): String = when (v.kind) {
    VerdictKind.TANPA_GAJI -> "TANPA GAJI"
    VerdictKind.BONCOS -> "BONCOS ${rp(v.amount)}"
    VerdictKind.PAS_PASAN -> "PAS-PASAN"
    VerdictKind.BERHASIL_NABUNG -> "BERHASIL NABUNG"
}

fun verdictTone(v: Verdict): Tone = when (v.kind) {
    VerdictKind.TANPA_GAJI -> Tone.NEUTRAL
    VerdictKind.BONCOS -> Tone.DANGER
    VerdictKind.PAS_PASAN -> Tone.WARNING
    VerdictKind.BERHASIL_NABUNG -> Tone.SUCCESS
}

/** R-81: baris kartu verdict. */
fun verdictLines(m: MonthSummary, reconciled: Long?): List<Pair<String, String>> = buildList {
    val masuk = (m.salary ?: 0) + m.gajiTambahan + m.pemasukan
    add("Pemasukan (gaji + tambahan)" to rp(masuk))
    add("Pengeluaran riil" to rp(m.pengeluaranRiil))
    add("Tabungan masuk / keluar" to "${rp(m.tabunganMasuk)} / ${rp(m.tabunganKeluar)}")
    if (m.danaDaruratTerpakai > 0) add("Dana darurat terpakai" to rp(m.danaDaruratTerpakai))
    add("Hutang dibawa" to rp(m.hutangDibawa))
    add("Saku Sisa akhir" to rp(m.sakuSebelumDistribusi))
    if (m.verdict.kind == VerdictKind.BERHASIL_NABUNG && m.verdict.amount > 0) add("Ditabung (Nabung rutin + sisa)" to rp(m.verdict.amount))
    reconciled?.let { add("Cocokkan saldo" to if (it == 0L) "Pas" else (if (it > 0) "Selisih lebih ${rp(it)}" else "Tidak tercatat ${rp(-it)}")) }
}
