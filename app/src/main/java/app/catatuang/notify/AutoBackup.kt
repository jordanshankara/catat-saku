package app.catatuang.notify

import android.content.Context
import android.net.Uri
import app.catatuang.AppContainer
import app.catatuang.export.BackupFolder
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Auto-backup mingguan (bab 11): Minggu 23:00 ke folder pilihan pengguna. Dilewati selama Mode Uji. */
object AutoBackup {
    suspend fun runIfDue(context: Context, container: AppContainer, today: LocalDate = LocalDate.now()) {
        if (today.dayOfWeek != DayOfWeek.SUNDAY) return
        if (container.clock.override.value != null) return
        run(context, container)
    }

    /** Backup ke folder sekarang juga; status disimpan untuk ditampilkan di Pengaturan. */
    suspend fun run(context: Context, container: AppContainer): Result<String> {
        val repo = container.repository
        val uri = repo.backupFolderUri.first() ?: return Result.failure(IllegalStateException("Folder backup belum dipilih"))
        val result = runCatching { BackupFolder.backupNow(context, repo, Uri.parse(uri)) }
        val stamp = LocalDateTime.now().withSecond(0).withNano(0)
        repo.setLastFolderBackup(result.fold({ "$stamp|$it" }, { "$stamp|GAGAL: ${it.message ?: "tidak diketahui"}" }))
        return result
    }
}
