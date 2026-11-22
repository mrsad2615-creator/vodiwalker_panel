plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false

    // Static analysis is part of the build, not an optional extra: detekt runs in
    // the same `check` lifecycle CI already calls, so a complex function or a
    // swallowed exception fails the pipeline exactly as a compile error does.
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    dependencies {
        "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("detekt.yml"))
        baseline = rootProject.file("detekt-baseline.xml")
        // Fail the build: a report nobody is forced to read is decoration.
        ignoreFailures = false
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            txt.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
        }
    }
}
