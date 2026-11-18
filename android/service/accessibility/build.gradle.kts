plugins {
    id("vodigesture.android.library")
    id("vodigesture.android.hilt")
}

android {
    namespace = "com.vodiwalker.gesture.service.accessibility"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.engine.gesture)
    implementation(projects.engine.prediction)
    implementation(projects.engine.smartaction)
    implementation(projects.engine.haptics)
    implementation(projects.engine.power)
    implementation(projects.service.overlay)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
