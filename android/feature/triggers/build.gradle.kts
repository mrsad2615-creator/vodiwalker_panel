plugins {
    id("vodigesture.android.library")
    id("vodigesture.android.compose")
    id("vodigesture.android.hilt")
}

android {
    namespace = "com.vodiwalker.gesture.feature.triggers"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.engine.smartaction)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
}
