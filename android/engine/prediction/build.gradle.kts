plugins {
    id("vodigesture.jvm.library")
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(projects.engine.gesture)
}
