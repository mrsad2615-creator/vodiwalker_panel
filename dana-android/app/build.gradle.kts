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
        versionCode = 8
        versionName = "0.8.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("bundled-assets"))
    androidResources { noCompress.add("litertlm") }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            // Side-by-side with older debug APKs, whose signing key may differ.
            applicationIdSuffix = ".mrsad.workspace"
            versionNameSuffix = "-preview"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
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
    implementation(libs.litertlm.android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.okhttp.client)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.test.junit4)
    ksp(libs.hilt.compiler)
}
