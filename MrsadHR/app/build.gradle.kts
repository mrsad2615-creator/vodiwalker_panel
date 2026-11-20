plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mrsadhr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mrsadhr"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
        resourceConfigurations += listOf("en", "fa")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The shipped debug keystore is fine for a personal build; replace with your own.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            assets.srcDirs("src/main/assets")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

dependencies {
    // No third party runtime dependencies: the renderer, motion engine, persistence layer and
    // audio pipeline are implemented in this repository against the platform APIs.
}
