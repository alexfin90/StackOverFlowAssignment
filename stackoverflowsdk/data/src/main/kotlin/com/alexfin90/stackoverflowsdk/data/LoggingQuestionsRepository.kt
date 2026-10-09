package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.logging.LogLevel
import com.alexfin90.stackoverflowsdk.data.logging.LogSink
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult

/**
 * Decorator that logs one line per call and delegates everything else: the repository itself
 * stays free of logging. Only created when logging is enabled, so the default path pays nothing.
 */
internal class LoggingQuestionsRepository(
    private val delegate: QuestionsRepository,
    private val log: LogSink,
    private val nanoTime: () -> Long = System::nanoTime,
) : QuestionsRepository {

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

    override suspend fun getQuestion(id: Long): QuestionResult {
        val start = nanoTime()
        val result = delegate.getQuestion(id)
        val millis = (nanoTime() - start) / NANOS_PER_MILLI
        safeLog(
            level = result.logLevel(),
            message = "getQuestion(id=$id) -> ${result.describe()} in $millis ms",
        )
        return result
    }

    /** A failing sink (e.g. Logcat unavailable) must never break a request. */
    private fun safeLog(level: LogLevel, message: String) {
        try {
            log(level, message)
        } catch (_: Exception) {
            // Swallowed on purpose.
        }
    }

    private fun QuestionsResult.logLevel(): LogLevel =  when (this) {
        is QuestionsResult.Success -> LogLevel.DEBUG
        is QuestionsResult.NetworkError -> LogLevel.WARN
        is QuestionsResult.ApiError -> LogLevel.ERROR
        is QuestionsResult.RateLimited -> LogLevel.ERROR
    }

    private fun QuestionResult.logLevel(): LogLevel = when (this) {
        is QuestionResult.Success, QuestionResult.NotFound -> LogLevel.DEBUG
        is QuestionResult.NetworkError -> LogLevel.WARN
        is QuestionResult.ApiError -> LogLevel.ERROR
    }

    private fun QuestionResult.describe(): String = when (this) {
        is QuestionResult.Success -> "Success"
        QuestionResult.NotFound -> "NotFound"
        is QuestionResult.NetworkError -> "NetworkError"
        is QuestionResult.ApiError -> "ApiError(httpCode=$httpCode)"
    }

    // Kind and counts only: no titles, no error messages (they may echo server content).
    private fun QuestionsResult.describe(): String = when (this) {
        is QuestionsResult.Success -> "Success(items=${questions.size}, hasMore=$hasMore)"
        is QuestionsResult.NetworkError -> "NetworkError"
        is QuestionsResult.ApiError -> "ApiError(httpCode=$httpCode)"
        is QuestionsResult.RateLimited -> "RateLimited(retryAfterSeconds=$retryAfterSeconds)"
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
