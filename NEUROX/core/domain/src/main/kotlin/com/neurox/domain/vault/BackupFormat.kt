package com.neurox.domain.vault

import com.neurox.common.json.JsonSerializable

/**
 * NEUROX SECURE VAULT file layout (version 1).
 *
 * The file is a single JSON envelope. The payload is AES-256-GCM encrypted; the
 * envelope stores only what is needed to derive and verify the key.
 *
 * ```
 * {
 *   "format": "NEUROX-VAULT",
 *   "version": 1,
 *   "kdf": { "algorithm": "ARGON2ID", "memoryKiB": 65536, "iterations": 3,
 *            "parallelism": 1, "saltBase64": "..." },
 *   "cipher": { "algorithm": "AES-256-GCM", "nonceBase64": "...",
 *               "aadBase64": "..." },
 *   "payloadBase64": "...",
 *   "payloadSha256": "...",
 *   "createdAtMillis": 1735689600000,
 *   "deviceLabel": "android",
 *   "selection": ["profiles","sessions","exercises","achievements","plans","settings"]
 * }
 * ```
 *
 * SECURITY NOTES
 * - The nonce is unique per export and stored in the envelope.
 * - The AAD binds format, version, kdf parameters and selection, so an attacker
 *   cannot downgrade the KDF or widen the selection without breaking the tag.
 * - The plaintext SHA-256 is a second integrity check after decryption, which
 *   detects truncation even if a platform library mis-handles the GCM tag.
 * - A wrong password fails the tag check; it never yields partial plaintext.
 */
@JsonSerializable
data class VaultEnvelope(
    val format: String = FORMAT_TAG,
    val version: Int = CURRENT_VERSION,
    val kdf: KdfParams,
    val cipher: CipherParams,
    val payloadBase64: String,
    val payloadSha256: String,
    val createdAtMillis: Long,
    val deviceLabel: String = "android",
    val selection: List<String>,
) {
    companion object {
        const val FORMAT_TAG = "NEUROX-VAULT"
        const val CURRENT_VERSION = 1
        const val MIN_SUPPORTED_VERSION = 1
    }
}

@JsonSerializable
data class KdfParams(
    val algorithm: String,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val saltBase64: String,
) {
    init {
        // Bands are enforced per algorithm. An unknown algorithm is accepted as a
        // data value so the validator can report a supported domain error instead
        // of throwing; it is never used to derive a key.
        when (algorithm) {
            ARGON2ID -> {
                require(memoryKiB in 8_192..262_144) { "Argon2 memory outside the reviewed band" }
                require(iterations in 1..8) { "Argon2 iterations outside the reviewed band" }
                require(parallelism in 1..4) { "Argon2 parallelism outside the reviewed band" }
            }
            PBKDF2_SHA256 -> {
                require(memoryKiB == 0) { "PBKDF2 does not take a memory cost" }
                require(iterations in 100_000..2_000_000) { "PBKDF2 iterations outside the reviewed band" }
                require(parallelism == 1) { "PBKDF2 takes one lane in this format" }
            }
            else -> {
                require(memoryKiB in 0..262_144)
                require(iterations in 0..2_000_000)
                require(parallelism in 0..4)
            }
        }
    }

    companion object {
        const val ARGON2ID = "ARGON2ID"
        const val PBKDF2_SHA256 = "PBKDF2-SHA256"
        /** Only reviewed algorithms are accepted. */
        val SUPPORTED = setOf(ARGON2ID, PBKDF2_SHA256)

        /** Product default: Argon2id, 64 MiB, 3 passes, 1 lane. */
        fun argon2Default(saltBase64: String): KdfParams = KdfParams(
            algorithm = ARGON2ID,
            memoryKiB = 65_536,
            iterations = 3,
            parallelism = 1,
            saltBase64 = saltBase64,
        )

        /**
         * Conservative fallback for devices where Argon2 is unavailable.
         * It is much weaker than Argon2id, and the export dialog says so.
         */
        fun pbkdf2Fallback(saltBase64: String): KdfParams = KdfParams(
            algorithm = PBKDF2_SHA256,
            memoryKiB = 0,
            iterations = 210_000,
            parallelism = 1,
            saltBase64 = saltBase64,
        ).also { /* memory not used by PBKDF2 */ }
    }
}

@JsonSerializable
data class CipherParams(
    val algorithm: String = AES_GCM,
    val nonceBase64: String,
    val aadBase64: String,
    val keyLengthBits: Int = 256,
) {
    companion object {
        const val AES_GCM = "AES-256-GCM"
    }
}

/** JSON payload schema inside the encrypted blob. */
@JsonSerializable
data class VaultPayload(
    val schemaVersion: Int = 1,
    val profileId: String,
    val profileName: String,
    val exportedAtMillis: Long,
    val profiles: List<ExportedProfile> = emptyList(),
    val sessions: List<ExportedSession> = emptyList(),
    val exercises: List<ExportedExercise> = emptyList(),
    val achievements: List<ExportedAchievement> = emptyList(),
    val plans: List<ExportedPlan> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val checksum: String,
)

@JsonSerializable
data class ExportedProfile(
    val id: String,
    val displayName: String,
    val isGuest: Boolean,
    val avatarId: Int,
    val createdAtMillis: Long,
    val themeId: String,
    val languageTag: String,
)

@JsonSerializable
data class ExportedSession(
    val id: String,
    val exerciseId: String,
    val module: String,
    val kind: String,
    val status: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val activeDurationMillis: Long,
    val difficulty: Int,
    val roundCount: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val missedCount: Int,
    val prematureCount: Int,
    val responseTimeMeanMillis: Double,
    val responseTimeMedianMillis: Double,
    val responseTimeStdevMillis: Double,
    val bestResponseMillis: Long,
    val accuracy: Double,
    val score: Double,
    val xpAwarded: Int,
)

@JsonSerializable
data class ExportedExercise(
    val id: String,
    val name: String,
    val module: String,
    val templateId: String,
    val difficulty: Int,
    val description: String,
    val configJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@JsonSerializable
data class ExportedAchievement(
    val achievementId: String,
    val earnedAtMillis: Long,
)

@JsonSerializable
data class ExportedPlan(
    val id: String,
    val name: String,
    val origin: String,
    val weeklyGoalMinutes: Int,
    val restDays: List<Int>,
    val daysJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val active: Boolean,
)
