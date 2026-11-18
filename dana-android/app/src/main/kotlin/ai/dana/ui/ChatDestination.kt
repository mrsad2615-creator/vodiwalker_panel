package ai.dana.ui

import ai.dana.domain.RouterFreeModel
import ai.dana.localai.DeviceModel

internal sealed interface ChatDestination {
    data object Unselected : ChatDestination
    data class Local(val model: DeviceModel) : ChatDestination
    data class Saved(val profileId: String) : ChatDestination
    data class OnlineFree(val profileId: String, val model: RouterFreeModel) : ChatDestination
}
