package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.domain.LogLevel
import com.alexfin90.stackoverflowsdk.domain.StackOverflowClient
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import com.alexfin90.stackoverflowsdk.domain.StackOverflowLogger

/**
 * Decorator that logs one line per call and delegates everything else: the client itself
 * stays free of logging. Only created when logging is enabled, so the default path pays nothing.
 */
internal class LoggingStackOverflowClient(
    private val delegate: StackOverflowClient,
    private val logger: StackOverflowLogger,
    private val nanoTime: () -> Long = System::nanoTime,
) : StackOverflowClient {

    override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int): QuestionsResult {
        val start = nanoTime()
        // A cancellation propagates before reaching the log line, which is what we want.
        val result = delegate.getLatestQuestions(unansweredOnly, page)
        val millis = (nanoTime() - start) / NANOS_PER_MILLI
        safeLog(
            level = result.logLevel(),
            message = "getLatestQuestions(unansweredOnly=$unansweredOnly, page=$page) " +
                "-> ${result.describe()} in $millis ms",
        )
        return result
    }

    /** A faulty host logger must never break a request. */
    private fun safeLog(level: LogLevel, message: String) {
        try {
            logger.log(level, message)
        } catch (_: Exception) {
            // Swallowed on purpose.
        }
    }

    private fun QuestionsResult.logLevel(): LogLevel = when (this) {
        is QuestionsResult.Success -> LogLevel.DEBUG
        is QuestionsResult.NetworkError -> LogLevel.WARN
        is QuestionsResult.ApiError -> LogLevel.ERROR
    }

    // Kind and counts only: no titles, no error messages (they may echo server content).
    private fun QuestionsResult.describe(): String = when (this) {
        is QuestionsResult.Success -> "Success(items=${questions.size}, hasMore=$hasMore)"
        is QuestionsResult.NetworkError -> "NetworkError"
        is QuestionsResult.ApiError -> "ApiError(httpCode=$httpCode)"
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
