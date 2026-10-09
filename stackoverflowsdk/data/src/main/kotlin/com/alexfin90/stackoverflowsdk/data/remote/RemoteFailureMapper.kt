package com.alexfin90.stackoverflowsdk.data.remote

import com.alexfin90.stackoverflowsdk.domain.SdkError

/**
 * The only place where transport failures become public errors. Used by the repository and by the
 * pager, so both report the same [SdkError] for the same failure.
 */
internal fun RemoteResult.Failure.toSdkError(): SdkError = when (this) {
    is RemoteResult.Failure.Network -> SdkError.Network(message)
    is RemoteResult.Failure.Http -> SdkError.Api(code, message)
    is RemoteResult.Failure.Malformed -> SdkError.Api(null, "Unreadable response: $message")
    is RemoteResult.Failure.Unexpected -> SdkError.Api(null, message)
}
