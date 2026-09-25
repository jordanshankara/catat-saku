package app.catatuang.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.catatuang.BuildConfig
import app.catatuang.data.CatatRepository
import app.catatuang.engine.store.BackupCodec
import app.catatuang.engine.store.BackupDocument
import java.io.File
import java.time.LocalDateTime

/** Tulis file ke cache/share lalu buka share sheet (bab 3: FileProvider). */
fun shareFile(context: Context, name: String, mime: String, write: (File) -> Unit) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, name)
    write(file)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Kirim $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** "Kirim backup" (bab 11): JSON seluruh data kecuali hash PIN. */
suspend fun shareBackup(context: Context, repo: CatatRepository) {
    val now = LocalDateTime.now()
    val doc = BackupDocument.of(repo.exportSnapshot(), BuildConfig.VERSION_NAME, now)
    val text = BackupCodec.encode(doc)
    shareFile(context, BackupCodec.fileName(now), "application/json") { it.writeText(text) }
}
