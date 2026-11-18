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

rootProject.name = "AegisSecurity"

include(
    ":core:model",
    ":core:crypto",
    ":core:database",
    ":core:ai",
    ":core:maps",
    ":core:ui",
    ":feature:personal",
    ":feature:crisis",
    ":feature:maps",
    ":feature:emergency",
    ":feature:ai",
    ":feature:family",
    ":feature:academy",
    ":feature:analytics",
    ":feature:privacy",
    ":feature:utilities",
    ":app"
)
