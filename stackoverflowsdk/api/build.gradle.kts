plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Public configuration of the SDK (StackOverflowConfig). Pure Kotlin/JVM.
kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    // StackOverflowConfig exposes the StackOverflowLogger type from the domain.
    api(project(":stackoverflowsdk:domain"))
    testImplementation(libs.junit)
}
