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

    // CI release signing. `-Pvodigesture.ciKeystore=<path>` opts in; the keystore is a throwaway
    // CI key (never a Play upload key) and is never committed. When the property is absent the
    // release variant stays unsigned, exactly as a store build must.
    val ciKeystorePath = providers.gradleProperty("vodigesture.ciKeystore").orNull
    val ciKeystoreFile = ciKeystorePath?.let { file(it) }
    val ciSigningRequested = ciKeystoreFile != null
    signingConfigs {
        if (ciSigningRequested) {
            create("ci") {
                storeFile = ciKeystoreFile
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
            // Signing the minified release build is what makes the small, R8-optimised APK
            // installable in one click.
            if (ciSigningRequested) {
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
        // The debug variant deliberately carries the ".debug" applicationIdSuffix so both builds
        // can be installed side by side; the release variant owns the real applicationId.
        val releasePackage = providers.gradleProperty("APPLICATION_ID").get()
        val minSdk = providers.gradleProperty("MIN_SDK").get()
        val targetSdk = providers.gradleProperty("TARGET_SDK").get()
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
                val expectedPackage = if (label.contains("debug")) "$releasePackage.debug" else releasePackage
                check(dump.contains("package: name='$expectedPackage'")) {
                    "$label: expected applicationId $expectedPackage"
                }
                // aapt/aapt2 across build-tools versions label this line `sdkVersion` or
                // `minSdkVersion`; accept either spelling and report what was actually found.
                val sdkLine = Regex("""(?m)^(min|target)?[sS]dkVersion:'(\d+)'""")
                val sdkLines = dump.lines().filter { it.contains("dkVersion") }
                project.logger.lifecycle("$label sdk lines: $sdkLines")
                val sdkValues = sdkLine.findAll(dump)
                    .mapNotNull { m -> m.groupValues[2].toIntOrNull() }
                    .toList()
                val declaredMin = sdkValues.minOrNull()
                check(declaredMin == minSdk.toInt()) {
                    "$label: expected minSdk $minSdk, badging reported " +
                        "sdk lines $sdkLines"
                }
                val declaredTarget = sdkValues.maxOrNull()
                check(declaredTarget == targetSdk.toInt()) {
                    "$label: expected targetSdk $targetSdk, badging reported " +
                        "sdk lines $sdkLines"
                }
                forbidden.forEach { permission ->
                    check(!dump.contains(permission)) { "$label: forbidden permission $permission" }
                }
                project.logger.lifecycle("verified $label")
            }
        }
    }

    afterEvaluate {
        val raw = ciKeystorePath
        logger.lifecycle(
            "vodigesture signing: property=${raw ?: "<unset>"} " +
                "resolved=${ciKeystoreFile?.absolutePath ?: "<none>"} " +
                "exists=${ciKeystoreFile?.exists() ?: false} " +
                "requested=$ciSigningRequested",
        )
        if (ciSigningRequested) {
            check(ciKeystoreFile!!.exists()) {
                "vodigesture.ciKeystore=${ciKeystoreFile.absolutePath} does not exist"
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
