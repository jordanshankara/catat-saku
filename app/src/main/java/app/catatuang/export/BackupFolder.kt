package app.catatuang.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.catatuang.BuildConfig
import app.catatuang.data.CatatRepository
import app.catatuang.engine.store.BackupCodec
import app.catatuang.engine.store.BackupDocument
import java.time.LocalDateTime

/**
 * Backup ke folder pilihan pengguna (bab 11, SAF `ACTION_OPEN_DOCUMENT_TREE`). Hanya file
 * `catatuang-backup-*.json` di folder itu yang pernah dihapus (simpan 8 terbaru).
 */
object BackupFolder {
    fun write(context: Context, treeUri: Uri, name: String, text: String) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val doc = DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: error("Tidak bisa membuat file di folder backup")
        resolver.openOutputStream(doc, "w")?.use { it.write(text.toByteArray()) } ?: error("Tidak bisa menulis file backup")
    }

    private fun children(context: Context, treeUri: Uri): List<Pair<String, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(childrenUri, cols, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        } ?: emptyList()
    }

    fun prune(context: Context, treeUri: Uri, keep: Int = 8) {
        val files = children(context, treeUri)
        val delete = BackupCodec.backupsToDelete(files.map { it.second }, keep).toSet()
        files.filter { it.second in delete }.forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id)) }
        }
    }

    /** Tulis backup sekarang ke folder tersimpan lalu rapikan. Mengembalikan nama file. */
    suspend fun backupNow(context: Context, repo: CatatRepository, treeUri: Uri): String {
        val now = LocalDateTime.now()
        val name = BackupCodec.fileName(now)
        val text = BackupCodec.encode(BackupDocument.of(repo.exportSnapshot(), BuildConfig.VERSION_NAME, now))
        write(context, treeUri, name, text)
        prune(context, treeUri)
        return name
    }
}
