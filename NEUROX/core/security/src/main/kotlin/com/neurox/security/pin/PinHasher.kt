package com.neurox.security.pin

import com.neurox.security.crypto.Digests
import com.neurox.security.crypto.Pbkdf2
import com.neurox.security.crypto.SecureRandomSource
import java.util.Base64

/**
 * Salted, iterated hashing for optional profile PINs.
 *
 * FORMAT: `pbkdf2-sha256$<iterations>$<saltBase64>$<hashBase64>`
 * The format string is versioned by its prefix, so a future algorithm can be added
 * and old records can still be verified.
 *
 * The PIN itself is never stored, never logged, and never written to a backup.
 */
object PinHasher {

    const val DEFAULT_ITERATIONS = 120_000
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8
    private const val PREFIX = "pbkdf2-sha256"

    /** True when [pin] has an accepted length and contains digits only. */
    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): String {
        require(isValidPin(pin)) { "PIN length or characters are not allowed" }
        require(iterations >= 10_000)
        val salt = SecureRandomSource.salt()
        val derived = Pbkdf2.deriveKey(pin.toCharArray(), salt, iterations, 32)
        return "$PREFIX\$$iterations\$${base64(salt)}\$${Digests.base64Of(derived)}"
    }

    /**
     * Verifies [pin] against [stored]. Returns false for malformed records instead
     * of throwing, so a corrupted database cannot lock the UI in an error loop.
     */
    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations < 10_000) return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = try {
            Pbkdf2.deriveKey(pin.toCharArray(), salt, iterations, expected.size)
        } catch (e: IllegalArgumentException) {
            return false
        }
        return Digests.constantTimeEquals(expected, actual)
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}

/** Base64 helper kept next to the hasher so hashing stays self-contained. */
private fun Digests.base64Of(bytes: ByteArray): String =
    java.util.Base64.getEncoder().encodeToString(bytes)
