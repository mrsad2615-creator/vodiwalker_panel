package ir.aegis.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.aegis.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val dbJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Entity(tableName = "security_profiles")
data class SecurityProfileEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val aliasOrCallsign: String,
    val homeRegionLabel: String,
    val homeLatitude: Double,
    val homeLongitude: Double,
    val safeCheckInCode: String,
    val duressCode: String,
    val emergencyNote: String,
    val updatedAtMillis: Long,
) {
    fun toDomain() = SecurityProfile(id, fullName, aliasOrCallsign, homeRegionLabel, homeLatitude, homeLongitude, safeCheckInCode, duressCode, emergencyNote, updatedAtMillis)
    companion object {
        fun fromDomain(m: SecurityProfile) = SecurityProfileEntity(m.id, m.fullName, m.aliasOrCallsign, m.homeRegionLabel, m.homeLatitude, m.homeLongitude, m.safeCheckInCode, m.duressCode, m.emergencyNote, m.updatedAtMillis)
    }
}

@Entity(tableName = "emergency_cards")
data class EmergencyCardEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val dateOfBirth: String,
    val bloodType: String,
    val allergies: String,
    val medications: String,
    val medicalConditions: String,
    val primaryIceContactName: String,
    val primaryIceContactPhone: String,
    val organDonor: Boolean,
    val languagesSpoken: String,
    val insuranceOrIdNumber: String,
    val updatedAtMillis: Long,
) {
    fun toDomain() = EmergencyIdentityCard(id, fullName, dateOfBirth, bloodType, allergies, medications, medicalConditions, primaryIceContactName, primaryIceContactPhone, organDonor, languagesSpoken, insuranceOrIdNumber, updatedAtMillis)
    companion object {
        fun fromDomain(m: EmergencyIdentityCard) = EmergencyCardEntity(m.id, m.fullName, m.dateOfBirth, m.bloodType, m.allergies, m.medications, m.medicalConditions, m.primaryIceContactName, m.primaryIceContactPhone, m.organDonor, m.languagesSpoken, m.insuranceOrIdNumber, m.updatedAtMillis)
    }
}

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val encryptedPayloadBase64: String,
    val ivBase64: String,
    val saltBase64: String,
    val sha256Checksum: String,
    val attachmentPath: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain() = VaultItem(id, title, VaultCategory.valueOf(category), encryptedPayloadBase64, ivBase64, saltBase64, sha256Checksum, attachmentPath, createdAtMillis, updatedAtMillis)
    companion object {
        fun fromDomain(m: VaultItem) = VaultItemEntity(m.id, m.title, m.category.name, m.encryptedPayloadBase64, m.ivBase64, m.saltBase64, m.sha256Checksum, m.attachmentPath, m.createdAtMillis, m.updatedAtMillis)
    }
}

@Entity(tableName = "trusted_contacts")
data class TrustedContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relation: String,
    val phone: String,
    val alternateChannel: String,
    val addressOrZone: String,
    val priorityRank: Int,
    val canReceiveSosMessage: Boolean,
    val notes: String,
    val createdAtMillis: Long,
) {
    fun toDomain() = TrustedContact(id, name, relation, phone, alternateChannel, addressOrZone, priorityRank, canReceiveSosMessage, notes, createdAtMillis)
    companion object {
        fun fromDomain(m: TrustedContact) = TrustedContactEntity(m.id, m.name, m.relation, m.phone, m.alternateChannel, m.addressOrZone, m.priorityRank, m.canReceiveSosMessage, m.notes, m.createdAtMillis)
    }
}

@Entity(tableName = "safety_plans")
data class SafetyPlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val scenarioType: String,
    val triggerConditions: String,
    val actionStepsJson: String,
    val primarySafeLocation: String,
    val backupSafeLocation: String,
    val updatedAtMillis: Long,
) {
    fun toDomain() = PersonalSafetyPlan(id, title, scenarioType, triggerConditions, dbJson.decodeFromString(actionStepsJson), primarySafeLocation, backupSafeLocation, updatedAtMillis)
    companion object {
        fun fromDomain(m: PersonalSafetyPlan) = SafetyPlanEntity(m.id, m.title, m.scenarioType, m.triggerConditions, dbJson.encodeToString(m.actionSteps), m.primarySafeLocation, m.backupSafeLocation, m.updatedAtMillis)
    }
}

@Entity(tableName = "security_reminders")
data class SecurityReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val notes: String,
    val dueAtMillis: Long,
    val recurrenceDays: Int,
    val enabled: Boolean,
    val completed: Boolean,
    val createdAtMillis: Long,
) {
    fun toDomain() = SecurityReminder(id, title, category, notes, dueAtMillis, recurrenceDays, enabled, completed, createdAtMillis)
    companion object {
        fun fromDomain(m: SecurityReminder) = SecurityReminderEntity(m.id, m.title, m.category, m.notes, m.dueAtMillis, m.recurrenceDays, m.enabled, m.completed, m.createdAtMillis)
    }
}

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val isEncrypted: Boolean,
    val ivBase64: String,
    val saltBase64: String,
    val threatLevel: String,
    val tagsJson: String,
    val locationLabel: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain() = PrivateJournalEntry(id, title, content, isEncrypted, ivBase64, saltBase64, ThreatMoodLevel.valueOf(threatLevel), dbJson.decodeFromString(tagsJson), locationLabel, createdAtMillis, updatedAtMillis)
    companion object {
        fun fromDomain(m: PrivateJournalEntry) = JournalEntryEntity(m.id, m.title, m.content, m.isEncrypted, m.ivBase64, m.saltBase64, m.threatLevel.name, dbJson.encodeToString(m.tags), m.locationLabel, m.createdAtMillis, m.updatedAtMillis)
    }
}

@Entity(
    tableName = "incidents",
    indices = [
        Index(value = ["category"]),
        Index(value = ["status"]),
        Index(value = ["createdAtMillis"]),
    ],
)
data class IncidentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val priority: String,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationLabel: String,
    val duplicateOfId: String?,
    val aiSuggestedCategory: String?,
    val isArchived: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain() = Incident(id, title, description, IncidentCategory.valueOf(category), IncidentPriority.valueOf(priority), IncidentStatus.valueOf(status), latitude, longitude, locationLabel, duplicateOfId, aiSuggestedCategory, isArchived, createdAtMillis, updatedAtMillis)
    companion object {
        fun fromDomain(m: Incident) = IncidentEntity(m.id, m.title, m.description, m.category.name, m.priority.name, m.status.name, m.latitude, m.longitude, m.locationLabel, m.duplicateOfId, m.aiSuggestedCategory, m.isArchived, m.createdAtMillis, m.updatedAtMillis)
    }
}

@Entity(
    tableName = "incident_timeline",
    foreignKeys = [
        ForeignKey(
            entity = IncidentEntity::class,
            parentColumns = ["id"],
            childColumns = ["incidentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["incidentId"])],
)
data class IncidentTimelineEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val eventType: String,
    val summary: String,
    val createdAtMillis: Long,
) {
    fun toDomain() = IncidentTimelineEvent(id, incidentId, eventType, summary, createdAtMillis)
    companion object {
        fun fromDomain(m: IncidentTimelineEvent) = IncidentTimelineEntity(m.id, m.incidentId, m.eventType, m.summary, m.createdAtMillis)
    }
}

@Entity(
    tableName = "incident_attachments",
    foreignKeys = [
        ForeignKey(
            entity = IncidentEntity::class,
            parentColumns = ["id"],
            childColumns = ["incidentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["incidentId"])],
)
data class IncidentAttachmentEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val displayName: String,
    val mimeType: String,
    val localFilePath: String,
    val sha256Checksum: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
) {
    fun toDomain() = IncidentAttachment(id, incidentId, displayName, mimeType, localFilePath, sha256Checksum, sizeBytes, createdAtMillis)
    companion object {
        fun fromDomain(m: IncidentAttachment) = IncidentAttachmentEntity(m.id, m.incidentId, m.displayName, m.mimeType, m.localFilePath, m.sha256Checksum, m.sizeBytes, m.createdAtMillis)
    }
}

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val linkedIncidentId: String?,
    val isAiGenerated: Boolean,
    val createdAtMillis: Long,
) {
    fun toDomain() = EmergencyChecklist(id, title, category, linkedIncidentId, isAiGenerated, createdAtMillis)
    companion object {
        fun fromDomain(m: EmergencyChecklist) = ChecklistEntity(m.id, m.title, m.category, m.linkedIncidentId, m.isAiGenerated, m.createdAtMillis)
    }
}

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["checklistId"])],
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val checklistId: String,
    val label: String,
    val isChecked: Boolean,
    val sortOrder: Int,
) {
    fun toDomain() = ChecklistItem(id, checklistId, label, isChecked, sortOrder)
    companion object {
        fun fromDomain(m: ChecklistItem) = ChecklistItemEntity(m.id, m.checklistId, m.label, m.isChecked, m.sortOrder)
    }
}

@Entity(tableName = "map_packages")
data class MapPackageEntity(
    @PrimaryKey val id: String,
    val regionName: String,
    val regionCode: String,
    val description: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val sizeBytes: Long,
    val tileCount: Int,
    val versionTag: String,
    val sha256Checksum: String,
    val isBundled: Boolean,
    val isInstalled: Boolean,
    val updatedAtMillis: Long,
) {
    fun toDomain() = OfflineMapPackage(id, regionName, regionCode, description, minLat, maxLat, minLon, maxLon, sizeBytes, tileCount, versionTag, sha256Checksum, isBundled, isInstalled, updatedAtMillis)
    companion object {
        fun fromDomain(m: OfflineMapPackage) = MapPackageEntity(m.id, m.regionName, m.regionCode, m.description, m.minLat, m.maxLat, m.minLon, m.maxLon, m.sizeBytes, m.tileCount, m.versionTag, m.sha256Checksum, m.isBundled, m.isInstalled, m.updatedAtMillis)
    }
}

@Entity(
    tableName = "map_markers",
    indices = [Index(value = ["packageId"])],
)
data class MapMarkerEntity(
    @PrimaryKey val id: String,
    val packageId: String,
    val title: String,
    val description: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val isEmergencyResource: Boolean,
    val isCustom: Boolean,
    val createdAtMillis: Long,
) {
    fun toDomain() = MapMarker(id, packageId, title, description, MarkerCategory.valueOf(category), latitude, longitude, elevationMeters, isEmergencyResource, isCustom, createdAtMillis)
    companion object {
        fun fromDomain(m: MapMarker) = MapMarkerEntity(m.id, m.packageId, m.title, m.description, m.category.name, m.latitude, m.longitude, m.elevationMeters, m.isEmergencyResource, m.isCustom, m.createdAtMillis)
    }
}

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey val id: String,
    val packageId: String,
    val name: String,
    val description: String,
    val waypointsJson: String,
    val totalDistanceMeters: Double,
    val estimatedWalkMinutes: Int,
    val hazardLevel: String,
    val createdAtMillis: Long,
) {
    fun toDomain() = SavedRoute(id, packageId, name, description, dbJson.decodeFromString(waypointsJson), totalDistanceMeters, estimatedWalkMinutes, hazardLevel, createdAtMillis)
    companion object {
        fun fromDomain(m: SavedRoute) = SavedRouteEntity(m.id, m.packageId, m.name, m.description, dbJson.encodeToString(m.waypoints), m.totalDistanceMeters, m.estimatedWalkMinutes, m.hazardLevel, m.createdAtMillis)
    }
}

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val relation: String,
    val ageGroup: String,
    val bloodType: String,
    val allergies: String,
    val medications: String,
    val specialNeeds: String,
    val phone: String,
    val emergencyNote: String,
    val checkInStatus: String,
    val lastCheckInMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain() = FamilyMemberProfile(id, fullName, relation, ageGroup, bloodType, allergies, medications, specialNeeds, phone, emergencyNote, FamilyCheckInStatus.valueOf(checkInStatus), lastCheckInMillis, updatedAtMillis)
    companion object {
        fun fromDomain(m: FamilyMemberProfile) = FamilyMemberEntity(m.id, m.fullName, m.relation, m.ageGroup, m.bloodType, m.allergies, m.medications, m.specialNeeds, m.phone, m.emergencyNote, m.checkInStatus.name, m.lastCheckInMillis, m.updatedAtMillis)
    }
}

@Entity(tableName = "meeting_points")
data class MeetingPointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPrimary: Boolean,
    val latitude: Double,
    val longitude: Double,
    val addressNote: String,
    val landmarkNote: String,
    val instructions: String,
) {
    fun toDomain() = FamilyMeetingPoint(id, name, isPrimary, latitude, longitude, addressNote, landmarkNote, instructions)
    companion object {
        fun fromDomain(m: FamilyMeetingPoint) = MeetingPointEntity(m.id, m.name, m.isPrimary, m.latitude, m.longitude, m.addressNote, m.landmarkNote, m.instructions)
    }
}

@Entity(tableName = "family_plans")
data class FamilyPlanEntity(
    @PrimaryKey val id: String,
    val planTitle: String,
    val evacuationProtocol: String,
    val communicationProtocol: String,
    val outOfAreaContactName: String,
    val outOfAreaContactPhone: String,
    val rallyWindowMinutes: Int,
    val updatedAtMillis: Long,
) {
    fun toDomain() = FamilyPlan(id, planTitle, evacuationProtocol, communicationProtocol, outOfAreaContactName, outOfAreaContactPhone, rallyWindowMinutes, updatedAtMillis)
    companion object {
        fun fromDomain(m: FamilyPlan) = FamilyPlanEntity(m.id, m.planTitle, m.evacuationProtocol, m.communicationProtocol, m.outOfAreaContactName, m.outOfAreaContactPhone, m.rallyWindowMinutes, m.updatedAtMillis)
    }
}

@Entity(tableName = "academy_progress")
data class AcademyProgressEntity(
    @PrimaryKey val courseId: String,
    val completedLessonsJson: String,
    val bestQuizScore: Int,
    val totalQuizQuestions: Int,
    val scenarioBestScore: Int,
    val badgeEarned: Boolean,
    val updatedAtMillis: Long,
) {
    fun toDomain() = AcademyProgressRecord(courseId, dbJson.decodeFromString<Set<String>>(completedLessonsJson), bestQuizScore, totalQuizQuestions, scenarioBestScore, badgeEarned, updatedAtMillis)
    companion object {
        fun fromDomain(m: AcademyProgressRecord) = AcademyProgressEntity(m.courseId, dbJson.encodeToString(m.completedLessonIds), m.bestQuizScore, m.totalQuizQuestions, m.scenarioBestScore, m.badgeEarned, m.updatedAtMillis)
    }
}

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val minimumThreshold: Double,
    val expiryAtMillis: Long?,
    val storageLocation: String,
    val updatedAtMillis: Long,
) {
    fun toDomain() = InventoryItem(id, name, category, quantity, unit, minimumThreshold, expiryAtMillis, storageLocation, updatedAtMillis)
    companion object {
        fun fromDomain(m: InventoryItem) = InventoryItemEntity(m.id, m.name, m.category, m.quantity, m.unit, m.minimumThreshold, m.expiryAtMillis, m.storageLocation, m.updatedAtMillis)
    }
}

@Entity(tableName = "offline_notes")
data class OfflineNoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val category: String,
    val isPinned: Boolean,
    val updatedAtMillis: Long,
) {
    fun toDomain() = OfflineNote(id, title, body, category, isPinned, updatedAtMillis)
    companion object {
        fun fromDomain(m: OfflineNote) = OfflineNoteEntity(m.id, m.title, m.body, m.category, m.isPinned, m.updatedAtMillis)
    }
}

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val localFilePath: String,
    val durationMillis: Long,
    val createdAtMillis: Long,
) {
    fun toDomain() = VoiceNote(id, title, localFilePath, durationMillis, createdAtMillis)
    companion object {
        fun fromDomain(m: VoiceNote) = VoiceNoteEntity(m.id, m.title, m.localFilePath, m.durationMillis, m.createdAtMillis)
    }
}

@Entity(tableName = "audit_logs", indices = [Index(value = ["timestampMillis"])])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val actionType: String,
    val moduleName: String,
    val summary: String,
    val timestampMillis: Long,
) {
    fun toDomain() = ActivityAuditLog(id, actionType, moduleName, summary, timestampMillis)
    companion object {
        fun fromDomain(m: ActivityAuditLog) = AuditLogEntity(m.id, m.actionType, m.moduleName, m.summary, m.timestampMillis)
    }
}
