package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.mapper.toDomain
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.data.remote.RemoteResult
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult.*
import kotlinx.coroutines.CancellationException

/**
 * Turns remote outcomes into the SDK's public results. It knows nothing about Retrofit, OkHttp
 * or HTTP exceptions: that is the job of the [QuestionsRemoteDataSource].
 */
internal class QuestionsRepositoryImpl(
    private val remote: QuestionsRemoteDataSource,
    private val pageSize: Int,
    private val lastDays: Int,
    private val nowEpochSeconds: () -> Long,
) : QuestionsRepository {

    override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int): QuestionsResult {
        // The API filters with "fromdate" (Unix time, seconds): only questions created at
        // or after it are returned. "Last N days" is therefore now minus N days in seconds.
        // Recomputed on every call, so the window slides slightly between pages.
        val fromDate = nowEpochSeconds() - lastDays * SECONDS_PER_DAY
        return when (val result = remote.getLatestQuestions(fromDate, page, pageSize, unansweredOnly)) {
            is RemoteResult.Success -> mapSuccess(result)
            is RemoteResult.Failure.Network -> NetworkError(result.message)
            is RemoteResult.Failure.Http -> ApiError(result.code, result.message)
            is RemoteResult.Failure.Malformed ->
                ApiError(null, "Unreadable response: ${result.message}")
            is RemoteResult.Failure.Unexpected -> ApiError(null, result.message)
            is RemoteResult.Failure.RateLimited -> RateLimited(result.retryAfterSeconds)
        }
    }

    override suspend fun getQuestion(id: Long): QuestionResult =
        when (val result = remote.getQuestion(id)) {
            is RemoteResult.Success -> mapQuestion(result)
            is RemoteResult.Failure.Network -> QuestionResult.NetworkError(result.message)
            is RemoteResult.Failure.Http -> QuestionResult.ApiError(result.code, result.message)
            is RemoteResult.Failure.Malformed ->
                QuestionResult.ApiError(null, "Unreadable response: ${result.message}")
            is RemoteResult.Failure.Unexpected -> QuestionResult.ApiError(null, result.message)
            is RemoteResult.Failure.RateLimited -> QuestionResult.RateLimited(result.retryAfterSeconds)
        }

    private fun mapQuestion(result: RemoteResult.Success<com.alexfin90.stackoverflowsdk.data.remote.QuestionsResponseDto>): QuestionResult =
        try {
            result.value.items.firstOrNull()?.let { QuestionResult.Success(it.toDomain()) }
                ?: QuestionResult.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mapping bug: report it as a value, never crash the host.
            QuestionResult.ApiError(null, e.message ?: e.javaClass.simpleName)
        }

    private fun mapSuccess(result: RemoteResult.Success<com.alexfin90.stackoverflowsdk.data.remote.QuestionsResponseDto>): QuestionsResult =
        try {
            QuestionsResult.Success(result.value.items.map { it.toDomain() }, result.value.hasMore)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mapping bug: report it as a value, never crash the host.
            QuestionsResult.ApiError(null, e.message ?: e.javaClass.simpleName)
        }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L // 24 * 60 * 60
    }
}
