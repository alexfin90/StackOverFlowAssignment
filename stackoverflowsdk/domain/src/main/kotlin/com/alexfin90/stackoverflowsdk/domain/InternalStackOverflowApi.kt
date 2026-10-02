package com.alexfin90.stackoverflowsdk.domain

/**
 * Marks declarations that are public only because another SDK module needs
 * them (Gradle modules cannot share `internal`). Not part of the supported API:
 * it may change or disappear in any release.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is internal to the StackOverflow SDK and not meant to be used by host apps.",
)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalStackOverflowApi
