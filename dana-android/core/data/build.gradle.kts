plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// These are public Supabase client settings, not SMTP or service-role credentials.
val danaEmailUrl = providers.environmentVariable("DANA_EMAIL_PROJECT_URL").orElse("").get().trim().removeSuffix("/")
val danaEmailKey = providers.environmentVariable("DANA_EMAIL_PUBLISHABLE_KEY").orElse("").get().trim()
require((danaEmailUrl.isEmpty() && danaEmailKey.isEmpty()) ||
    (Regex("https://[a-z0-9]{20}\\.supabase\\.co").matches(danaEmailUrl) &&
        Regex("sb_publishable_[a-zA-Z0-9_-]{16,96}").matches(danaEmailKey))) {
    "Set both DANA_EMAIL_PROJECT_URL and a public DANA_EMAIL_PUBLISHABLE_KEY, or leave both unset."
}

android {
    namespace = "ai.dana.data"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    buildFeatures { buildConfig = true }

    defaultConfig {
        buildConfigField("String", "EMAIL_PROJECT_URL", "\"$danaEmailUrl\"")
        buildConfigField("String", "EMAIL_PUBLISHABLE_KEY", "\"$danaEmailKey\"")
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
