plugins {
    id("vodigesture.android.library")
    id("vodigesture.android.compose")
    id("vodigesture.android.hilt")
}

android {
    namespace = "com.vodiwalker.gesture.feature.permissions"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.datastore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
