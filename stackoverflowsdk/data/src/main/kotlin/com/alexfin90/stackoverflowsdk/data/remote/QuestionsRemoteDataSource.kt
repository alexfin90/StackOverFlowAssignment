package com.alexfin90.stackoverflowsdk.data.remote

/**
 * Where questions come from over the network. The client implementation depends on this interface only, so
 * the HTTP stack (today Retrofit/OkHttp) can be replaced, faked in tests or complemented by a
 * local source without touching it.
 *
 * Implementations never throw (except coroutine cancellation): failures are [RemoteResult.Failure].
 */
internal interface QuestionsRemoteDataSource {

    suspend fun getLatestQuestions(
        fromDate: Long,
        page: Int,
        pageSize: Int,
        unansweredOnly: Boolean,
    ): RemoteResult<QuestionsResponseDto>
}
