package ir.aegis.core.crypto

import ir.aegis.core.model.ConsentLocalSharePacket
import ir.aegis.core.model.FullBackupSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class EncryptedCipherPayload(
    val ciphertextBase64: String,
    val ivBase64: String,
    val saltBase64: String,
    val plaintextSha256: String,
)

@Serializable
data class AegisEncryptedBackupEnvelope(
    val formatVersion: Int = 1,
    val createdAtMillis: Long,
    val kdfAlgorithm: String = "PBKDF2WithHmacSHA256",
    val kdfIterations: Int = CryptoEngine.DEFAULT_KDF_ITERATIONS,
    val cipherAlgorithm: String = "AES/GCM/NoPadding",
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val plaintextSha256: String,
)

object CryptoEngine {
    const val DEFAULT_KDF_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    private val random = SecureRandom()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun generateSaltBase64(): String {
        val bytes = ByteArray(SALT_BYTES)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hashPin(pin: String, saltBase64: String, iterations: Int = DEFAULT_KDF_ITERATIONS): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val keyBytes = deriveKeyBytes(pin.toCharArray(), salt, iterations)
        return Base64.getEncoder().encodeToString(keyBytes)
    }

    fun verifyPin(
        candidatePin: String,
        saltBase64: String,
        expectedHashBase64: String,
        iterations: Int = DEFAULT_KDF_ITERATIONS,
    ): Boolean {
        if (saltBase64.isBlank() || expectedHashBase64.isBlank() || candidatePin.isEmpty()) return false
        val expected = runCatching { Base64.getDecoder().decode(expectedHashBase64) }.getOrNull() ?: return false
        val actual = runCatching {
            val salt = Base64.getDecoder().decode(saltBase64)
            deriveKeyBytes(candidatePin.toCharArray(), salt, iterations)
        }.getOrNull() ?: return false
        return MessageDigest.isEqual(actual, expected)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun encryptText(
        plaintext: String,
        passphrase: String,
        iterations: Int = DEFAULT_KDF_ITERATIONS,
    ): EncryptedCipherPayload {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val keyBytes = deriveKeyBytes(passphrase.toCharArray(), salt, iterations)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(plainBytes)
        keyBytes.fill(0)
        return EncryptedCipherPayload(
            ciphertextBase64 = Base64.getEncoder().encodeToString(encrypted),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            plaintextSha256 = sha256Hex(plainBytes),
        )
    }

    fun decryptText(
        ciphertextBase64: String,
        ivBase64: String,
        saltBase64: String,
        passphrase: String,
        iterations: Int = DEFAULT_KDF_ITERATIONS,
    ): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val iv = Base64.getDecoder().decode(ivBase64)
        val encrypted = Base64.getDecoder().decode(ciphertextBase64)
        val keyBytes = deriveKeyBytes(passphrase.toCharArray(), salt, iterations)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } finally {
            keyBytes.fill(0)
        }
    }

    fun sealEncryptedBackup(
        snapshot: FullBackupSnapshot,
        passphrase: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        require(passphrase.length >= 4) { "Passphrase must be at least 4 characters" }
        val payloadJson = json.encodeToString(FullBackupSnapshot.serializer(), snapshot)
        val cipherPayload = encryptText(payloadJson, passphrase, DEFAULT_KDF_ITERATIONS)
        val envelope = AegisEncryptedBackupEnvelope(
            createdAtMillis = nowMillis,
            saltBase64 = cipherPayload.saltBase64,
            ivBase64 = cipherPayload.ivBase64,
            ciphertextBase64 = cipherPayload.ciphertextBase64,
            plaintextSha256 = cipherPayload.plaintextSha256,
        )
        return json.encodeToString(AegisEncryptedBackupEnvelope.serializer(), envelope)
    }

    fun openEncryptedBackup(
        envelopeJson: String,
        passphrase: String,
    ): FullBackupSnapshot {
        val envelope = json.decodeFromString(AegisEncryptedBackupEnvelope.serializer(), envelopeJson)
        val plaintext = decryptText(
            ciphertextBase64 = envelope.ciphertextBase64,
            ivBase64 = envelope.ivBase64,
            saltBase64 = envelope.saltBase64,
            passphrase = passphrase,
            iterations = envelope.kdfIterations,
        )
        val actualHash = sha256Hex(plaintext)
        if (!actualHash.equals(envelope.plaintextSha256, ignoreCase = true)) {
            throw SecurityException("Backup SHA-256 integrity mismatch")
        }
        return json.decodeFromString(FullBackupSnapshot.serializer(), plaintext)
    }

    fun signLocalSharePacket(packet: ConsentLocalSharePacket): ConsentLocalSharePacket {
        val canonical = "${packet.version}|${packet.exportedAtMillis}|${packet.senderCallsign}|${packet.members.size}|${packet.meetingPoints.size}|${packet.emergencyNote}"
        return packet.copy(sha256Checksum = sha256Hex(canonical))
    }

    fun verifyLocalSharePacket(packet: ConsentLocalSharePacket): Boolean {
        val canonical = "${packet.version}|${packet.exportedAtMillis}|${packet.senderCallsign}|${packet.members.size}|${packet.meetingPoints.size}|${packet.emergencyNote}"
        return packet.sha256Checksum.equals(sha256Hex(canonical), ignoreCase = true)
    }

    private fun deriveKeyBytes(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
