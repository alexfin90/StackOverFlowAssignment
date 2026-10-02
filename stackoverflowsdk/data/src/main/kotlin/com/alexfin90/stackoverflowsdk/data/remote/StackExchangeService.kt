package com.alexfin90.stackoverflowsdk.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

internal interface StackExchangeService {

    @GET("2.3/questions")
    suspend fun latestQuestions(
        @Query("fromdate") fromDate: Long,
        @Query("page") page: Int,
        @Query("pagesize") pageSize: Int,
        @Query("order") order: String = "desc",
        @Query("sort") sort: String = "creation",
        @Query("site") site: String = "stackoverflow",
    ): QuestionsResponseDto

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
