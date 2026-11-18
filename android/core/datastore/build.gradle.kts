plugins {
    id("vodigesture.android.library")
    id("vodigesture.android.hilt")
}

android {
    namespace = "com.vodiwalker.gesture.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
