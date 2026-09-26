package app.catatuang.security

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Token acak per-instalasi untuk rute dari notifikasi/widget. MainActivity harus exported (launcher),
 * jadi rute dari intent tanpa token yang cocok diabaikan — app lain tidak bisa menyetir navigasi.
 */
object LinkToken {
    const val EXTRA = "link_token"
    @Volatile private var cached: String? = null

    fun get(context: Context): String = cached ?: synchronized(this) {
        cached ?: run {
            val file = File(context.filesDir, "link_token")
            val existing = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.length >= 16 }
            (existing ?: Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also(SecureRandom()::nextBytes))
                .also { file.writeText(it) }).also { cached = it }
        }
    }

    fun matches(context: Context, candidate: String?): Boolean =
        candidate != null && MessageDigest.isEqual(candidate.toByteArray(), get(context).toByteArray())
}
