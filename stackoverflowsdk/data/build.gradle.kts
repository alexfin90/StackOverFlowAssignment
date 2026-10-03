plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Networking layer: Retrofit service, DTOs, mappers and the client implementation.
// Consumed only by the :stackoverflowsdk facade, never directly by apps.
//
// NOTE: Retrofit/OkHttp are `implementation` on purpose. That hides them at compile time,
// but at runtime Gradle still unifies versions with the host app (see README, "Dependency
// conflicts").
kotlin {
    explicitApi()
    jvmToolchain(17)
}

android {
    namespace = "com.alexfin90.stackoverflowsdk.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":stackoverflowsdk:domain"))
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
