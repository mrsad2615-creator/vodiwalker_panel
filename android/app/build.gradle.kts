plugins {
    id("vodigesture.android.application")
    id("vodigesture.android.compose")
    id("vodigesture.android.hilt")
}

android {
    namespace = "com.vodiwalker.gesture"

    defaultConfig {
        applicationId = providers.gradleProperty("APPLICATION_ID").get()
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val ciKeystore = providers.gradleProperty("vodigesture.ciKeystore")
    signingConfigs {
        create("ci") {
            val store = ciKeystore.orNull
            if (store != null && file(store).exists()) {
                storeFile = file(store)
                storePassword = providers.gradleProperty("vodigesture.ciStorePassword").orNull
                keyAlias = providers.gradleProperty("vodigesture.ciKeyAlias").orNull
                keyPassword = providers.gradleProperty("vodigesture.ciKeyPassword").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // `-Pvodigesture.ciSigning=true -Pvodigesture.ciKeystore=...` (CI) signs the
            // minified release build so the small, R8-optimised APK installs in one click.
            // Without those properties the release variant stays unsigned, as a store build must.
            val ciSign = ciKeystore.orNull?.let { file(it).exists() } == true
            if (ciSign && providers.gradleProperty("vodigesture.ciSigning").getOrElse("false").toBoolean()) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    // Verify the packaged APK on the build machine: manifest identity, SDK range and the
    // security posture (no INTERNET / SYSTEM_ALERT_WINDOW / QUERY_ALL_PACKAGES permissions).
    tasks.register("verifyPackagedApk") {
        group = "verification"
        description = "Asserts the manifest of the packaged APKs matches the release contract."
        dependsOn("assembleDebug", "assembleRelease")
        val outputsDir = layout.buildDirectory.dir("outputs/apk")
        val manifestContract = mapOf(
            "package" to namespace!!,
            "minSdk" to providers.gradleProperty("MIN_SDK").get(),
            "targetSdk" to providers.gradleProperty("TARGET_SDK").get(),
        )
        val forbidden = listOf(
            "android.permission.INTERNET",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.READ_SMS",
            "android.permission.RECORD_AUDIO",
        )
        val androidSdkDir = providers.environmentVariable("ANDROID_SDK_ROOT")
            .orElse(providers.environmentVariable("ANDROID_HOME"))
        doLast {
            val aapt2 = file("${androidSdkDir.get()}/build-tools/35.0.0/aapt2")
            check(aapt2.exists()) { "aapt2 not found at $aapt2" }
            val apks = outputsDir.get().asFile.walkTopDown().filter { it.extension == "apk" }.toList()
            check(apks.isNotEmpty()) { "no APK produced under $outputsDir" }
            apks.forEach { apk ->
                val dump = providers.exec {
                    commandLine(aapt2.absolutePath, "dump", "badging", apk.absolutePath)
                }.standardOutput.asText.get()
                val label = apk.name
                check(dump.contains("package: name='${manifestContract["package"]}'")) {
                    "$label: unexpected applicationId"
                }
                check(dump.contains("sdkVersion:'${manifestContract["minSdk"]}'")) {
                    "$label: minSdk mismatch"
                }
                check(dump.contains("targetSdkVersion:'${manifestContract["targetSdk"]}'")) {
                    "$label: targetSdk mismatch"
                }
                forbidden.forEach { permission ->
                    check(!dump.contains(permission)) { "$label: forbidden permission $permission" }
                }
                project.logger.lifecycle("verified $label")
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.engine.gesture)
    implementation(projects.engine.prediction)
    implementation(projects.engine.smartaction)
    implementation(projects.engine.haptics)
    implementation(projects.engine.power)
    implementation(projects.service.accessibility)
    implementation(projects.service.overlay)
    implementation(projects.feature.dashboard)
    implementation(projects.feature.recorder)
    implementation(projects.feature.triggers)
    implementation(projects.feature.permissions)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
