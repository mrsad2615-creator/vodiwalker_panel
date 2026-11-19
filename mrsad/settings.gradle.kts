pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MRSAD"

// Platform-free domain core: pure Kotlin (no Android / no third-party deps).
// Hosted by plain JVM unit tests and executed in CI on every push.
include(":core")

// Android application module.
include(":app")
