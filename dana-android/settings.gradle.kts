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
        exclusiveContent {
            forRepository { maven("https://jitpack.io") { name = "Tesseract4Android releases" } }
            filter { includeModule("cz.adaptech.tesseract4android", "tesseract4android") }
        }
    }
}

rootProject.name = "DanaAndroid"
include(":app", ":core:domain", ":core:data")
