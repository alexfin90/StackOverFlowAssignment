plugins {
    alias(libs.plugins.android.library)
}

kotlin {
    // Explicit API mode: every public declaration must state its visibility and
    // type explicitly. Since this module is an SDK consumed by third parties,
    // this keeps the public surface deliberate and prevents accidental exposure
    // of internal classes.
    explicitApi()
    jvmToolchain(17)
}

android {
    namespace = "com.alexfin90.stackoverflowsdk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

}

dependencies {
    // Deliberately no appcompat/material/core-ktx: every dependency of an SDK ends up in the
    // host app, so we only declare what the code needs.
    // Public part, re-exported so host apps get StackOverflowConfig and the domain types
    // by depending on this module only.
    api(project(":stackoverflowsdk:api"))
    api(project(":stackoverflowsdk:domain"))

    // IMPORTANT: keep `implementation`, never `api`. This is what hides :data (Retrofit, OkHttp,
    // DTOs, createStackOverflowClient) from host apps. With `api` they would become visible
    // and part of the public API. See README, "Architecture".
    implementation(project(":stackoverflowsdk:data"))
    testImplementation(libs.junit)
}