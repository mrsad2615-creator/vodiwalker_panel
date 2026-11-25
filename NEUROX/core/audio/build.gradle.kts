plugins {
    alias(libs.plugins.kotlin.jvm)
}

// PURE JVM. Synth, AudioPolicy and SoundCatalog only — no android.* imports.
// Keeping this module free of the Android plugin is not cosmetic: the Android
// AudioTrack renderer lives in :core:audio-android precisely because an Android
// library module cannot be consumed by the JVM modules that need AudioPolicy
// (core:presentation compiles SessionController against it).
kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:common"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
