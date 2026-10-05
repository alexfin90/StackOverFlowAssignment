package com.alexfin90.stackoverflowsdk.data.remote

/**
 * Where questions come from over the network. The repository depends on this interface only, so
 * the HTTP stack (today Retrofit/OkHttp) can be replaced, faked in tests or complemented by a
 * local source without touching the repository.
 *
 * Implementations never throw (except coroutine cancellation): failures are [RemoteResult.Failure].
 */
internal interface QuestionsRemoteDataSource {

    suspend fun getLatestQuestions(
        fromDate: Long,
        page: Int,
        pageSize: Int,
        unansweredOnly: Boolean,
        /** When not `null`, only questions created up to this instant (Unix seconds) are returned. */
        toDate: Long? = null,
    ): RemoteResult<QuestionsResponseDto>

    /** Same wrapper as the list: `items` is empty when the id does not exist. */
    suspend fun getQuestion(id: Long): RemoteResult<QuestionsResponseDto>
}
