pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                // NOT "com\\.google.*": that sends every com.google artifact here,
                // and KSP (com.google.devtools.ksp) is not published to Google Maven.
                // Restricting it there made the KSP plugin unresolvable with
                // "not found in any of the following sources", which reads like a
                // missing version but is a repository-scoping mistake.
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.gms.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
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
