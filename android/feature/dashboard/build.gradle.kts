plugins {
    id("vodigesture.android.library")
    id("vodigesture.android.compose")
    id("vodigesture.android.hilt")
}

android {
    namespace = "com.vodiwalker.gesture.feature.dashboard"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.datastore)
    implementation(projects.service.accessibility)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
}
