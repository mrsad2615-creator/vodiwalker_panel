package com.neurox.security.vault

import com.neurox.common.json.JsonParser
import com.neurox.common.json.JsonWriter
import com.neurox.common.json.JsonParseException
import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.domain.vault.CipherParams
import com.neurox.domain.vault.KdfParams
import com.neurox.domain.vault.VaultEnvelope
import com.neurox.domain.vault.VaultMappers
import com.neurox.domain.vault.VaultPayload
import com.neurox.domain.vault.VaultValidator
import com.neurox.security.crypto.Digests
import com.neurox.security.crypto.Pbkdf2
import com.neurox.security.crypto.SecureRandomSource
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * NEUROX SECURE VAULT: encrypt, decrypt and validate backup files.
 *
 * CRYPTOGRAPHY (all from the platform, none hand written)
 * - Key derivation: PBKDF2-HMAC-SHA256, 210 000 iterations, 16-byte salt.
 * - Encryption: AES-256-GCM with a 96-bit random nonce per export.
 * - Authentication: the 128-bit GCM tag, plus a SHA-256 of the plaintext as a
 *   second, independent integrity check.
 * - Binding: the AAD covers format, version, KDF parameters and the selection,
 *   so a file cannot be downgraded or widened without breaking the tag.
 *
 * RESTORE SAFETY ORDER (mirrors the specification)
 * 1. Structure: [VaultValidator.validateEnvelope].
 * 2. Version and algorithm acceptance.
 * 3. Authentication through GCM: a wrong password fails here.
 * 4. Payload structure: [VaultValidator.validatePayload].
 * 5. Plaintext checksum: [VaultValidator.verifyChecksum].
 * Only after all five steps does the caller receive data it may apply.
 */
class VaultService(
    private val kdfIterations: Int = DEFAULT_ITERATIONS,
    private val nonceSource: () -> ByteArray = SecureRandomSource::nonce,
) {

    /** Outcome of a successful restore, with the reason for any warnings. */
    data class Restored(
        val payload: VaultPayload,
        val envelopeVersion: Int,
        val kdfAlgorithm: String,
        val usedFallbackKdf: Boolean,
    )

    /**
     * Encrypts [payloadText] into a complete vault document.
     *
     * [payloadText] must already be the canonical JSON produced by
     * [VaultMappers.payloadToJson]. The plaintext checksum stored inside the
     * payload is recomputed here, so a caller cannot export a file whose checksum
     * disagrees with its content.
     */
    fun encrypt(
        payload: VaultPayload,
        payloadJson: String,
        password: CharArray,
        selection: List<String>,
        createdAtMillis: Long,
        preferredKdf: String = KdfParams.PBKDF2_SHA256,
    ): Outcome<String> {
        return try {
            require(password.isNotEmpty()) { "password must not be empty" }
            require(selection.isNotEmpty()) { "selection must not be empty" }
            val salt = SecureRandomSource.salt()
            val nonce = nonceSource()
            require(nonce.size == VaultValidator.NONCE_BYTES) { "nonce must be 12 bytes" }
            val kdf = when (preferredKdf) {
                KdfParams.ARGON2ID -> KdfParams.argon2Default(base64(salt))
                KdfParams.PBKDF2_SHA256 -> KdfParams(
                    algorithm = KdfParams.PBKDF2_SHA256,
                    memoryKiB = 0,
                    iterations = kdfIterations,
                    parallelism = 1,
                    saltBase64 = base64(salt),
                )
                else -> return Outcome.Err(DomainError.BackupFormatUnsupported)
            }
            val aad = aadFor(kdf = kdf, selection = selection, version = VaultEnvelope.CURRENT_VERSION)
            // KEY DERIVATION: the reviewed PBKDF2 path is used for this build. When
            // the envelope declares Argon2id, the importer reports
            // `usedFallbackKdf = true` so the UI can tell the user which algorithm
            // actually protected the file.
            val derivationIterations = when (kdf.algorithm) {
                KdfParams.PBKDF2_SHA256 -> kdf.iterations
                else -> DEFAULT_ITERATIONS
            }
            val key = Pbkdf2.deriveKey(password, salt, derivationIterations, KEY_BYTES)
            val ciphertext = try {
                encryptWithKey(key, nonce, aad, payloadJson.toByteArray(Charsets.UTF_8))
            } finally {
                Digests.wipe(key)
            }
            val envelope = VaultEnvelope(
                version = VaultEnvelope.CURRENT_VERSION,
                kdf = kdf,
                cipher = CipherParams(
                    nonceBase64 = base64(nonce),
                    aadBase64 = base64(aad),
                ),
                payloadBase64 = base64(ciphertext),
                payloadSha256 = Digests.sha256Hex(payloadJson),
                createdAtMillis = createdAtMillis,
                selection = selection,
            )
            // Self-check: the file we are about to hand out must validate and must
            // decrypt with the same password.
            val validation = VaultValidator.validateEnvelope(envelope)
            if (validation is Outcome.Err) return validation
            when (val roundTrip = decrypt(JsonWriter.write(mapEnvelope(envelope)), password)) {
                is Outcome.Err -> return roundTrip
                is Outcome.Ok -> Unit
            }
            Outcome.Ok(JsonWriter.write(mapEnvelope(envelope)))
        } catch (e: IllegalArgumentException) {
            Outcome.Err(DomainError.ConfigurationField("vault"))
        }
    }

    /**
     * Decrypts and fully validates a vault document.
     *
     * Returns [DomainError.BackupPasswordWrong] when the GCM tag does not verify,
     * which the caller must present without revealing whether the file exists.
     */
    fun decrypt(documentJson: String, password: CharArray): Outcome<Restored> {
        val envelope = try {
            envelopeFromJson(documentJson)
        } catch (e: JsonParseException) {
            return Outcome.Err(DomainError.BackupCorrupted)
        } catch (e: IllegalArgumentException) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        }
        when (val structure = VaultValidator.validateEnvelope(envelope)) {
            is Outcome.Err -> return structure
            is Outcome.Ok -> Unit
        }
        val salt = VaultValidator.decodeOrNull(envelope.kdf.saltBase64)
            ?: return Outcome.Err(DomainError.BackupCorrupted)
        val nonce = VaultValidator.decodeOrNull(envelope.cipher.nonceBase64)
            ?: return Outcome.Err(DomainError.BackupCorrupted)
        val ciphertext = VaultValidator.decodeOrNull(envelope.payloadBase64)
            ?: return Outcome.Err(DomainError.BackupCorrupted)
        val aad = VaultValidator.aadFor(envelope)
        val iterations = when (envelope.kdf.algorithm) {
            KdfParams.PBKDF2_SHA256 -> envelope.kdf.iterations
            KdfParams.ARGON2ID -> {
                // The import path accepts the envelope, but this build derives the
                // key with the reviewed PBKDF2 band. The result is reported to the
                // user as a fallback.
                DEFAULT_ITERATIONS
            }
            else -> return Outcome.Err(DomainError.BackupFormatUnsupported)
        }
        val key = try {
            Pbkdf2.deriveKey(password, salt, iterations, KEY_BYTES)
        } catch (e: IllegalArgumentException) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        }
        val plaintext = try {
            decryptWithKey(key, nonce, aad, ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            return Outcome.Err(DomainError.BackupPasswordWrong)
        } catch (e: java.security.GeneralSecurityException) {
            return Outcome.Err(DomainError.BackupCorrupted)
        } finally {
            Digests.wipe(key)
        }
        val payloadText = String(plaintext, Charsets.UTF_8)
        // The checksum must match before anything is parsed further.
        if (Digests.sha256Hex(payloadText) != envelope.payloadSha256) {
            return Outcome.Err(DomainError.RestoreVerifyFailed)
        }
        val payload = try {
            VaultMappers.payloadFromJson(JsonParser.parse(payloadText))
        } catch (e: JsonParseException) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        } catch (e: IllegalArgumentException) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        }
        when (val content = VaultValidator.validatePayload(payload)) {
            is Outcome.Err -> return content
            is Outcome.Ok -> Unit
        }
        // The payload declares a checksum of its own *content* fields. The mapper
        // writes the checksum field last, so the declared value is compared with a
        // recomputation over the payload without that field.
        when (val checksum = verifyPayloadChecksum(payload, payloadText)) {
            is Outcome.Err -> return checksum
            is Outcome.Ok -> Unit
        }
        return Outcome.Ok(
            Restored(
                payload = payload,
                envelopeVersion = envelope.version,
                kdfAlgorithm = envelope.kdf.algorithm,
                usedFallbackKdf = envelope.kdf.algorithm == KdfParams.ARGON2ID,
            ),
        )
    }

    /**
     * Verifies the payload checksum.
     *
     * The check is deliberately tolerant of where the checksum field sits in the
     * JSON text: it recomputes the checksum of the payload re-serialized with an
     * empty checksum, which is exactly how the exporter computed it.
     */
    private fun verifyPayloadChecksum(payload: VaultPayload, payloadText: String): Outcome<Unit> {
        val recomputed = VaultValidator.checksumOf(
            JsonWriter.write(VaultMappers.payloadToJson(payload.copy(checksum = EMPTY_CHECKSUM))),
        )
        val expectedOverEmpty = payload.checksum
        if (expectedOverEmpty == recomputed) return Outcome.Ok(Unit)
        // Fallback: some exporters hash the payload text with the real checksum in
        // place. Accept only if that also matches, so no unchecked content passes.
        if (VaultValidator.checksumOf(payloadText) == expectedOverEmpty) return Outcome.Ok(Unit)
        return Outcome.Err(DomainError.RestoreVerifyFailed)
    }

    /**
     * Prepares a payload for export: computes the checksum over the canonical form
     * with an empty checksum, then returns the payload and its serialized text.
     */
    fun sealForExport(payload: VaultPayload): Pair<VaultPayload, String> {
        val checksumInput = JsonWriter.write(VaultMappers.payloadToJson(payload.copy(checksum = EMPTY_CHECKSUM)))
        val checksum = VaultValidator.checksumOf(checksumInput)
        val sealed = payload.copy(checksum = checksum)
        return sealed to JsonWriter.write(VaultMappers.payloadToJson(sealed))
    }

    /** Reads only the header of a file, so the UI can preview without a password. */
    fun describe(documentJson: String): Outcome<Header> {
        val envelope = try {
            envelopeFromJson(documentJson)
        } catch (e: JsonParseException) {
            return Outcome.Err(DomainError.BackupCorrupted)
        } catch (e: IllegalArgumentException) {
            return Outcome.Err(DomainError.BackupContentInvalid)
        }
        when (val structure = VaultValidator.validateEnvelope(envelope)) {
            is Outcome.Err -> return structure
            is Outcome.Ok -> Unit
        }
        return Outcome.Ok(
            Header(
                version = envelope.version,
                createdAtMillis = envelope.createdAtMillis,
                selection = envelope.selection,
                kdfAlgorithm = envelope.kdf.algorithm,
                iterations = envelope.kdf.iterations,
            ),
        )
    }

    data class Header(
        val version: Int,
        val createdAtMillis: Long,
        val selection: List<String>,
        val kdfAlgorithm: String,
        val iterations: Int,
    )

    // ---- internals -------------------------------------------------------

    private fun encryptWithKey(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun decryptWithKey(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    /** Serializes the envelope with the exact field set the format defines. */
    private fun mapEnvelope(envelope: VaultEnvelope) = com.neurox.common.json.jsonObject {
        "format" string envelope.format
        "version" integer envelope.version
        "kdf" value com.neurox.common.json.jsonObject {
            "algorithm" string envelope.kdf.algorithm
            "memoryKiB" integer envelope.kdf.memoryKiB
            "iterations" integer envelope.kdf.iterations
            "parallelism" integer envelope.kdf.parallelism
            "saltBase64" string envelope.kdf.saltBase64
        }
        "cipher" value com.neurox.common.json.jsonObject {
            "algorithm" string envelope.cipher.algorithm
            "nonceBase64" string envelope.cipher.nonceBase64
            "aadBase64" string envelope.cipher.aadBase64
            "keyLengthBits" integer envelope.cipher.keyLengthBits
        }
        "payloadBase64" string envelope.payloadBase64
        "payloadSha256" string envelope.payloadSha256
        "createdAtMillis" long envelope.createdAtMillis
        "deviceLabel" string envelope.deviceLabel
        "selection" strings envelope.selection
    }

    private fun envelopeFromJson(text: String): VaultEnvelope {
        val root = JsonParser.parse(text).let { it as? com.neurox.common.json.JsonValue.Obj }
            ?: throw JsonParseException("vault document must be an object", -1)
        val o = root.fields
        fun obj(key: String) = (o[key] as? com.neurox.common.json.JsonValue.Obj)?.fields
            ?: throw JsonParseException("missing object '$key'", -1)
        fun str(fields: Map<String, com.neurox.common.json.JsonValue>, key: String): String =
            (fields[key] as? com.neurox.common.json.JsonValue.Str)?.value
                ?: throw JsonParseException("missing string '$key'", -1)
        fun num(fields: Map<String, com.neurox.common.json.JsonValue>, key: String): Int =
            (fields[key] as? com.neurox.common.json.JsonValue.Num)?.value?.toInt()
                ?: throw JsonParseException("missing number '$key'", -1)
        fun lng(fields: Map<String, com.neurox.common.json.JsonValue>, key: String): Long =
            (fields[key] as? com.neurox.common.json.JsonValue.Num)?.value?.toLong()
                ?: throw JsonParseException("missing number '$key'", -1)
        val kdf = obj("kdf")
        val cipher = obj("cipher")
        return VaultEnvelope(
            format = str(o, "format"),
            version = num(o, "version"),
            kdf = KdfParams(
                algorithm = str(kdf, "algorithm"),
                memoryKiB = num(kdf, "memoryKiB"),
                iterations = num(kdf, "iterations"),
                parallelism = num(kdf, "parallelism"),
                saltBase64 = str(kdf, "saltBase64"),
            ),
            cipher = CipherParams(
                algorithm = str(cipher, "algorithm"),
                nonceBase64 = str(cipher, "nonceBase64"),
                aadBase64 = str(cipher, "aadBase64"),
                keyLengthBits = num(cipher, "keyLengthBits"),
            ),
            payloadBase64 = str(o, "payloadBase64"),
            payloadSha256 = str(o, "payloadSha256"),
            createdAtMillis = lng(o, "createdAtMillis"),
            deviceLabel = (o["deviceLabel"] as? com.neurox.common.json.JsonValue.Str)?.value ?: "android",
            selection = (o["selection"] as? com.neurox.common.json.JsonValue.Arr)?.items
                ?.mapNotNull { (it as? com.neurox.common.json.JsonValue.Str)?.value }
                ?: throw JsonParseException("missing selection", -1),
        )
    }

    private fun aadFor(kdf: KdfParams, selection: List<String>, version: Int): ByteArray =
        buildString {
            append(VaultEnvelope.FORMAT_TAG).append('|')
            append(version).append('|')
            append(kdf.algorithm).append('|')
            append(kdf.memoryKiB).append('|')
            append(kdf.iterations).append('|')
            append(kdf.parallelism).append('|')
            append(kdf.saltBase64).append('|')
            append(selection.sorted().joinToString(","))
        }.toByteArray(Charsets.UTF_8)

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    companion object {
        const val DEFAULT_ITERATIONS = 210_000
        const val KEY_BYTES = 32
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val EMPTY_CHECKSUM = ""
    }
}
