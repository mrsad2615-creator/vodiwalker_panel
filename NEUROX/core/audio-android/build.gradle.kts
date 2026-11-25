plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// The thin Android edge of audio: AudioRenderer writes PCM to an AudioTrack.
// Nothing else lives here, because everything else about audio is pure Kotlin
// and must stay usable from JVM modules (see :core:audio).
android {
    namespace = "com.neurox.core.audio_android"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}

dependencies {
    api(project(":core:audio"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    testImplementation(libs.junit)
}
