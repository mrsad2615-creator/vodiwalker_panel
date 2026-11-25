package com.neurox.domain.vault

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import java.security.MessageDigest
import java.util.Base64

/**
 * Structural validation for vault files. Pure logic; no cryptography.
 *
 * ORDER OF OPERATIONS when importing (matches the restore safety contract):
 * 1. Read the file.
 * 2. Validate the envelope structure with [validateEnvelope].
 * 3. Validate the format version with [validateVersion].
 * 4. Decrypt and authenticate (implemented in core:security).
 * 5. Validate the payload with [validatePayload].
 * 6. Verify the plaintext checksum with [verifyChecksum].
 * 7. Only then ask the data layer to prepare a restore transaction.
 */
object VaultValidator {

    val ALLOWED_SELECTIONS = setOf(
        "profiles", "sessions", "exercises", "achievements", "plans", "settings",
    )

    fun validateEnvelope(envelope: VaultEnvelope): Outcome<VaultEnvelope> {
        if (envelope.format != VaultEnvelope.FORMAT_TAG) return Outcome.Err(DomainError.BackupFormatUnsupported)
        if (envelope.version !in VaultEnvelope.MIN_SUPPORTED_VERSION..VaultEnvelope.CURRENT_VERSION) {
            return Outcome.Err(DomainError.BackupVersionUnsupported)
        }
        if (envelope.kdf.algorithm !in KdfParams.SUPPORTED) return Outcome.Err(DomainError.BackupFormatUnsupported)
        if (envelope.cipher.algorithm != CipherParams.AES_GCM) return Outcome.Err(DomainError.BackupFormatUnsupported)
        if (envelope.cipher.keyLengthBits != 256) return Outcome.Err(DomainError.BackupFormatUnsupported)
        if (envelope.selection.isEmpty()) return Outcome.Err(DomainError.BackupContentInvalid)
        if (envelope.selection.any { it !in ALLOWED_SELECTIONS }) return Outcome.Err(DomainError.BackupContentInvalid)
        if (envelope.selection.distinct().size != envelope.selection.size) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        }
        if (envelope.payloadBase64.isBlank()) return Outcome.Err(DomainError.BackupContentInvalid)
        if (!isHexSha256(envelope.payloadSha256)) return Outcome.Err(DomainError.BackupContentInvalid)
        val nonce = decodeOrNull(envelope.cipher.nonceBase64) ?: return Outcome.Err(DomainError.BackupCorrupted)
        // AES-GCM nonce of 12 bytes is the reviewed choice.
        if (nonce.size != NONCE_BYTES) return Outcome.Err(DomainError.BackupContentInvalid)
        val salt = decodeOrNull(envelope.kdf.saltBase64) ?: return Outcome.Err(DomainError.BackupCorrupted)
        if (salt.size < MIN_SALT_BYTES) return Outcome.Err(DomainError.BackupContentInvalid)
        decodeOrNull(envelope.cipher.aadBase64) ?: return Outcome.Err(DomainError.BackupCorrupted)
        decodeOrNull(envelope.payloadBase64) ?: return Outcome.Err(DomainError.BackupCorrupted)
        return Outcome.Ok(envelope)
    }

    fun validatePayload(payload: VaultPayload): Outcome<VaultPayload> {
        if (payload.schemaVersion !in 1..VaultEnvelope.CURRENT_VERSION) {
            return Outcome.Err(DomainError.BackupVersionUnsupported)
        }
        if (payload.profileId.isBlank() || payload.profileName.isBlank()) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        }
        if (payload.sessions.any { it.module !in KNOWN_MODULES }) return Outcome.Err(DomainError.BackupContentInvalid)
        if (payload.sessions.any { it.difficulty !in 1..5 }) return Outcome.Err(DomainError.BackupContentInvalid)
        if (payload.sessions.any { it.accuracy !in 0.0..1.0 }) return Outcome.Err(DomainError.BackupContentInvalid)
        if (payload.sessions.any { it.activeDurationMillis < 0 }) return Outcome.Err(DomainError.BackupContentInvalid)
        if (payload.sessions.distinctBy { it.id }.size != payload.sessions.size) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        }
        if (payload.achievements.distinctBy { it.achievementId }.size != payload.achievements.size) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        }
        if (!isHexSha256(payload.checksum)) return Outcome.Err(DomainError.BackupContentInvalid)
        return Outcome.Ok(payload)
    }

    /** SHA-256 of the payload text, hex lower-case. */
    fun checksumOf(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyChecksum(payload: VaultPayload, payloadText: String): Outcome<Unit> =
        if (checksumOf(payloadText) == payload.checksum) Outcome.Ok(Unit)
        else Outcome.Err(DomainError.RestoreVerifyFailed)

    /** Canonical AAD. Binds version, KDF parameters and selection to the ciphertext. */
    fun aadFor(envelope: VaultEnvelope): ByteArray =
        buildString {
            append(envelope.format).append('|')
            append(envelope.version).append('|')
            append(envelope.kdf.algorithm).append('|')
            append(envelope.kdf.memoryKiB).append('|')
            append(envelope.kdf.iterations).append('|')
            append(envelope.kdf.parallelism).append('|')
            append(envelope.kdf.saltBase64).append('|')
            append(envelope.selection.sorted().joinToString(","))
        }.toByteArray(Charsets.UTF_8)

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeOrNull(text: String): ByteArray? = try {
        Base64.getDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun isHexSha256(value: String): Boolean =
        value.length == 64 && value.all { it in "0123456789abcdef" }

    private val KNOWN_MODULES = setOf("vision", "reflex", "read", "type")

    const val NONCE_BYTES = 12
    const val MIN_SALT_BYTES = 16
    const val SALT_BYTES = 16
}
