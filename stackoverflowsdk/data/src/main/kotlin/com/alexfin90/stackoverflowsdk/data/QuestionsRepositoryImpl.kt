package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.mapper.toDomain
import com.alexfin90.stackoverflowsdk.data.remote.StackExchangeService
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
        try {
            // The API filters with "fromdate" (Unix time, seconds): only questions created at
            // or after it are returned. "Last N days" is therefore now minus N days in seconds.
            // Recomputed on every call, so the window slides slightly between pages.
            val fromDate = nowEpochSeconds() - lastDays * SECONDS_PER_DAY
            val response = if (unansweredOnly) {
                service.unansweredQuestions(fromDate, page, pageSize)
            } else {
                service.latestQuestions(fromDate, page, pageSize)
            }
            QuestionsResult.Success(response.items.map { it.toDomain() }, response.hasMore)
        } catch (e: CancellationException) {
            // Coroutine cancellation is signalled with this exception: swallowing it would keep
            // a cancelled coroutine running. The coroutines runtime handles it, it never crashes the host.
            throw e
        } catch (e: HttpException) {
            QuestionsResult.ApiError(e.code(), e.message())
        } catch (e: IOException) {
            QuestionsResult.NetworkError(e.message)
        } catch (e: SerializationException) {
            QuestionsResult.ApiError(null, "Unreadable response: ${e.message}")
        } catch (e: Exception) {
            // Last line of defence: an unexpected bug must not crash the host app.
            QuestionsResult.ApiError(null, e.message ?: e.javaClass.simpleName)
        }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L // 24 * 60 * 60
    }
}
