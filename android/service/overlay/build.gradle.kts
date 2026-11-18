plugins {
    id("vodigesture.android.library")
    id("vodigesture.android.compose")
}

android {
    namespace = "com.vodiwalker.gesture.service.overlay"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.kotlinx.coroutines.android)
}
