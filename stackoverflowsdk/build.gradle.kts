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
    // Facade: re-exports the domain API; data stays an implementation detail.
    api(project(":stackoverflowsdk:domain"))
    implementation(project(":stackoverflowsdk:data"))
    testImplementation(libs.junit)
}