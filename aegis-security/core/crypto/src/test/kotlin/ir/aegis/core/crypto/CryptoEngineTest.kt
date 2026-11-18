package ir.aegis.core.crypto

import ir.aegis.core.model.AppPreferences
import ir.aegis.core.model.ConsentLocalSharePacket
import ir.aegis.core.model.FullBackupSnapshot
import ir.aegis.core.model.OfflineNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoEngineTest {

    @Test
    fun pinHashingAndVerificationWorksAndRejectsWrongPin() {
        val salt = CryptoEngine.generateSaltBase64()
        val hash = CryptoEngine.hashPin("4829", salt, iterations = 10_000)
        assertTrue(CryptoEngine.verifyPin("4829", salt, hash, iterations = 10_000))
        assertFalse(CryptoEngine.verifyPin("4820", salt, hash, iterations = 10_000))
        assertFalse(CryptoEngine.verifyPin("", salt, hash, iterations = 10_000))
    }

    @Test
    fun aesGcmEncryptDecryptRoundTripAndTamperDetection() {
        val secret = "Emergency passport & safe house code 9182"
        val encrypted = CryptoEngine.encryptText(secret, "strong-vault-key", iterations = 10_000)
        assertNotEquals(secret, encrypted.ciphertextBase64)

        val decrypted = CryptoEngine.decryptText(
            encrypted.ciphertextBase64,
            encrypted.ivBase64,
            encrypted.saltBase64,
            "strong-vault-key",
            iterations = 10_000,
        )
        assertEquals(secret, decrypted)
        assertEquals(CryptoEngine.sha256Hex(secret), encrypted.plaintextSha256)

        var threw = false
        try {
            CryptoEngine.decryptText(
                encrypted.ciphertextBase64,
                encrypted.ivBase64,
                encrypted.saltBase64,
                "wrong-key",
                iterations = 10_000,
            )
        } catch (_: AEADBadTagException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun encryptedBackupSealAndOpenVerifiesSha256AndContent() {
        val snapshot = FullBackupSnapshot(
            exportedAtMillis = 1763400000000L,
            preferences = AppPreferences(),
            securityProfile = null,
            emergencyCard = null,
            vaultItems = emptyList(),
            trustedContacts = emptyList(),
            safetyPlans = emptyList(),
            reminders = emptyList(),
            journalEntries = emptyList(),
            incidents = emptyList(),
            timelineEvents = emptyList(),
            attachments = emptyList(),
            checklists = emptyList(),
            mapPackages = emptyList(),
            mapMarkers = emptyList(),
            savedRoutes = emptyList(),
            familyMembers = emptyList(),
            meetingPoints = emptyList(),
            familyPlan = null,
            academyProgress = emptyList(),
            inventoryItems = emptyList(),
            offlineNotes = listOf(
                OfflineNote("n1", "Power outage plan", "Battery bank in hallway", "Utility", true, 1000L),
            ),
            voiceNotes = emptyList(),
            auditLogs = emptyList(),
        )
        val sealed = CryptoEngine.sealEncryptedBackup(snapshot, "BackupPass!2026", nowMillis = 1763400000000L)
        val restored = CryptoEngine.openEncryptedBackup(sealed, "BackupPass!2026")
        assertEquals(1, restored.offlineNotes.size)
        assertEquals("Power outage plan", restored.offlineNotes.first().title)
    }

    @Test
    fun consentSharePacketChecksumVerifiesIntegrity() {
        val packet = CryptoEngine.signLocalSharePacket(
            ConsentLocalSharePacket(
                exportedAtMillis = 123456L,
                senderCallsign = "FALCON-1",
                members = emptyList(),
                meetingPoints = emptyList(),
                emergencyNote = "Meet at South Park Gate",
                sha256Checksum = "",
            ),
        )
        assertTrue(CryptoEngine.verifyLocalSharePacket(packet))
        assertFalse(CryptoEngine.verifyLocalSharePacket(packet.copy(emergencyNote = "Tampered")))
    }
}
