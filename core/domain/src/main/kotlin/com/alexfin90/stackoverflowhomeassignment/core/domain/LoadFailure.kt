package com.alexfin90.stackoverflowhomeassignment.core.domain

/** Why a load failed, in the app's own terms: the features do not see the SDK's result types. */
enum class LoadFailure {
    /** No usable connection. Usually retryable. */
    Network,

    /** The server answered badly, or the answer was unreadable. */
    Server,
}
