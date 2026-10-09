package com.alexfin90.stackoverflowhomeassignment.core.domain

import com.alexfin90.stackoverflowsdk.domain.SdkError

/**
 * Why a load failed, in the app's own terms: the features do not see the SDK's result types, and
 * they show this type directly. A sealed interface, not an enum, so a case can carry data.
 */
sealed interface LoadFailure {
    /** No usable connection. Usually retryable. */
    data object Network : LoadFailure

    /** The server answered badly, or the answer was unreadable. */
    data object Server : LoadFailure
}

/** The only place where the SDK's errors become the app's: both use cases call it. */
internal fun SdkError.toLoadFailure(): LoadFailure = when (this) {
    is SdkError.Network -> LoadFailure.Network
    is SdkError.Api -> LoadFailure.Server
}
