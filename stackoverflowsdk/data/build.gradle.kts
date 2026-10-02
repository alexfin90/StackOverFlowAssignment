import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
