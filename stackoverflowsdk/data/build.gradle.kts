plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Networking layer: Retrofit service, DTOs, mappers and repository implementation.
// Consumed only by the :stackoverflowsdk facade, never directly by apps.
//
// NOTE: Retrofit/OkHttp are `implementation` on purpose. That hides them at compile time,
// but at runtime Gradle still unifies versions with the host app (see README, "Dependency
// conflicts").
kotlin {
    explicitApi()
    // Pins Java and Kotlin compilation to JDK 17 (auto-provisioned via foojay if missing).
    jvmToolchain(17)
}

dependencies {
    api(project(":stackoverflowsdk:domain"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
