package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.mapper.toDomain
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.data.remote.RemoteResult
import com.alexfin90.stackoverflowsdk.domain.StackOverflowClient
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.CancellationException

/**
 * Turns remote outcomes into the SDK's public results. It knows nothing about Retrofit, OkHttp
 * or HTTP exceptions: that is the job of the [QuestionsRemoteDataSource].
 */
internal class StackOverflowClientImpl(
    private val remote: QuestionsRemoteDataSource,
    private val pageSize: Int,
    private val lastDays: Int,
    private val nowEpochSeconds: () -> Long,
) : StackOverflowClient {

    override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int): QuestionsResult {
        // The API filters with "fromdate" (Unix time, seconds): only questions created at
        // or after it are returned. "Last N days" is therefore now minus N days in seconds.
        // Recomputed on every call, so the window slides slightly between pages.
        val fromDate = nowEpochSeconds() - lastDays * SECONDS_PER_DAY
        return when (val result = remote.getLatestQuestions(fromDate, page, pageSize, unansweredOnly)) {
            is RemoteResult.Success -> mapSuccess(result)
            is RemoteResult.Failure.Network -> QuestionsResult.NetworkError(result.message)
            is RemoteResult.Failure.Http -> QuestionsResult.ApiError(result.code, result.message)
            is RemoteResult.Failure.Malformed ->
                QuestionsResult.ApiError(null, "Unreadable response: ${result.message}")
            is RemoteResult.Failure.Unexpected -> QuestionsResult.ApiError(null, result.message)
        }
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
