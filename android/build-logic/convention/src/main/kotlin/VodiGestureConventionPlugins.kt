import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = providers.gradleProperty("COMPILE_SDK").get().toInt()
        defaultConfig {
            minSdk = providers.gradleProperty("MIN_SDK").get().toInt()
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        lint {
            abortOnError = true
            warningsAsErrors = true
            checkDependencies = true
            disable += setOf("GradleDependency")
        }
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            allWarningsAsErrors.set(true)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xconsistent-data-class-copy-visibility",
            )
        }
    }
}

/** Convention plugin for `:app` targeting Android 14/15 (API 35) with strict R8 & lint gates. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = providers.gradleProperty("TARGET_SDK").get().toInt()
            packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/** Convention plugin for Android library modules (`:core:*`, `:service:*`, `:feature:*`). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
    }
}

/** Convention plugin enabling Jetpack Compose K2 compiler + Material Design 3 BOM. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.findByType(ApplicationExtension::class.java)?.apply {
            buildFeatures.compose = true
        }
        extensions.findByType(LibraryExtension::class.java)?.apply {
            buildFeatures.compose = true
        }
        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        }
    }
}

/** Convention plugin configuring Hilt dependency injection via KSP (zero KAPT). */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-compiler").get())
        }
    }
}

/** Convention plugin configuring Room 2.7+ with KSP and exported migration schemas. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.findLibrary("androidx-room-runtime").get())
            add("implementation", libs.findLibrary("androidx-room-ktx").get())
            add("ksp", libs.findLibrary("androidx-room-compiler").get())
            add("testImplementation", libs.findLibrary("androidx-room-testing").get())
        }
    }
}

/** Convention plugin for pure-Kotlin JVM 21 engine & domain modules (`-Werror` enforced). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
                allWarningsAsErrors.set(true)
            }
        }
        dependencies {
            add("testImplementation", libs.findLibrary("kotlin-test").get())
        }
    }
}
