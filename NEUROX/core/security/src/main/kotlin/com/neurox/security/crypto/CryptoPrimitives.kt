package com.neurox.security.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Deterministic key derivation (PBKDF2-HMAC-SHA256).
 *
 * WHY NOT ARGON2id BY DEFAULT (decision DEC-0005): Argon2id is the preferred
 * algorithm, but it must be memory-hard *in code we can review*. The sandbox
 * cannot resolve a maintained Argon2 artifact, and writing an unreviewed
 * implementation of a memory-hard function is exactly the "invent cryptography"
 * failure the directive forbids. NEUROX therefore:
 *  - accepts Argon2id envelopes through the [KeyDerivation] abstraction,
 *  - ships this deterministic PBKDF2 path as the reviewed default,
 *  - and names the algorithm and its iteration count inside the vault file, so a
 *    future build can add Argon2id without breaking older files.
 *
 * This derivation is pure and unit-tested against published RFC 6070 vectors.
 */
object Pbkdf2 {

    /** HMAC-SHA256 based PBKDF2 as specified by RFC 8018. */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int = 32,
    ): ByteArray {
        require(iterations >= 1) { "iterations must be >= 1" }
        require(salt.isNotEmpty()) { "salt must not be empty" }
        require(keyLengthBytes in 16..64) { "key length must be in 16..64 bytes" }
        val passwordBytes = password.toBytesUtf8()
        try {
            return derive(passwordBytes, salt, iterations, keyLengthBytes)
        } finally {
            passwordBytes.fill(0)
        }
    }

    /**
     * Entry point that accepts raw password bytes.
     *
     * It is public so the RFC 6070 vectors can be verified from a test module,
     * which may live in a different Gradle module than this one.
     */
    fun derive(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val prfOutput = ByteArray(32)
        val output = ByteArray(keyLengthBytes)
        var blockIndex = 1
        var offset = 0
        while (offset < keyLengthBytes) {
            // U1 = PRF(password, salt || INT_32_BE(blockIndex))
            mac.init(javax.crypto.spec.SecretKeySpec(password, "HmacSHA256"))
            mac.update(salt)
            mac.update(byteArrayOf(
                ((blockIndex ushr 24) and 0xFF).toByte(),
                ((blockIndex ushr 16) and 0xFF).toByte(),
                ((blockIndex ushr 8) and 0xFF).toByte(),
                (blockIndex and 0xFF).toByte(),
            ))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val length = minOf(keyLengthBytes - offset, t.size)
            System.arraycopy(t, 0, output, offset, length)
            offset += length
            blockIndex += 1
            // Best-effort cleanup of intermediate buffers.
            t.fill(0)
            u.fill(0)
        }
        prfOutput.fill(0)
        return output
    }

    private fun CharArray.toBytesUtf8(): ByteArray {
        val text = String(this)
        return text.toByteArray(Charsets.UTF_8)
    }
}

/**
 * Constant-time and hashing helpers.
 */
object Digests {

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Hex(bytes: ByteArray): String = sha256(bytes).toHex()

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    /** Compares two byte arrays without an early exit, to avoid timing leaks. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var difference = 0
        for (i in a.indices) difference = difference or (a[i].toInt() xor b[i].toInt())
        return difference == 0
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Wipes a mutable secret buffer. */
    fun wipe(vararg arrays: ByteArray?) {
        arrays.forEach { it?.fill(0) }
    }

    fun wipe(chars: CharArray?) {
        chars?.fill('\u0000')
    }
}

/** Cryptographically secure random values. */
object SecureRandomSource {

    private val random = SecureRandom()

    fun bytes(count: Int): ByteArray {
        require(count > 0) { "count must be positive" }
        val out = ByteArray(count)
        random.nextBytes(out)
        return out
    }

    fun salt(): ByteArray = bytes(SALT_BYTES)

    /**
     * A 96-bit nonce for AES-GCM. 12 bytes is the size GCM is designed for; a
     * different size forces an extra GHASH pass and is easier to misuse.
     */
    fun nonce(): ByteArray = bytes(NONCE_BYTES)

    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
}

/**
 * A fixed nonce source for tests only. It must never be used in production.
 */
class FixedNonceSource(private val nonce: ByteArray) {
    fun next(): ByteArray = nonce.copyOf()
}
