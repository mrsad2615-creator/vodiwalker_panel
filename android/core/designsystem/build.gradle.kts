plugins {
    id("vodigesture.android.library")
    id("vodigesture.android.compose")
}

android {
    namespace = "com.vodiwalker.gesture.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
}
