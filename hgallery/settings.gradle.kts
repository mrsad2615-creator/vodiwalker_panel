pluginManagement {
    repositories {
        // Same order and reason as dependencyResolutionManagement below: Maven
        // Central answers 429 on shared CI runners, so mirror first.
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Maven Central rate-limits the shared GitHub runner IPs (HTTP 429 on
        // the first POM it fetches), which fails the build before any test runs.
        // This mirror holds the same coordinates, so list it first.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "HGallery"

// Platform-free core: pure Kotlin, no Android and no third-party runtime deps.
// Hosted by plain JVM unit tests so the domain logic can be verified without a device.
include(":core")

// Android application module.
include(":app")
