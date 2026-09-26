package app.catatuang.engine

import app.catatuang.engine.store.BackupCodec
import app.catatuang.engine.store.BackupPreview
import app.catatuang.engine.store.toRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BackupFilesTest {
    @Test @DisplayName("Auto-backup: simpan 8 terbaru, hanya file backup app yang dihapus")
    fun prune() {
        val backups = (1..10).map { "catatuang-backup-202609%02d-2300.json".format(it) }
        val others = listOf("foto.jpg", "catatuang-backup-lama.json", "catatan.json")
        val delete = BackupCodec.backupsToDelete(others + backups.shuffled())
        assertEquals(listOf("catatuang-backup-20260902-2300.json", "catatuang-backup-20260901-2300.json"), delete)
        assertEquals(emptyList<String>(), BackupCodec.backupsToDelete(backups.take(8)))
    }

    @Test @DisplayName("Preview Pulihkan")
    fun preview() {
        assertEquals("8 bulan data · 1.240 transaksi · terakhir 23 Sep 2026", BackupCodec.previewText(BackupPreview(8, 1240, d("2026-09-23"))))
        assertEquals("0 bulan data · 0 transaksi", BackupCodec.previewText(BackupPreview(0, 0, null)))
    }
}

class BackupValidationTest {
    private fun validDoc(): String {
        val s = Scenario.funded("2026-11").apply { expense("2026-11-02", Defaults.ID_MAKAN, 45_000) }
        val input = s.input()
        val snap = app.catatuang.engine.store.DataSnapshot(
            settings = app.catatuang.engine.store.SettingsRecord(startDate = input.onboarding.startDate.toString(), onboardingDone = true),
            categories = input.categories.map { it.toRecord() },
            monthPlans = emptyList(), allocations = emptyList(),
            transactions = input.transactions.map { it.toRecord() },
            fixedObligations = emptyList(), dayMarks = emptyList(), closures = emptyList(),
        )
        return app.catatuang.engine.store.BackupCodec.encode(app.catatuang.engine.store.BackupDocument.of(snap, "t", java.time.LocalDateTime.of(2026, 11, 3, 9, 0)))
    }

    private fun invalid(text: String) = app.catatuang.engine.store.BackupCodec.decode(text) is app.catatuang.engine.store.BackupReadResult.Invalid

    @Test @DisplayName("Backup rusak/berbahaya ditolak sebelum Pulihkan (tidak membuat app crash)")
    fun rejectsBadContent() {
        val ok = validDoc()
        org.junit.jupiter.api.Assertions.assertFalse(invalid(ok))
        org.junit.jupiter.api.Assertions.assertTrue(invalid(ok.replace("\"type\":\"EXPENSE\"", "\"type\":\"HACK\"")))
        org.junit.jupiter.api.Assertions.assertTrue(invalid(ok.replace("\"amount\":45000", "\"amount\":-45000")))
        org.junit.jupiter.api.Assertions.assertTrue(invalid(ok.replace("\"date\":\"2026-11-02\"", "\"date\":\"2026-13-40\"")))
        org.junit.jupiter.api.Assertions.assertTrue(invalid(ok.replace("\"onboardingDone\":true", "\"onboardingDone\":false")))
        org.junit.jupiter.api.Assertions.assertTrue(invalid(ok.replace("\"kind\":\"DAILY\"", "\"kind\":\"ROOT\"")))
        org.junit.jupiter.api.Assertions.assertTrue(invalid(ok.replace("\"amount\":45000", "\"amount\":9223372036854775807")))
        org.junit.jupiter.api.Assertions.assertTrue(invalid("{\"schemaVersion\":1}"))
        org.junit.jupiter.api.Assertions.assertTrue(invalid("bukan json"))
    }
}
