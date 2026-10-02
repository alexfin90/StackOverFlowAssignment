package com.alexfin90.stackoverflowsdk

/**
 * Configuration for [StackOverflowSdk].
 *
 * All parameters have sensible defaults, so `StackOverflowConfig()` is enough for most hosts.
 * Invalid values fail fast at construction: a bad configuration is a programming error in the
 * host app, not a runtime condition to recover from.
 *
 * @param baseUrl Stack Exchange API base URL, must end with `/`. Override it to point to a proxy
 *   or a test server.
 * @param pageSize questions per page, between 1 and 100 (API limit).
 * @param lastDays only questions created in the last [lastDays] days are returned.
 */
public class StackOverflowConfig(
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val pageSize: Int = DEFAULT_PAGE_SIZE,
    public val lastDays: Int = DEFAULT_LAST_DAYS,
) {
    init {
        require(baseUrl.endsWith("/")) { "baseUrl must end with '/': $baseUrl" }
        require(pageSize in 1..MAX_PAGE_SIZE) { "pageSize must be in 1..$MAX_PAGE_SIZE: $pageSize" }
        require(lastDays > 0) { "lastDays must be > 0: $lastDays" }
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.stackexchange.com/"
        public const val DEFAULT_PAGE_SIZE: Int = 30
        public const val DEFAULT_LAST_DAYS: Int = 30
        private const val MAX_PAGE_SIZE = 100
    }
}
