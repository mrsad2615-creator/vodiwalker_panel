package ai.dana.localai

import ai.dana.R
import androidx.annotation.StringRes

internal enum class DeviceModel(val id: String, val title: String, @StringRes val blurb: Int) {
    QWEN("qwen3-0.6b", "Qwen3 0.6B", R.string.models_qwen),
    SMOL("smollm2-135m", "SmolLM2 135M", R.string.models_smol),
    ;
    val asset: String get() = "models/$id.litertlm"
}

internal sealed interface DeviceText {
    data object Preparing : DeviceText
    data object Generating : DeviceText
    data class Delta(val value: String) : DeviceText
}

internal class DeviceModelProblem(@StringRes val messageRes: Int) : Exception()
