package com.alexfin90.stackoverflowsdk.data.remote

/**
 * Outcome of a remote call, already free of transport types: no Retrofit or OkHttp exception
 * crosses the [QuestionsRemoteDataSource] boundary.
 */
internal sealed interface RemoteResult<out T> {

    class Success<out T>(val value: T) : RemoteResult<T>

    sealed interface Failure : RemoteResult<Nothing> {
        /** No usable connection: offline, DNS failure, timeout. */
        class Network(val message: String?) : Failure

        /** The server answered with a non-2xx status. */
        class Http(val code: Int, val message: String) : Failure

        /** The server answered 2xx but the body could not be read. */
        class Malformed(val message: String?) : Failure

        /** Anything else; a bug or an unforeseen condition, never rethrown. */
        class Unexpected(val message: String) : Failure
    }
}
