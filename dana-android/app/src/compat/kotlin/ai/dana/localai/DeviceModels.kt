package ai.dana.localai

import ai.dana.R
import ai.dana.domain.ContextMessage
import ai.dana.domain.ReviewMessage
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Compatibility edition has no model weights, JNI inference library or silent online substitute. */
@Singleton
@Suppress("UNUSED_PARAMETER")
internal class DeviceModels @Inject constructor(@ApplicationContext context: Context) {
    suspend fun available(): Set<DeviceModel> = emptySet()

    fun complete(model: DeviceModel, prompt: String, previous: List<ContextMessage>): Flow<DeviceText> = unavailable()

    fun review(message: ReviewMessage): Flow<DeviceText> = unavailable()

    private fun unavailable(): Flow<DeviceText> = flow {
        throw DeviceModelProblem(R.string.models_compat_summary)
    }

    companion object {
        const val BUNDLED = false
        const val PROMPT_BYTES = ReviewMessage.LOCAL_BYTES
    }
}
