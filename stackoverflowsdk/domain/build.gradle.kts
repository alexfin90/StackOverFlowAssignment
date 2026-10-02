plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, zero third-party dependencies: models and contracts only.
kotlin {
    explicitApi()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    testImplementation(libs.junit)
}
