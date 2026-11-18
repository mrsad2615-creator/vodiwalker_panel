package ir.aegis.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SecurityProfile(
    val id: String = "primary",
    val fullName: String = "",
    val aliasOrCallsign: String = "",
    val homeRegionLabel: String = "",
    val homeLatitude: Double = 35.6892,
    val homeLongitude: Double = 51.3890,
    val safeCheckInCode: String = "",
    val duressCode: String = "",
    val emergencyNote: String = "",
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class EmergencyIdentityCard(
    val id: String = "primary-card",
    val fullName: String = "",
    val dateOfBirth: String = "",
    val bloodType: String = "O+",
    val allergies: String = "",
    val medications: String = "",
    val medicalConditions: String = "",
    val primaryIceContactName: String = "",
    val primaryIceContactPhone: String = "",
    val organDonor: Boolean = false,
    val languagesSpoken: String = "English, Persian",
    val insuranceOrIdNumber: String = "",
    val updatedAtMillis: Long = 0L,
)

@Serializable
enum class VaultCategory {
    IDENTITY,
    MEDICAL,
    ACCESS_KEY,
    LEGAL,
    FINANCIAL,
    OTHER,
}

@Serializable
data class VaultItem(
    val id: String,
    val title: String,
    val category: VaultCategory,
    val encryptedPayloadBase64: String,
    val ivBase64: String,
    val saltBase64: String,
    val sha256Checksum: String,
    val attachmentPath: String = "",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
data class TrustedContact(
    val id: String,
    val name: String,
    val relation: String,
    val phone: String,
    val alternateChannel: String = "",
    val addressOrZone: String = "",
    val priorityRank: Int = 1,
    val canReceiveSosMessage: Boolean = true,
    val notes: String = "",
    val createdAtMillis: Long,
)

@Serializable
data class PersonalSafetyPlan(
    val id: String,
    val title: String,
    val scenarioType: String,
    val triggerConditions: String,
    val actionSteps: List<String>,
    val primarySafeLocation: String,
    val backupSafeLocation: String,
    val updatedAtMillis: Long,
)

@Serializable
data class SecurityReminder(
    val id: String,
    val title: String,
    val category: String,
    val notes: String = "",
    val dueAtMillis: Long,
    val recurrenceDays: Int = 0,
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val createdAtMillis: Long,
)

@Serializable
enum class ThreatMoodLevel {
    CALM,
    CAUTIOUS,
    ELEVATED,
    CRITICAL,
}

@Serializable
data class PrivateJournalEntry(
    val id: String,
    val title: String,
    val content: String,
    val isEncrypted: Boolean = false,
    val ivBase64: String = "",
    val saltBase64: String = "",
    val threatLevel: ThreatMoodLevel = ThreatMoodLevel.CALM,
    val tags: List<String> = emptyList(),
    val locationLabel: String = "",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
