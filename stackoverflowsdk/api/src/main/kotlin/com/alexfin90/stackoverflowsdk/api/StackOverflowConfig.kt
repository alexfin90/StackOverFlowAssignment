package com.alexfin90.stackoverflowsdk.api

import com.alexfin90.stackoverflowsdk.domain.StackOverflowLogger

/**
 * Configuration for the SDK, passed to `StackOverflowSdk.initialize` (once, in `Application`) or
 * to `StackOverflowSdk.create`.
 *
 * All parameters have sensible defaults, so `StackOverflowConfig()` is enough for most hosts.
 * Invalid values fail fast at construction: a bad configuration is a programming error in the
 * host app, not a runtime condition to recover from.
 *
 * @param baseUrl Stack Exchange API base URL, must end with `/`. Override it to point to a proxy
 *   or a test server.
 * @param pageSize questions per page, between 1 and 100 (API limit).
 * @param lastDays only questions created in the last [lastDays] days are returned.
 * @param connectTimeoutSeconds max time to open the connection; fails fast when offline.
 * @param callTimeoutSeconds hard cap for a whole request, including slow responses. Must be at
 *   least [connectTimeoutSeconds].
 * @param loggingEnabled turns the SDK's diagnostic logging on. Off by default: a library should
 *   be silent unless the host asks otherwise.
 * @param logger where the log lines go when [loggingEnabled] is `true`. `null` (default) writes
 *   to Logcat with the tag `StackOverflowSdk`; pass your own to use Timber or another framework.
 *   Ignored when logging is disabled.
 */
public class StackOverflowConfig(
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val pageSize: Int = DEFAULT_PAGE_SIZE,
    public val lastDays: Int = DEFAULT_LAST_DAYS,
    public val connectTimeoutSeconds: Long = DEFAULT_CONNECT_TIMEOUT_SECONDS,
    public val callTimeoutSeconds: Long = DEFAULT_CALL_TIMEOUT_SECONDS,
    public val loggingEnabled: Boolean = false,
    public val logger: StackOverflowLogger? = null,
) {
    init {
        require(baseUrl.endsWith("/")) { "baseUrl must end with '/': $baseUrl" }
        require(pageSize in 1..MAX_PAGE_SIZE) { "pageSize must be in 1..$MAX_PAGE_SIZE: $pageSize" }
        require(lastDays > 0) { "lastDays must be > 0: $lastDays" }
        require(connectTimeoutSeconds > 0) { "connectTimeoutSeconds must be > 0: $connectTimeoutSeconds" }
        require(callTimeoutSeconds >= connectTimeoutSeconds) {
            "callTimeoutSeconds ($callTimeoutSeconds) must be >= connectTimeoutSeconds ($connectTimeoutSeconds)"
        }
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.stackexchange.com/"
        public const val DEFAULT_PAGE_SIZE: Int = 30
        public const val DEFAULT_LAST_DAYS: Int = 30
        public const val DEFAULT_CONNECT_TIMEOUT_SECONDS: Long = 10
        public const val DEFAULT_CALL_TIMEOUT_SECONDS: Long = 30
        private const val MAX_PAGE_SIZE = 100
    }
}
