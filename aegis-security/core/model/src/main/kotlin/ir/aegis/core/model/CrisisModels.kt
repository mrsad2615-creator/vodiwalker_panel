package ir.aegis.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class IncidentCategory {
    MEDICAL,
    FIRE,
    EARTHQUAKE,
    FLOOD,
    SECURITY_THREAT,
    TRAFFIC_ACCIDENT,
    UTILITY_OUTAGE,
    MISSING_PERSON,
}

@Serializable
enum class IncidentPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

@Serializable
enum class IncidentStatus {
    OPEN,
    INVESTIGATING,
    CONTAINED,
    RESOLVED,
    ARCHIVED,
}

@Serializable
data class Incident(
    val id: String,
    val title: String,
    val description: String,
    val category: IncidentCategory,
    val priority: IncidentPriority,
    val status: IncidentStatus,
    val latitude: Double?,
    val longitude: Double?,
    val locationLabel: String,
    val duplicateOfId: String? = null,
    val aiSuggestedCategory: String? = null,
    val isArchived: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
data class IncidentTimelineEvent(
    val id: String,
    val incidentId: String,
    val eventType: String,
    val summary: String,
    val createdAtMillis: Long,
)

@Serializable
data class IncidentAttachment(
    val id: String,
    val incidentId: String,
    val displayName: String,
    val mimeType: String,
    val localFilePath: String,
    val sha256Checksum: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
)

@Serializable
data class EmergencyChecklist(
    val id: String,
    val title: String,
    val category: String,
    val linkedIncidentId: String? = null,
    val isAiGenerated: Boolean = false,
    val createdAtMillis: Long,
)

@Serializable
data class ChecklistItem(
    val id: String,
    val checklistId: String,
    val label: String,
    val isChecked: Boolean,
    val sortOrder: Int,
)

@Serializable
data class ChecklistWithItems(
    val checklist: EmergencyChecklist,
    val items: List<ChecklistItem>,
)
