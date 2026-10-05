plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, zero third-party dependencies: models and contracts only.
kotlin {
    explicitApi()
    // Pins Java and Kotlin compilation to JDK 17 (auto-provisioned via foojay if missing).
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
