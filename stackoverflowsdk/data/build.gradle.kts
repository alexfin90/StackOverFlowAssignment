plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Networking layer: Retrofit service, DTOs, mappers and repository implementation.
// Consumed only by the :stackoverflowsdk facade, never directly by apps.
//
// NOTE: no HTTP library on purpose. The transport is the platform's HttpURLConnection, so no
// HTTP dependency can clash with the host app's versions (see docs/dependency-conflicts.md).
// The two runtime dependencies left, kotlinx.serialization and kotlinx.coroutines, are
// `implementation` and very common in host apps.
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
