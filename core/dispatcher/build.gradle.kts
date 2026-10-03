plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Injectable coroutine dispatchers and an application-wide scope (see README in this module).
kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.alexfin90.stackoverflowhomeassignment.core.dispatcher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
