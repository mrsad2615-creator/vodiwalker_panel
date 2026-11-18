pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = providers.gradleProperty("PROJECT_NAME").getOrElse("VodiGesture")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Application entry point
include(":app")

// Core foundation layers
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")

// Multi-Engine domain & signal-processing stack (100% pure Kotlin, zero Android framework coupling)
include(":engine:gesture")
include(":engine:prediction")
include(":engine:smartaction")
include(":engine:haptics")
include(":engine:power")

// Android 14/15 system services (Accessibility & Overlay)
include(":service:accessibility")
include(":service:overlay")

// Feature presentation modules (MVVM + UDF + Jetpack Compose M3)
include(":feature:dashboard")
include(":feature:recorder")
include(":feature:triggers")
include(":feature:permissions")
