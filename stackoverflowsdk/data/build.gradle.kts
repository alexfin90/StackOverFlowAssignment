plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Networking layer: Retrofit service, DTOs, mappers and repository implementation.
// Consumed only by the :stackoverflowsdk facade, never directly by apps.
kotlin {
    explicitApi()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":stackoverflowsdk:domain"))
    testImplementation(libs.junit)
}
