package app.catatuang.security

import app.catatuang.data.StoredPin
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Bagian 3 & 8.14: PIN disimpan sebagai hash PBKDF2 + salt acak; PIN asli tidak pernah disimpan. */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun isValidFormat(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }

    fun hash(pin: String, random: SecureRandom = SecureRandom()): StoredPin {
        val salt = ByteArray(16).also(random::nextBytes)
        return StoredPin(encode(derive(pin, salt)), encode(salt))
    }

    fun verify(pin: String, stored: StoredPin): Boolean {
        val salt = Base64.getDecoder().decode(stored.salt)
        val expected = Base64.getDecoder().decode(stored.hash)
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}

/**
 * 8.14: 5× salah → jeda 30 detik, berlipat dua setiap 5× salah berikutnya (30, 60, 120, …).
 * Murni (tanpa Android) agar bisa dites.
 */
class PinAttempts(private val now: () -> Long) {
    var failures = 0
        private set
    private var lockedUntil = 0L

    fun remainingLockMillis(): Long = maxOf(0, lockedUntil - now())

    /** @return true bila PIN boleh dicoba sekarang. */
    fun canTry(): Boolean = remainingLockMillis() == 0L

    fun onFailure() {
        failures++
        if (failures % 5 == 0) {
            val round = failures / 5 - 1
            lockedUntil = now() + 30_000L * (1L shl round.coerceAtMost(10))
        }
    }

    /** Pulihkan hitungan yang tersimpan (8.14: jeda tetap berlaku walau app ditutup paksa). */
    fun restore(failures: Int, lockedUntil: Long) {
        this.failures = failures
        this.lockedUntil = lockedUntil
    }

    val lockedUntilMillis: Long get() = lockedUntil

    fun onSuccess() {
        failures = 0
        lockedUntil = 0
    }
}
