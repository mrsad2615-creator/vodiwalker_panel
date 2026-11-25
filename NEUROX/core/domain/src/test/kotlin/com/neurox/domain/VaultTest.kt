package com.neurox.domain

import com.neurox.common.json.JsonParser
import com.neurox.common.json.JsonWriter
import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.MovementDirection
import com.neurox.domain.model.Module
import com.neurox.domain.plan.PlanItem
import com.neurox.domain.plan.PlanValidator
import com.neurox.domain.vault.CipherParams
import com.neurox.domain.vault.ExportedAchievement
import com.neurox.domain.vault.ExportedExercise
import com.neurox.domain.vault.ExportedProfile
import com.neurox.domain.vault.KdfParams
import com.neurox.domain.vault.VaultEnvelope
import com.neurox.domain.vault.VaultMappers
import com.neurox.domain.vault.VaultPayload
import com.neurox.domain.vault.VaultValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class VaultValidatorTest {

    private fun envelope(
        format: String = VaultEnvelope.FORMAT_TAG,
        version: Int = VaultEnvelope.CURRENT_VERSION,
        kdf: KdfParams = KdfParams.argon2Default(salt()),
        cipher: CipherParams = CipherParams(
            nonceBase64 = Base64.getEncoder().encodeToString(ByteArray(12) { it.toByte() }),
            aadBase64 = Base64.getEncoder().encodeToString("aad".toByteArray()),
        ),
        selection: List<String> = listOf("profiles", "sessions"),
        payload: String = Base64.getEncoder().encodeToString(ByteArray(64) { 7 }),
        sha: String = "a".repeat(64),
    ) = VaultEnvelope(
        format = format,
        version = version,
        kdf = kdf,
        cipher = cipher,
        payloadBase64 = payload,
        payloadSha256 = sha,
        createdAtMillis = 1_700_000_000_000,
        selection = selection,
    )

    private fun salt(): String = Base64.getEncoder().encodeToString(ByteArray(16) { (it * 3).toByte() })

    @Test
    fun acceptsAWellFormedEnvelope() {
        assertTrue(VaultValidator.validateEnvelope(envelope()) is Outcome.Ok)
    }

    @Test
    fun rejectsUnknownFormatAndVersion() {
        assertEquals(
            DomainError.BackupFormatUnsupported,
            (VaultValidator.validateEnvelope(envelope(format = "SOME-OTHER")) as Outcome.Err).error,
        )
        assertTrue(
            (VaultValidator.validateEnvelope(envelope(version = 2)) as Outcome.Err).error
                is DomainError.BackupVersionUnsupported,
        )
        assertTrue(
            (VaultValidator.validateEnvelope(envelope(version = 0)) as Outcome.Err).error
                is DomainError.BackupVersionUnsupported,
        )
    }

    @Test
    fun rejectsWeakOrUnknownKeyDerivation() {
        assertEquals(
            DomainError.BackupFormatUnsupported,
            (VaultValidator.validateEnvelope(envelope(kdf = KdfParams("MD5", 8192, 1, 1, salt()))) as Outcome.Err)
                .error,
        )
        // Out-of-band Argon2 parameters cannot even be constructed.
        assertThrows(IllegalArgumentException::class.java) {
            KdfParams(KdfParams.ARGON2ID, 1_024, 3, 1, salt())
        }
        assertThrows(IllegalArgumentException::class.java) {
            KdfParams(KdfParams.ARGON2ID, 65_536, 99, 1, salt())
        }
    }

    @Test
    fun rejectsWrongCipherAndWrongNonceLength() {
        assertEquals(
            DomainError.BackupFormatUnsupported,
            (VaultValidator.validateEnvelope(
                envelope(cipher = CipherParams(algorithm = "AES-128-CBC", nonceBase64 = "AAAA", aadBase64 = "", keyLengthBits = 256)),
            ) as Outcome.Err).error,
        )
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validateEnvelope(
                envelope(
                    cipher = CipherParams(
                        nonceBase64 = Base64.getEncoder().encodeToString(ByteArray(8)),
                        aadBase64 = Base64.getEncoder().encodeToString("a".toByteArray()),
                    ),
                ),
            ) as Outcome.Err).error,
        )
    }

    @Test
    fun rejectsDuplicatedOrUnknownSelection() {
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validateEnvelope(envelope(selection = listOf("profiles", "profiles"))) as Outcome.Err).error,
        )
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validateEnvelope(envelope(selection = listOf("everything"))) as Outcome.Err).error,
        )
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validateEnvelope(envelope(selection = emptyList())) as Outcome.Err).error,
        )
    }

    @Test
    fun rejectsShortSaltAndCorruptChecksumField() {
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validateEnvelope(
                envelope(kdf = KdfParams(KdfParams.ARGON2ID, 65_536, 3, 1, Base64.getEncoder().encodeToString(ByteArray(4)))),
            ) as Outcome.Err).error,
        )
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validateEnvelope(envelope(sha = "zz")) as Outcome.Err).error,
        )
    }

    @Test
    fun rejectsPayloadThatIsNotBase64() {
        assertEquals(
            DomainError.BackupCorrupted,
            (VaultValidator.validateEnvelope(envelope(payload = "not base64!!")) as Outcome.Err).error,
        )
    }

    @Test
    fun payloadValidationCatchesTamperedContent() {
        val good = payload()
        assertTrue(VaultValidator.validatePayload(good) is Outcome.Ok)
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validatePayload(good.copy(sessions = good.sessions.map { it.copy(difficulty = 9) }))
                as Outcome.Err).error,
        )
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validatePayload(good.copy(sessions = good.sessions.map { it.copy(module = "audio") }))
                as Outcome.Err).error,
        )
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validatePayload(good.copy(sessions = good.sessions + good.sessions))
                as Outcome.Err).error,
        )
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validatePayload(good.copy(sessions = good.sessions.map { it.copy(accuracy = 1.4) }))
                as Outcome.Err).error,
        )
    }

    @Test
    fun payloadValidationRejectsEmptyIdentityAndBadChecksum() {
        val good = payload()
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validatePayload(good.copy(profileId = " ")) as Outcome.Err).error,
        )
        assertEquals(
            DomainError.BackupContentInvalid,
            (VaultValidator.validatePayload(good.copy(checksum = "abc")) as Outcome.Err).error,
        )
        assertTrue(
            (VaultValidator.validatePayload(good.copy(schemaVersion = 9)) as Outcome.Err).error
                is DomainError.BackupVersionUnsupported,
        )
    }

    @Test
    fun checksumDetectsAnyChangeAndIsStable() {
        val text = """{"a":1}"""
        val checksum = VaultValidator.checksumOf(text)
        assertEquals(checksum, VaultValidator.checksumOf(text))
        assertEquals(64, checksum.length)
        assertTrue(checksum.all { it in "0123456789abcdef" })
        val signed = payload().copy(checksum = checksum)
        // The unchanged payload verifies...
        assertTrue(VaultValidator.verifyChecksum(signed, """{"a":1}""") is Outcome.Ok)
        // ...and a single changed byte fails loudly.
        assertEquals(
            DomainError.RestoreVerifyFailed,
            (VaultValidator.verifyChecksum(signed, """{"a":2}""") as Outcome.Err).error,
        )
    }

    @Test
    fun aadBindsVersionKdfAndSelection() {
        val base = VaultValidator.aadFor(envelope())
        val otherVersion = VaultValidator.aadFor(envelope(version = 1))
        val otherSelection = VaultValidator.aadFor(envelope(selection = listOf("profiles")))
        val otherKdf = VaultValidator.aadFor(envelope(kdf = KdfParams.pbkdf2Fallback(salt())))
        assertEquals(base.size, otherVersion.size)
        assertTrue(!base.contentEquals(otherSelection))
        assertTrue(!base.contentEquals(otherKdf))
    }

    @Test
    fun pbkdf2FallbackIsAdvertisedWithItsIterationCount() {
        val fallback = KdfParams.pbkdf2Fallback(salt())
        assertEquals(KdfParams.PBKDF2_SHA256, fallback.algorithm)
        assertTrue(fallback.iterations >= 200_000)
        assertTrue(VaultValidator.validateEnvelope(envelope(kdf = fallback)) is Outcome.Ok)
    }

    private fun payload(): VaultPayload {
        val sessions = listOf(VaultMappers.sessionToExported(Records.of(id = "s1")))
        return VaultPayload(
            profileId = "profile-a",
            profileName = "Ada",
            exportedAtMillis = 1_700_000_000_000,
            profiles = listOf(ExportedProfile("profile-a", "Ada", false, 1, 0, "obsidian", "fa")),
            sessions = sessions,
            exercises = listOf(
                ExportedExercise(
                    id = "ex-1",
                    name = "My focus",
                    module = "vision",
                    templateId = ExerciseTemplate.FOCUS_LOCK.id,
                    difficulty = 3,
                    description = "",
                    configJson = JsonWriter.write(VaultMappers.configToJson(ExerciseTemplate.FOCUS_LOCK.defaultConfig())),
                    createdAtMillis = 1,
                    updatedAtMillis = 2,
                ),
            ),
            achievements = listOf(ExportedAchievement("first_session", 1_700_000_000_000)),
            settings = mapOf("adaptive.enabled" to "true"),
            checksum = "b".repeat(64),
        )
    }
}

class VaultMappersTest {

    @Test
    fun sessionRoundTripsExactly() {
        val original = Records.of(id = "round-trip", accuracy = 0.83, responseMillis = 512.0)
        val json = JsonWriter.write(VaultMappers.sessionToJson(original))
        val restored = VaultMappers.sessionFromJson(JsonParser.parse(json))
        assertEquals(original, restored)
    }

    @Test
    fun configRoundTripsIncludingNullableFields() {
        val original = ExerciseTemplate.PERIPHERAL_SCAN.defaultConfig().copy(
            movementDirection = MovementDirection.VERTICAL,
            readingTextId = "text-7",
            requireComprehension = true,
        )
        val restored = VaultMappers.configFromJson(JsonWriter.write(VaultMappers.configToJson(original)))
        assertEquals(original, restored)
    }

    @Test
    fun configToleratesUnknownAndMissingFields() {
        val partial = """{"rounds":20,"futureField":"ignored"}"""
        val restored = VaultMappers.configFromJson(partial)
        assertEquals(20, restored.rounds)
        assertEquals(120, restored.sessionDurationSeconds)
    }

    @Test
    fun planRoundTripsWithNestedDays() {
        val plan = com.neurox.domain.plan.TrainingPlan(
            id = "p",
            name = "Plan",
            origin = com.neurox.domain.plan.PlanOrigin.COMBINED,
            days = mapOf(1 to listOf(PlanItem(ExerciseTemplate.FOCUS_LOCK.id, 8, 3, 18))),
            weeklyGoalMinutes = 8,
            restDays = setOf(2, 3, 4, 5, 6, 7),
            createdAtMillis = 10,
            updatedAtMillis = 20,
            active = true,
        )
        val restored = VaultMappers.planFromJson(JsonParser.parse(JsonWriter.write(VaultMappers.planToJson(plan))))
        assertEquals(plan, restored)
        assertTrue(PlanValidator.validate(restored) is Outcome.Ok)
    }

    @Test
    fun payloadRoundTripsAndPreservesPersianNames() {
        val payload = VaultPayload(
            profileId = "p1",
            profileName = "سارا",
            exportedAtMillis = 99,
            settings = mapOf("theme.id" to "obsidian"),
            checksum = "c".repeat(64),
        )
        val restored = VaultMappers.payloadFromJson(JsonParser.parse(JsonWriter.write(VaultMappers.payloadToJson(payload))))
        assertEquals(payload, restored)
        assertEquals("سارا", restored.profileName)
    }

    @Test
    fun missingRequiredSessionFieldFailsLoudly() {
        assertThrows(com.neurox.common.json.JsonParseException::class.java) {
            VaultMappers.sessionFromJson(JsonParser.parse("""{"id":"x"}"""))
        }
    }

    @Test
    fun exportedSessionKeepsEveryMeasuredValue() {
        val record = Records.of(id = "e1")
        val exported = VaultMappers.sessionToExported(record)
        assertNotNull(exported)
        assertEquals(record.accuracy, exported.accuracy, 1e-9)
        assertEquals(record.module.id, exported.module)
        assertEquals(record.xpAwarded, exported.xpAwarded)
    }
}
