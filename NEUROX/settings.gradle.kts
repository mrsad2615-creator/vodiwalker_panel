pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                // All com.google.* EXCEPT com.google.devtools.*, which is where KSP
                // lives and which Google Maven does not publish.
                //
                // The simple form, includeGroupByRegex("com\\.google.*"), claims KSP
                // for Google Maven and makes the plugin permanently unresolvable;
                // Gradle then reports "not found in any of the following sources",
                // which reads like a bad version. Narrowing instead to the specific
                // com.google families broke com.google.testing.platform, which is
                // part of AGP's own dependency graph. So: include the family, exclude
                // the one development-tools subtree that is not there.
                includeGroupByRegex("com\\.google\\.(?!devtools).*")
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
