plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: domain models and contracts, no Android dependencies.
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
