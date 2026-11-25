plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    api(project(":core:games"))
    api(project(":core:audio"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
