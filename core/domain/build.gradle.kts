plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The app's domain layer: use cases over the SDK contract. Pure Kotlin/JVM, no Android.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // The SDK models and results (Question, ...) are part of the vocabulary of the use cases.
    api(project(":stackoverflowsdk:domain"))
    // @Inject on the use cases; Hilt (in the app) builds them.
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
