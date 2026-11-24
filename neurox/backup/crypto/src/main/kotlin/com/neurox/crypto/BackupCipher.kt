package com.neurox.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable vault primitive. The Android restore workflow is a separate acceptance gate. */
object BackupCipher {
    const val MAX_PLAINTEXT = 4 * 1024 * 1024
    private const val HEADER_SIZE = 4 + 1 + 4 + 16 + 12
    private const val ITERATIONS = 310_000
    private val magic = byteArrayOf(0x4e, 0x58, 0x56, 0x42) // NXVB

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(plaintext.size in 1..MAX_PLAINTEXT) { "Invalid backup size" }
        require(password.size in 10..1024) { "Use a password of 10 to 1024 characters" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).put(magic).put(1.toByte()).putInt(ITERATIONS).put(salt).put(nonce).array()
        val key = derive(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(plaintext)
        } finally { key.fill(0) }
    }
    /** Authentication failure throws. No caller may report a successful restore on failure. */
    fun decrypt(backup: ByteArray, password: CharArray): ByteArray {
        require(backup.size in (HEADER_SIZE + 17)..(HEADER_SIZE + MAX_PLAINTEXT + 16)) { "Invalid backup size" }
        require(password.size in 10..1024) { "Invalid password length" }
        val buffer = ByteBuffer.wrap(backup)
        val actualMagic = ByteArray(4).also(buffer::get)
        require(actualMagic.contentEquals(magic)) { "Not a NEUROX backup" }
        require(buffer.get().toInt() == 1) { "Unsupported backup version" }
        val iterations = buffer.int
        require(iterations in 200_000..600_000) { "Unsupported derivation cost" }
        val salt = ByteArray(16).also(buffer::get)
        val nonce = ByteArray(12).also(buffer::get)
        val header = backup.copyOfRange(0, HEADER_SIZE)
        val key = derive(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            cipher.doFinal(backup, HEADER_SIZE, backup.size - HEADER_SIZE)
        } finally { key.fill(0) }
    }
    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
