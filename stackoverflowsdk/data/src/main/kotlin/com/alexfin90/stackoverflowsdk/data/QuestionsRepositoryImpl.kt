package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.mapper.toDomain
import com.alexfin90.stackoverflowsdk.data.remote.StackExchangeService
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

internal class QuestionsRepositoryImpl(
    private val service: StackExchangeService,
    private val pageSize: Int,
    private val lastDays: Int,
    private val nowEpochSeconds: () -> Long,
) : QuestionsRepository {

    override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int): QuestionsResult =
        call(
            request = {
                // The API filters with "fromdate" (Unix time, seconds): only questions created at
                // or after it are returned. "Last N days" is therefore now minus N days in seconds.
                // Recomputed on every call, so the window slides slightly between pages.
                val fromDate = nowEpochSeconds() - lastDays * SECONDS_PER_DAY
                if (unansweredOnly) {
                    service.unansweredQuestions(fromDate, page, pageSize)
                } else {
                    service.latestQuestions(fromDate, page, pageSize)
                }
            },
            onSuccess = { response ->
                QuestionsResult.Success(response.items.map { it.toDomain() }, response.hasMore)
            },
            onNetworkError = QuestionsResult::NetworkError,
            onApiError = QuestionsResult::ApiError,
        )

    override suspend fun getQuestion(id: Long): QuestionResult =
        call(
            request = { service.question(id) },
            onSuccess = { response ->
                response.items.firstOrNull()?.let { QuestionResult.Success(it.toDomain()) }
                    ?: QuestionResult.NotFound
            },
            onNetworkError = QuestionResult::NetworkError,
            onApiError = QuestionResult::ApiError,
        )

    /**
     * Single place that turns every failure into a value, so nothing is thrown across the SDK
     * boundary and the host app cannot crash because of us.
     */
    private suspend fun <T, R> call(
        request: suspend () -> T,
        onSuccess: (T) -> R,
        onNetworkError: (String?) -> R,
        onApiError: (Int?, String) -> R,
    ): R =
        try {
            onSuccess(request())
        } catch (e: CancellationException) {
            // Coroutine cancellation is signalled with this exception: swallowing it would keep
            // a cancelled coroutine running. The coroutines runtime handles it, it never crashes the host.
            throw e
        } catch (e: HttpException) {
            onApiError(e.code(), e.message())
        } catch (e: IOException) {
            // Also covers UnknownHostException, ConnectException and SocketTimeoutException
            // (all subclasses): offline, DNS failure, connect/call timeout.
            onNetworkError(e.message)
        } catch (e: SerializationException) {
            onApiError(null, "Unreadable response: ${e.message}")
        } catch (e: Exception) {
            // Last line of defence: an unexpected bug must not crash the host app.
            onApiError(null, e.message ?: e.javaClass.simpleName)
        }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L // 24 * 60 * 60
    }
}
