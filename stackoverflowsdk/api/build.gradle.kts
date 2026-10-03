plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Public configuration of the SDK (StackOverflowConfig). Pure Kotlin/JVM, no dependencies.
kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
