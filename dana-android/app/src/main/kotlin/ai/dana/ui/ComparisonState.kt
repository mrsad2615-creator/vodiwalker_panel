package ai.dana.ui

import ai.dana.domain.EngineEvent
import ai.dana.domain.EngineProblem
import ai.dana.domain.ProviderProfile

internal enum class LaneStatus {
    CONNECTING, STREAMING, COMPLETE, FAILED, STOPPED,
}

internal data class EngineLane(
    val profile: ProviderProfile,
    val answer: String = "",
    val status: LaneStatus = LaneStatus.CONNECTING,
    val problem: EngineProblem? = null,
) {
    val pending: Boolean get() = status == LaneStatus.CONNECTING || status == LaneStatus.STREAMING
}

internal data class ComparisonState(
    val prompt: String = "",
    val selected: Set<String> = emptySet(),
    val lanes: List<EngineLane> = emptyList(),
    val running: Boolean = false,
    val problem: EngineProblem? = null,
) {
    fun reduce(event: EngineEvent): ComparisonState = when (event) {
        is EngineEvent.Text -> updateLane(event.profileId) { it.copy(answer = it.answer + event.delta, status = LaneStatus.STREAMING) }
        is EngineEvent.Complete -> updateLane(event.profileId) { it.copy(status = LaneStatus.COMPLETE) }
        is EngineEvent.Failed -> updateLane(event.profileId) { it.copy(status = LaneStatus.FAILED, problem = event.problem) }
        EngineEvent.Offline -> copy(problem = EngineProblem.OFFLINE)
        EngineEvent.Finished -> this
    }

    fun finish(): ComparisonState = copy(running = false, lanes = lanes.map { lane ->
        when {
            !lane.pending -> lane
            problem == null -> lane.copy(status = LaneStatus.STOPPED)
            else -> lane.copy(status = LaneStatus.FAILED, problem = problem)
        }
    })

    private fun updateLane(id: String, transform: (EngineLane) -> EngineLane) =
        copy(lanes = lanes.map { if (it.profile.id == id) transform(it) else it })
}
