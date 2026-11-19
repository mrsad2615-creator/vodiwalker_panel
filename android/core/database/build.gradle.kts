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
    // Starter content is generated with the same geometry pipeline the recogniser uses.
    implementation(projects.engine.gesture)
    implementation(libs.kotlinx.coroutines.android)
}
