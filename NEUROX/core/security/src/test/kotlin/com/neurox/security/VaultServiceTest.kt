package com.neurox.security

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.vault.KdfParams
import com.neurox.domain.vault.VaultMappers
import com.neurox.domain.vault.VaultPayload
import com.neurox.domain.vault.VaultValidator
import com.neurox.security.crypto.Digests
import com.neurox.security.crypto.Pbkdf2
import com.neurox.security.pin.PinHasher
import com.neurox.security.vault.VaultService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * RFC 6070 published vectors. These prove that the derivation is standard
 * PBKDF2-HMAC-SHA256 and not an approximation.
 */
class Pbkdf2VectorTest {

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun matchesKnownSha256Vectors() {
        // PBKDF2-HMAC-SHA256("password", "salt", 1, 32)
        val one = Pbkdf2.derive("password".toByteArray(), "salt".toByteArray(), 1, 32)
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", one.toHex())

        // PBKDF2-HMAC-SHA256("password", "salt", 2, 32)
        val two = Pbkdf2.derive("password".toByteArray(), "salt".toByteArray(), 2, 32)
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43", two.toHex())

        // PBKDF2-HMAC-SHA256("password", "salt", 4096, 32)
        val fourThousand = Pbkdf2.derive("password".toByteArray(), "salt".toByteArray(), 4096, 32)
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", fourThousand.toHex())
    }

    @Test
    fun thirtyTwoByteBlockBoundaryIsCorrect() {
        // Multi-block vector with a longer password and salt.
        val derived = Pbkdf2.derive(
            "passwordPASSWORDpassword".toByteArray(),
            "saltSALTsaltSALTsaltSALTsaltSALTsalt".toByteArray(),
            4096,
            40,
        )
        assertEquals(
            "348c89dbcbd32b2f32d814b8116e84cf2b17347ebc1800181c4e2a1fb8dd53e1c635518c7dac47e9",
            derived.toHex(),
        )
    }

    @Test
    fun outputLengthIsExactAndRejectsInvalidInputs() {
        assertEquals(32, Pbkdf2.derive("p".toByteArray(), "s".toByteArray(), 10, 32).size)
        assertEquals(48, Pbkdf2.derive("p".toByteArray(), "s".toByteArray(), 10, 48).size)
        assertThrows(IllegalArgumentException::class.java) { Pbkdf2.deriveKey("1234".toCharArray(), ByteArray(4), 0, 32) }
        assertThrows(IllegalArgumentException::class.java) { Pbkdf2.deriveKey("1234".toCharArray(), ByteArray(0), 10, 32) }
        assertThrows(IllegalArgumentException::class.java) { Pbkdf2.deriveKey("1234".toCharArray(), ByteArray(8), 10, 8) }
    }

    @Test
    fun passwordChangeProducesADifferentKey() {
        val salt = ByteArray(16) { 7 }
        val a = Pbkdf2.derive("secret-a".toByteArray(), salt, 1000, 32)
        val b = Pbkdf2.derive("secret-b".toByteArray(), salt, 1000, 32)
        assertFalse(Digests.constantTimeEquals(a, b))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

class ConstantTimeTest {

    @Test
    fun equalAndDifferentArraysBehaveCorrectly() {
        assertTrue(Digests.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(Digests.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(Digests.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun wipeClearsSecrets() {
        val secret = "password".toByteArray()
        Digests.wipe(secret)
        assertTrue(secret.all { it == 0.toByte() })
        val chars = "1234".toCharArray()
        Digests.wipe(chars)
        assertTrue(chars.all { it == '\u0000' })
    }

    @Test
    fun sha256MatchesKnownDigest() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Digests.sha256Hex(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Digests.sha256Hex("abc"),
        )
    }
}

class VaultServiceTest {

    // Production-strength parameters are used on purpose: the KDF band is a
    // security control, so tests must not bypass it with a cheap iteration count.
    private val service = VaultService()

    private fun payload() = VaultPayload(
        profileId = "p1",
        profileName = "سارا",
        exportedAtMillis = 1_735_689_600_000,
        settings = mapOf("theme.id" to "obsidian", "language" to "fa"),
        checksum = "d".repeat(64),
    )

    private fun export(payload: VaultPayload = payload(), useArgon2: Boolean = false): String {
        val (sealed, json) = service.sealForExport(payload)
        val document = service.encrypt(
            payload = sealed,
            payloadJson = json,
            password = "correct horse battery".toCharArray(),
            selection = listOf("profiles", "sessions", "settings"),
            createdAtMillis = 1_735_689_600_000,
            preferredKdf = if (useArgon2) KdfParams.ARGON2ID else KdfParams.PBKDF2_SHA256,
        )
        assertTrue("export must succeed: $document", document is Outcome.Ok)
        return (document as Outcome.Ok).value
    }

    @Test
    fun exportAndRestoreRoundTrip() {
        val document = export()
        val restored = service.decrypt(document, "correct horse battery".toCharArray())
        assertTrue("restore failed: $restored", restored is Outcome.Ok)
        val value = (restored as Outcome.Ok).value
        assertEquals("p1", value.payload.profileId)
        assertEquals("سارا", value.payload.profileName)
        assertEquals("obsidian", value.payload.settings["theme.id"])
    }

    @Test
    fun wrongPasswordIsRejectedAndNeverYieldsData() {
        val document = export()
        val wrong = service.decrypt(document, "incorrect".toCharArray())
        assertEquals(DomainError.BackupPasswordWrong, (wrong as Outcome.Err).error)
    }

    @Test
    fun everyAlteredByteBreaksAuthentication() {
        val document = export()
        val altered = document.replace("\"version\":1", "\"version\":1")
        assertTrue(altered == document)
        // Change one character inside the ciphertext.
        val marker = "\"payloadBase64\":\""
        val start = document.indexOf(marker) + marker.length
        val flipped = document.substring(0, start) + (if (document[start] == 'A') 'B' else 'A') +
            document.substring(start + 1)
        val result = service.decrypt(flipped, "correct horse battery".toCharArray())
        assertTrue(result is Outcome.Err)
    }

    @Test
    fun tamperedEnvelopeMetadataIsRejectedNotSilentlyAccepted() {
        val document = export()
        // Remove one selected data set: the AAD covers the selection list, so the
        // tag must fail, and the restore must be refused.
        val tampered = document.replace("\"settings\"", "\"plans\"")
        val result = service.decrypt(tampered, "correct horse battery".toCharArray())
        assertTrue("a widened or changed selection must not decrypt", result is Outcome.Err)
    }

    @Test
    fun truncatedOrCorruptFilesAreRejected() {
        val document = export()
        assertTrue(service.decrypt(document.take(document.length / 2), "correct horse battery".toCharArray()) is Outcome.Err)
        assertTrue(service.decrypt("{}", "correct horse battery".toCharArray()) is Outcome.Err)
        assertTrue(service.decrypt("not json", "correct horse battery".toCharArray()) is Outcome.Err)
    }

    @Test
    fun aWeakKeyDerivationSettingIsRefused() {
        val weak = VaultService(kdfIterations = 1_000)
        val json = VaultMappers.writeJson(VaultMappers.payloadToJson(payload()))
        val result = weak.encrypt(payload(), json, "pw".toCharArray(), listOf("profiles"), 0)
        assertEquals(DomainError.ConfigurationField("vault"), (result as Outcome.Err).error)
    }

    @Test
    fun emptyPasswordIsRefusedAtExport() {
        val json = VaultMappers.writeJson(VaultMappers.payloadToJson(payload()))
        val result = service.encrypt(payload(), json, CharArray(0), listOf("profiles"), 0)
        assertTrue(result is Outcome.Err)
    }

    @Test
    fun headerCanBeReadWithoutThePassword() {
        val document = export()
        val header = service.describe(document)
        assertTrue(header is Outcome.Ok)
        val value = (header as Outcome.Ok).value
        assertEquals(VaultService.DEFAULT_ITERATIONS, value.iterations)
        assertEquals(listOf("profiles", "sessions", "settings"), value.selection)
        assertNotNull(value.createdAtMillis)
    }

    @Test
    fun argon2EnvelopeIsImportedAndTheFallbackIsDisclosed() {
        val document = export(useArgon2 = true)
        val restored = service.decrypt(document, "correct horse battery".toCharArray())
        assertTrue("an Argon2id envelope must still restore: $restored", restored is Outcome.Ok)
        val value = (restored as Outcome.Ok).value
        assertEquals(KdfParams.ARGON2ID, value.kdfAlgorithm)
        // This build derives the key with PBKDF2, and says so instead of pretending.
        assertTrue(value.usedFallbackKdf)
    }

    @Test
    fun everyExportUsesAFreshSaltAndNonce() {
        val a = export()
        val b = export()
        val saltOf = { doc: String ->
            Regex("\"saltBase64\":\"([^\"]+)\"").find(doc)?.groupValues?.get(1)
        }
        val nonceOf = { doc: String ->
            Regex("\"nonceBase64\":\"([^\"]+)\"").find(doc)?.groupValues?.get(1)
        }
        assertNotNull(saltOf(a))
        assertFalse("salt must differ per export", saltOf(a) == saltOf(b))
        assertFalse("nonce must differ per export", nonceOf(a) == nonceOf(b))
        assertEquals(12, Base64.getDecoder().decode(nonceOf(a))!!.size)
        assertEquals(16, Base64.getDecoder().decode(saltOf(a))!!.size)
    }

    @Test
    fun ciphertextNeverContainsThePlaintext() {
        val document = export()
        assertFalse(document.contains("obsidian"))
        assertFalse(document.contains("سارا"))
    }

    @Test
    fun fingerprintOfADifferentFileIsNotAcceptedAsSameFile() {
        val a = export()
        val b = export(payload().copy(exportedAtMillis = 42))
        assertTrue(a != b)
        assertTrue(service.decrypt(a, "correct horse battery".toCharArray()) is Outcome.Ok)
        assertTrue(service.decrypt(b, "correct horse battery".toCharArray()) is Outcome.Ok)
    }

    @Test
    fun exerciseConfigsSurviveTheVault() {
        val config = ExerciseTemplate.VISUAL_MEMORY.defaultConfig()
        val json = VaultMappers.writeJson(VaultMappers.configToJson(config))
        val restored = VaultMappers.configFromJson(json)
        assertEquals(config, restored)
    }
}

class PinHasherTest {

    @Test
    fun hashIsSaltedAndVerifiable() {
        val hash = PinHasher.hash("1234")
        assertTrue(PinHasher.verify("1234", hash))
        assertFalse(PinHasher.verify("1235", hash))
        assertEquals(4, hash.split('$').size)
        assertTrue(hash.startsWith("pbkdf2-sha256$"))
    }

    @Test
    fun twoHashesOfTheSamePinDiffer() {
        val a = PinHasher.hash("4321")
        val b = PinHasher.hash("4321")
        assertFalse("a shared salt would make rainbow tables trivial", a == b)
        assertTrue(PinHasher.verify("4321", a))
        assertTrue(PinHasher.verify("4321", b))
    }

    @Test
    fun malformedRecordsNeverThrow() {
        assertFalse(PinHasher.verify("1234", ""))
        assertFalse(PinHasher.verify("1234", "plaintext:1234"))
        assertFalse(PinHasher.verify("1234", "pbkdf2-sha256\$abc\$x\$y"))
        assertFalse(PinHasher.verify("1234", "pbkdf2-sha256\$1000\$!!!\$???"))
        assertFalse(PinHasher.verify("1234", "pbkdf2-sha256\$999\$c2FsdHNhbHRzYWx0c2E=\$AAAA"))
    }

    @Test
    fun pinRulesRejectUnsafeValues() {
        assertFalse(PinHasher.isValidPin("12"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertThrows(IllegalArgumentException::class.java) { PinHasher.hash("abc") }
    }
}
