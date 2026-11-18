package ir.aegis.core.model

import kotlinx.serialization.Serializable

@Serializable
data class InventoryItem(
    val id: String,
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val minimumThreshold: Double,
    val expiryAtMillis: Long?,
    val storageLocation: String,
    val updatedAtMillis: Long,
)

@Serializable
data class OfflineNote(
    val id: String,
    val title: String,
    val body: String,
    val category: String,
    val isPinned: Boolean = false,
    val updatedAtMillis: Long,
)

@Serializable
data class VoiceNote(
    val id: String,
    val title: String,
    val localFilePath: String,
    val durationMillis: Long,
    val createdAtMillis: Long,
)

@Serializable
data class ActivityAuditLog(
    val id: String,
    val actionType: String,
    val moduleName: String,
    val summary: String,
    val timestampMillis: Long,
)

@Serializable
data class FirstAidGuide(
    val id: String,
    val title: String,
    val category: String,
    val urgency: String,
    val summary: String,
    val steps: List<String>,
    val doNotDo: List<String>,
    val reviewedSource: String,
)

@Serializable
data class DisasterGuide(
    val id: String,
    val hazardType: String,
    val title: String,
    val beforeSteps: List<String>,
    val duringSteps: List<String>,
    val afterSteps: List<String>,
    val reviewedSource: String,
)

@Serializable
data class FullBackupSnapshot(
    val schemaVersion: Int = 2,
    val exportedAtMillis: Long,
    val preferences: AppPreferences,
    val securityProfile: SecurityProfile?,
    val emergencyCard: EmergencyIdentityCard?,
    val vaultItems: List<VaultItem>,
    val trustedContacts: List<TrustedContact>,
    val safetyPlans: List<PersonalSafetyPlan>,
    val reminders: List<SecurityReminder>,
    val journalEntries: List<PrivateJournalEntry>,
    val incidents: List<Incident>,
    val timelineEvents: List<IncidentTimelineEvent>,
    val attachments: List<IncidentAttachment>,
    val checklists: List<ChecklistWithItems>,
    val mapPackages: List<OfflineMapPackage>,
    val mapMarkers: List<MapMarker>,
    val savedRoutes: List<SavedRoute>,
    val familyMembers: List<FamilyMemberProfile>,
    val meetingPoints: List<FamilyMeetingPoint>,
    val familyPlan: FamilyPlan?,
    val academyProgress: List<AcademyProgressRecord>,
    val inventoryItems: List<InventoryItem>,
    val offlineNotes: List<OfflineNote>,
    val voiceNotes: List<VoiceNote>,
    val auditLogs: List<ActivityAuditLog>,
)
