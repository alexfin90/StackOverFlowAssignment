package com.alexfin90.stackoverflowsdk.api

/**
 * Receives the SDK's diagnostic lines, so the host can send them where it already collects logs
 * (Timber, a crash reporter, a file). Passed with `StackOverflowConfig(logger = ...)`.
 *
 * A line describes one call: operation, parameters, kind of outcome, counts and duration. It
 * never contains response content (titles, authors, error messages), so it is safe to forward to a
 * remote service.
 *
 * Called on the thread of the caller of the SDK: keep it fast and non-blocking. If it throws, the
 * exception is swallowed and the request is not affected.
 */
public fun interface StackOverflowLogger {

    public fun log(level: Level, message: String)

    /** `DEBUG` for success, `WARN` for no connection, `ERROR` for an API error. */
    public enum class Level { DEBUG, WARN, ERROR }
}
