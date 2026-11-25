package com.neurox.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore integration for secrets that must stay on the device.
 *
 * USES
 * - Wrap the local database passphrase on devices where encrypted storage is
 *   enabled (optional feature, off by default).
 * - Protect the scheduled-backup key so a stolen file cannot be reused.
 *
 * SECURITY NOTES
 * - The key never leaves the Keystore; only wrapped blobs are stored.
 * - User authentication is not required for the key: NEUROX must work offline and
 *   without biometrics. Biometrics gate the UI only, never the data.
 * - A device without a secure lock screen may fall back to software protection;
 *   [isHardwareBacked] reports what happened, and the UI can warn the user.
 */
class KeyStoreManager(
    private val alias: String = DEFAULT_ALIAS,
) {

    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    /** True when a hardware-backed key exists for [alias]. */
    fun isHardwareBacked(): Boolean {
        val key = getExistingKey() ?: return false
        return try {
            val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, PROVIDER)
            val info = factory.getKeySpec(key, android.security.keystore.KeyInfo::class.java)
                as android.security.keystore.KeyInfo
            info.isInsideSecureHardware
        } catch (e: Exception) {
            false
        }
    }

    /** Returns the key, creating it on first use. */
    fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun getExistingKey(): SecretKey? =
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    /**
     * Encrypts [plaintext] and returns `nonce:ciphertext` in Base64.
     * The nonce is random and follows the key, never reused.
     */
    fun encrypt(plaintext: ByteArray): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return Base64.getEncoder().encodeToString(cipher.iv) + ":" +
            Base64.getEncoder().encodeToString(ciphertext)
    }

    /** Reverses [encrypt]. Throws when the blob was not produced by this key. */
    fun decrypt(encoded: String): ByteArray {
        val parts = encoded.split(':')
        require(parts.size == 2) { "wrapped secret is malformed" }
        val nonce = Base64.getDecoder().decode(parts[0])
        val ciphertext = Base64.getDecoder().decode(parts[1])
        val key = getExistingKey() ?: throw IllegalStateException("keystore key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    /** Deletes the key. Data wrapped with it becomes unrecoverable by design. */
    fun deleteKey() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "neurox.local.vault"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
