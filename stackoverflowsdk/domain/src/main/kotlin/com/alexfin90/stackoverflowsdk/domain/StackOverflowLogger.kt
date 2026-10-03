package com.alexfin90.stackoverflowsdk.domain

/** Severity of an SDK log line. */
public enum class LogLevel {
    /** Normal operation, e.g. a request that succeeded. */
    DEBUG,

    /** Something went wrong that the caller can usually retry, e.g. no connection. */
    WARN,

    /** A failure that retrying will probably not fix, e.g. an HTTP error. */
    ERROR,
}

/**
 * Receives the SDK's diagnostic lines. Implement it to forward them to your logging framework:
 *
 * ```
 * StackOverflowLogger { level, message -> Timber.log(priority(level), message) }
 * ```
 *
 * Lines contain only the operation, its parameters, the kind of outcome and its duration. They
 * never contain response bodies, titles or user data. An exception thrown by your logger is
 * swallowed: logging can never crash the SDK or the host app.
 */
public fun interface StackOverflowLogger {
    public fun log(level: LogLevel, message: String)
}
