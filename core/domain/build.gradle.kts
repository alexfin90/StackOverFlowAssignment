plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The app's domain layer: use cases over the SDK contract. Pure Kotlin/JVM, no Android.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // `api`: the use cases expose SDK types (Question, ...) and the features use them, so they must
    // see them through this module. With `implementation` the features stop compiling.
    api(project(":stackoverflowsdk:domain"))
    // `implementation`: only needed here, to put @Inject on the use cases. Hilt, in the app and the
    // features, already brings javax.inject, so nothing has to be re-exported.
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
