pluginManagement {
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

rootProject.name = "NEUROX"

include(
    ":app",
    ":core:common",
    ":core:domain",
    ":core:data",
    ":core:designsystem",
    ":core:games",
    ":core:audio",
    ":core:audio-android",
    ":core:security",
    ":core:presentation",
    ":feature:onboarding",
    ":feature:home",
    ":feature:session",
    ":feature:vision",
    ":feature:reflex",
    ":feature:read",
    ":feature:type",
    ":feature:analytics",
    ":feature:library",
    ":feature:profile",
    ":feature:settings",
)
