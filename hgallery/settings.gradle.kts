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

rootProject.name = "HGallery"

// Platform-free core: pure Kotlin, no Android and no third-party runtime deps.
// Hosted by plain JVM unit tests so the domain logic can be verified without a device.
include(":core")

// Android application module.
include(":app")
