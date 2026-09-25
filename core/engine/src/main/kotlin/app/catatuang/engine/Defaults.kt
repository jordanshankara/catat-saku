package app.catatuang.engine

/** Konfigurasi default (bagian 5). */
object Defaults {
    const val SALARY_TEMPLATE = 3_300_000L
    const val EXPECTED_PAYDAY = 29
    const val SAFE_THRESHOLD = 50_000L
    const val EMERGENCY_TARGET = 1_000_000L
    const val LOCK_TIMEOUT_MINUTES = 5
    const val NICKNAME = "Ko"

    const val ID_MAKAN = 1L
    const val ID_BUAH = 2L
    const val ID_TRANSPORT = 3L
    const val ID_PROTEIN = 4L
    const val ID_LAIN = 5L
    const val ID_IURAN_MESS = 6L
    const val ID_AI = 7L
    const val ID_NABUNG = 8L

    val categories: List<Category> = listOf(
        Category(ID_MAKAN, "makan", "Makan", CategoryKind.DAILY, listOf(CategoryAmount(null, dailyAmount = 50_000)), hasSlots = true, sortOrder = 1),
        Category(ID_BUAH, "buah", "Buah", CategoryKind.DAILY, listOf(CategoryAmount(null, dailyAmount = 10_000)), sortOrder = 2),
        Category(ID_TRANSPORT, "transport", "Transport OE", CategoryKind.STOCK, listOf(CategoryAmount(null, monthlyAmount = 480_000)), weekendMode = true, sortOrder = 3),
        Category(ID_PROTEIN, "protein", "Protein", CategoryKind.STOCK, listOf(CategoryAmount(null, monthlyAmount = 200_000)), sortOrder = 4),
        Category(ID_LAIN, "lain", "Lain-lain", CategoryKind.STOCK, listOf(CategoryAmount(null, monthlyAmount = 150_000)), sortOrder = 5),
        Category(ID_IURAN_MESS, "iuran_mess", "Iuran Mess", CategoryKind.FIXED, listOf(CategoryAmount(null, monthlyAmount = 120_000)), dueDay = 1, sortOrder = 6),
        Category(ID_AI, "ai", "AI", CategoryKind.FIXED, listOf(CategoryAmount(null, monthlyAmount = 390_000)), dueDay = 1, sortOrder = 7),
        Category(ID_NABUNG, "nabung", "Nabung", CategoryKind.SAVING, listOf(CategoryAmount(null, monthlyAmount = 160_000)), sortOrder = 8),
    )
}
