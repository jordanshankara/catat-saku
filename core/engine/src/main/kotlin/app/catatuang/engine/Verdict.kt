package app.catatuang.engine

enum class VerdictKind { TANPA_GAJI, BONCOS, PAS_PASAN, BERHASIL_NABUNG }

/** [amount]: BONCOS = total ambil DARURAT; BERHASIL_NABUNG = Nabung rutin + sisa yang dipindah ke kantong. */
data class Verdict(val kind: VerdictKind, val amount: Long)

/** R-80. */
fun computeVerdict(
    noSalary: Boolean,
    daruratWithdrawn: Long,
    sakuAkhir: Long,
    hutangDibawa: Long,
    nabungRutin: Long,
    movedToPots: Long,
    safeThreshold: Long,
): Verdict = when {
    noSalary -> Verdict(VerdictKind.TANPA_GAJI, 0)
    daruratWithdrawn > 0 -> Verdict(VerdictKind.BONCOS, daruratWithdrawn)
    sakuAkhir < safeThreshold || hutangDibawa > 0 -> Verdict(VerdictKind.PAS_PASAN, 0)
    else -> Verdict(VerdictKind.BERHASIL_NABUNG, nabungRutin + movedToPots)
}
