plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: models and contracts only. The only dependency is kotlinx-coroutines, because the
// pager exposes its state as a StateFlow.
kotlin {
    explicitApi()
    // Pins Java and Kotlin compilation to JDK 17 (auto-provisioned via foojay if missing).
    jvmToolchain(17)
}

dependencies {
    // `api`: StateFlow is part of the public API (QuestionsPager.state).
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
