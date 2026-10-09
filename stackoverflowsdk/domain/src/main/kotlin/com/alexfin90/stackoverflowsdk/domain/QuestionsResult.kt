package com.alexfin90.stackoverflowsdk.domain

/**
 * Outcome of a questions request.
 *
 * The SDK never throws across its public boundary: every failure is reported
 * as one of these cases so the host app can handle it explicitly.
 */
public sealed interface QuestionsResult {

    public class Success(
        public val questions: List<Question>,
        /** `true` when more pages are available after this one. */
        public val hasMore: Boolean,
    ) : QuestionsResult

    /** No usable connection: offline, timeout, DNS failure, ... Usually retryable. */
    public class NetworkError(public val message: String?) : QuestionsResult

    /**
     * The server answered but the request failed or the payload was unreadable.
     * [httpCode] is `null` when the response could not be parsed.
     */
    public class ApiError(public val httpCode: Int?, public val message: String) : QuestionsResult

    /**
     * RateLimited
     */
    public class RateLimited(public val retryAfterSeconds: Int? = null) : QuestionsResult

}
