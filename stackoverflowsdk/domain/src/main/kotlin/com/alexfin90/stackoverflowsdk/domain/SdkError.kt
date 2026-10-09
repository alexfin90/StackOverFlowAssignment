package com.alexfin90.stackoverflowsdk.domain

/**
 * Why a call failed. One hierarchy shared by every result type, so a new kind of error is added in
 * one place. Never thrown: it travels inside [QuestionsResult.Failure] and [QuestionResult.Failure].
 */
public sealed interface SdkError {

    /** No usable connection: offline, timeout, DNS failure, ... Usually retryable. */
    public class Network(public val message: String?) : SdkError

    /**
     * The server answered but the request failed or the payload was unreadable.
     * [httpCode] is `null` when the response could not be parsed.
     */
    public class Api(public val httpCode: Int?, public val message: String) : SdkError
}
