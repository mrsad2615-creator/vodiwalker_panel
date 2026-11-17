plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "ai.dana.data"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.core)
    implementation(libs.data.store)
    implementation(libs.room.runtime)
    implementation(libs.hilt.android)
    implementation(libs.okhttp.client)
    implementation(libs.serialization.json)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
