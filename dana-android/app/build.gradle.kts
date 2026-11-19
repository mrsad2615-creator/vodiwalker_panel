plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ai.dana"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "ai.dana"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.compile.sdk.get().toInt()
        testInstrumentationRunner = "ai.dana.WorkspaceTestRunner"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("models") {
            dimension = "edition"
            versionCode = 10
            versionName = "0.10.0"
            ndk { abiFilters.addAll(listOf("arm64-v8a", "x86_64")) }
            proguardFiles("proguard-models.pro")
        }
        create("compat") {
            dimension = "edition"
            applicationId = "ai.dana.mrsad.compat"
            versionCode = 12
            versionName = "0.11.0-compat"
            ndk { abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("models").assets.srcDir(layout.buildDirectory.dir("bundled-assets").get().asFile)
    androidResources { noCompress.add("litertlm") }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            versionNameSuffix = "-preview"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

androidComponents.onVariants(androidComponents.selector().withBuildType("debug").withFlavor("edition" to "models")) { variant ->
    // Preserve both the previously published models preview ID and the signed release ID.
    variant.applicationId.set("ai.dana.mrsad.models")
}

androidComponents.onVariants(androidComponents.selector().withBuildType("debug").withFlavor("edition" to "compat")) { variant ->
    // The new debug certificate is not an upgrade key for the published 0.10.1 Lite APK.
    variant.applicationId.set("ai.dana.mrsad.compat.command")
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.tesseract.android)
    add("modelsImplementation", libs.litertlm.android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.okhttp.client)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.test.junit4)
    androidTestImplementation(libs.coroutines.test)
    ksp(libs.hilt.compiler)
}
