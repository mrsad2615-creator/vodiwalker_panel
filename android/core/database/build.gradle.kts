plugins {
    id("vodigesture.android.library")
    id("vodigesture.android.room")
    id("vodigesture.android.hilt")
}

android {
    namespace = "com.vodiwalker.gesture.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.engine.prediction)
    implementation(libs.kotlinx.coroutines.android)
}
