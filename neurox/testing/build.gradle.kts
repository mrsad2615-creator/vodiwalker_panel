plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":backup:crypto"))
}
application { mainClass.set("com.neurox.tests.CoreTestsKt") }
tasks.named("check") { dependsOn("run") }
