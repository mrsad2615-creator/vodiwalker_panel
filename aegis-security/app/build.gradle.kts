plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ir.aegis.app"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "ir.aegis.security"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.compile.sdk.get().toInt()
        versionCode = 10000
        versionName = "1.0.0-offline"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // CI injects release keystore via properties when available; falls back safely to debug key for local verification
        getByName("debug")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val ksPath = System.getenv("AEGIS_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(ksPath)
                    storePassword = System.getenv("AEGIS_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("AEGIS_KEY_ALIAS")
                    keyPassword = System.getenv("AEGIS_KEY_PASSWORD")
                }
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:crypto"))
    implementation(project(":core:database"))
    implementation(project(":core:ai"))
    implementation(project(":core:maps"))
    implementation(project(":core:ui"))

    implementation(project(":feature:personal"))
    implementation(project(":feature:crisis"))
    implementation(project(":feature:maps"))
    implementation(project(":feature:emergency"))
    implementation(project(":feature:ai"))
    implementation(project(":feature:family"))
    implementation(project(":feature:academy"))
    implementation(project(":feature:analytics"))
    implementation(project(":feature:privacy"))
    implementation(project(":feature:utilities"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.work.runtime)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
