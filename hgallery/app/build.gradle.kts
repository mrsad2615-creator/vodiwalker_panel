import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.hgallery.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hgallery.app"
        minSdk = 26
        targetSdk = 35
        // The workflow passes its run number so the tag, the release title and the
        // version the app reports in Settings are the same number. Without it every
        // APK said 0.1.0 while the tag said 0.1.157, and a bug report naming the
        // version pointed at no particular build.
        val releaseRun = providers.gradleProperty("HG_RELEASE_VERSION").orNull
        versionCode = releaseRun?.toIntOrNull() ?: 1
        versionName = releaseRun?.let { "0.1.$it" } ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        val keystorePath = providers.gradleProperty("HG_KEYSTORE").orNull
        if (keystorePath != null) {
            maybeCreate("release").apply {
                storeFile = rootProject.file(keystorePath)
                storePassword = providers.gradleProperty("HG_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("HG_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("HG_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 stays off until the Compose + Room rules have been validated
            // on a real device; a shrinking misconfiguration is not a risk
            // worth taking for a personal build.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    androidResources {
        noCompress += "ttf"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // The lint policy lives in hgallery/lint.xml; see the comment there for why
    // exactly two checks are silenced and everything else still fails the build.
    lint {
        lintConfig = file("../lint.xml")
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // BiometricPrompt, for the Private albums gate. The library falls back to the device
    // credential (PIN, pattern, password) when no fingerprint or face is enrolled, which
    // is the behaviour the screen promises and cannot implement with a keyguard check
    // alone. Apache 2.0.
    implementation("androidx.biometric:biometric:1.1.0")
    // The biometric prompt is a fragment, so the host activity must be a
    // FragmentActivity. Declared here rather than relied on as a transitive dependency,
    // because a compile-time import that arrives by accident is a build that breaks
    // on an unrelated version bump.
    implementation("androidx.fragment:fragment:1.8.5")
    // The platform EXIF class is missing the lens and ISO tags. This one carries the
    // full tag set and is what every camera app uses. Apache 2.0.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Media3 for video playback. ExoPlayer replaces MediaPlayer because the
    // platform player cannot report buffered position or speed reliably, and its
    // seeking on this class of hardware is visibly coarse. The UI module is
    // *not* used: the reference's player chrome is custom, and Media3's default
    // controller would have to be fought rather than configured.
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
