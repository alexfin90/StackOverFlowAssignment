package com.alexfin90.stackoverflowsdk.domain

/** Outcome of loading a single question. Like [QuestionsResult], it is never thrown. */
public sealed interface QuestionResult {

    public class Success(public val question: Question) : QuestionResult

    /** The id does not exist (or the question was deleted). */
    public data object NotFound : QuestionResult

    /** No usable connection: offline, timeout, DNS failure, ... Usually retryable. */
    public class NetworkError(public val message: String?) : QuestionResult

    /** The server answered but the request failed or the payload was unreadable. */
    public class ApiError(public val httpCode: Int?, public val message: String) : QuestionResult

    public class RateLimited(public val retryAfterSeconds: Int? = null) : QuestionResult
}
