package app.catatuang.engine

import app.catatuang.engine.store.BackupCodec
import app.catatuang.engine.store.BackupPreview
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
