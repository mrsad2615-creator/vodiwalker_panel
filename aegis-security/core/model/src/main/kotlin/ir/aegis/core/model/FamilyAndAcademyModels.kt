package ir.aegis.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class FamilyCheckInStatus {
    UNKNOWN,
    SAFE,
    NEED_HELP,
    EVACUATING,
}

@Serializable
data class FamilyMemberProfile(
    val id: String,
    val fullName: String,
    val relation: String,
    val ageGroup: String,
    val bloodType: String,
    val allergies: String,
    val medications: String,
    val specialNeeds: String,
    val phone: String,
    val emergencyNote: String,
    val checkInStatus: FamilyCheckInStatus = FamilyCheckInStatus.UNKNOWN,
    val lastCheckInMillis: Long = 0L,
    val updatedAtMillis: Long,
)

@Serializable
data class FamilyMeetingPoint(
    val id: String,
    val name: String,
    val isPrimary: Boolean,
    val latitude: Double,
    val longitude: Double,
    val addressNote: String,
    val landmarkNote: String,
    val instructions: String,
)

@Serializable
data class FamilyPlan(
    val id: String = "family-plan-main",
    val planTitle: String,
    val evacuationProtocol: String,
    val communicationProtocol: String,
    val outOfAreaContactName: String,
    val outOfAreaContactPhone: String,
    val rallyWindowMinutes: Int = 45,
    val updatedAtMillis: Long,
)

@Serializable
data class ConsentLocalSharePacket(
    val version: Int = 1,
    val exportedAtMillis: Long,
    val senderCallsign: String,
    val members: List<FamilyMemberProfile>,
    val meetingPoints: List<FamilyMeetingPoint>,
    val emergencyNote: String,
    val sha256Checksum: String,
)

@Serializable
enum class AcademyDomain {
    FIRST_AID,
    FIRE_SAFETY,
    EARTHQUAKE,
    FLOOD,
    TRAVEL_SAFETY,
    DIGITAL_SAFETY,
}

@Serializable
data class AcademyLesson(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val summary: String,
    val keySteps: List<String>,
    val warningNote: String,
    val reviewedSource: String,
)

@Serializable
data class AcademyQuizQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
)

@Serializable
data class AcademyScenarioChoice(
    val label: String,
    val feedback: String,
    val scoreDelta: Int,
    val nextNodeId: String?,
)

@Serializable
data class AcademyScenarioNode(
    val id: String,
    val prompt: String,
    val choices: List<AcademyScenarioChoice>,
)

@Serializable
data class AcademyScenario(
    val id: String,
    val title: String,
    val startNodeId: String,
    val nodes: List<AcademyScenarioNode>,
)

@Serializable
data class AcademyCourse(
    val id: String,
    val domain: AcademyDomain,
    val title: String,
    val subtitle: String,
    val estimatedMinutes: Int,
    val lessons: List<AcademyLesson>,
    val quiz: List<AcademyQuizQuestion>,
    val scenario: AcademyScenario,
)

@Serializable
data class AcademyProgressRecord(
    val courseId: String,
    val completedLessonIds: Set<String> = emptySet(),
    val bestQuizScore: Int = 0,
    val totalQuizQuestions: Int = 0,
    val scenarioBestScore: Int = 0,
    val badgeEarned: Boolean = false,
    val updatedAtMillis: Long = 0L,
)
