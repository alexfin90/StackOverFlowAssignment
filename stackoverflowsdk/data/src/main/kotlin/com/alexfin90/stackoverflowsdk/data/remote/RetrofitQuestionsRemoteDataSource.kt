package com.alexfin90.stackoverflowsdk.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** The only class that knows Retrofit's exceptions: it translates them into [RemoteResult]. */
internal class RetrofitQuestionsRemoteDataSource(
    private val service: StackExchangeService,
) : QuestionsRemoteDataSource {

    override suspend fun getLatestQuestions(
        fromDate: Long,
        page: Int,
        pageSize: Int,
        unansweredOnly: Boolean,
        toDate: Long?,
    ): RemoteResult<QuestionsResponseDto> = safeCall {
        if (unansweredOnly) {
            service.unansweredQuestions(fromDate, page, pageSize, toDate)
        } else {
            service.latestQuestions(fromDate, page, pageSize, toDate)
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
            RemoteResult.Failure.Http(e.code(), e.message())
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
}
