package com.alexfin90.stackoverflowsdk.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** The only class that knows Retrofit's exceptions: it translates them into [RemoteResult]. */
internal class RetrofitQuestionsRemoteDataSource(
    private val service: StackExchangeService,
    private val json: Json,
) : QuestionsRemoteDataSource {

    override suspend fun getLatestQuestions(
        fromDate: Long,
        page: Int,
        pageSize: Int,
        unansweredOnly: Boolean,
    ): RemoteResult<QuestionsResponseDto> = safeCall {
        if (unansweredOnly) {
            service.unansweredQuestions(fromDate, page, pageSize)
        } else {
            service.latestQuestions(fromDate, page, pageSize)
        }
    }

    override suspend fun getQuestion(id: Long): RemoteResult<QuestionsResponseDto> =
        safeCall { service.question(id) }

    private suspend fun <T> safeCall(request: suspend () -> T): RemoteResult<T> =
        try {
            RemoteResult.Success(request())
        } catch (e: CancellationException) {
            // Coroutine cancellation is signalled with this exception: swallowing it would keep
            // a cancelled coroutine running. The coroutines runtime handles it, it never crashes the host.
            throw e
        } catch (e: HttpException) {
            // Every API error is HTTP 400: the real kind is error_id in the body.
            if (e.isThrottleViolation()) RemoteResult.Failure.RateLimited(retryAfterSeconds = null)
            else RemoteResult.Failure.Http(e.code(), e.message())
        } catch (e: IOException) {
            // Also covers UnknownHostException, ConnectException and SocketTimeoutException
            // (all subclasses): offline, DNS failure, connect/call timeout.
            RemoteResult.Failure.Network(e.message)
        } catch (e: SerializationException) {
            RemoteResult.Failure.Malformed(e.message)
        } catch (e: Exception) {
            // Last line of defence: an unexpected bug must not crash the host app.
            RemoteResult.Failure.Unexpected(e.message ?: e.javaClass.simpleName)
        }



    private companion object {
        const val THROTTLE_VIOLATION = 502
    }

    private fun HttpException.isThrottleViolation(): Boolean {
        val body = response()?.errorBody()?.string() ?: return false
        // The body may not be JSON (a proxy, a gateway page): then it is not a throttle error.
        val error = runCatching { json.decodeFromString<ErrorResponseDto>(body) }.getOrNull()
        return error?.errorId == THROTTLE_VIOLATION
    }
}
