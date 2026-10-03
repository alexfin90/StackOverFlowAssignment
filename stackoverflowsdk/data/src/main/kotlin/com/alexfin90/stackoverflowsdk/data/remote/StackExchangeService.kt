package com.alexfin90.stackoverflowsdk.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Stack Exchange API v2.3. `order`, `sort` and `site` are fixed on purpose: they define what
 * "latest StackOverflow questions" means and are not exposed to SDK users.
 */
internal interface StackExchangeService {

    @GET("2.3/questions")
    suspend fun latestQuestions(
        @Query("fromdate") fromDate: Long, // Unix time in seconds
        @Query("page") page: Int,
        @Query("pagesize") pageSize: Int,
        @Query("order") order: String = "desc",
        @Query("sort") sort: String = "creation",
        @Query("site") site: String = "stackoverflow",
    ): QuestionsResponseDto

    /** Same as [latestQuestions] but only questions with 0 answers (filtered server side). */
    @GET("2.3/questions/no-answers")
    suspend fun unansweredQuestions(
        @Query("fromdate") fromDate: Long,
        @Query("page") page: Int,
        @Query("pagesize") pageSize: Int,
        @Query("order") order: String = "desc",
        @Query("sort") sort: String = "creation",
        @Query("site") site: String = "stackoverflow",
    ): QuestionsResponseDto
}
