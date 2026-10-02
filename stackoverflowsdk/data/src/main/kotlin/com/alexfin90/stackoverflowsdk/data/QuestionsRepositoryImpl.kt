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
            val fromDate = nowEpochSeconds() - lastDays * SECONDS_PER_DAY
            val response = if (unansweredOnly) {
                service.unansweredQuestions(fromDate, page, pageSize)
            } else {
                service.latestQuestions(fromDate, page, pageSize)
            }
            QuestionsResult.Success(response.items.map { it.toDomain() }, response.hasMore)
        } catch (e: CancellationException) {
            throw e // structured concurrency: cancellation is not an error
        } catch (e: HttpException) {
            QuestionsResult.ApiError(e.code(), e.message())
        } catch (e: IOException) {
            QuestionsResult.NetworkError(e.message)
        } catch (e: SerializationException) {
            QuestionsResult.ApiError(null, "Unreadable response: ${e.message}")
        } catch (e: Exception) {
            // Last line of defence: nothing may cross the SDK boundary and crash the host.
            QuestionsResult.ApiError(null, e.message ?: e.javaClass.simpleName)
        }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
