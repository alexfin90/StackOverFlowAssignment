package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.remote.StackExchangeService
import com.alexfin90.stackoverflowsdk.domain.InternalStackOverflowApi
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Only way for the facade module to obtain the implementation: everything else in
 * this module is `internal`.
 */
@InternalStackOverflowApi
public fun createQuestionsRepository(
    baseUrl: String,
    pageSize: Int,
    lastDays: Int,
    nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
): QuestionsRepository {
    val json = Json { ignoreUnknownKeys = true }
    val service = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(StackExchangeService::class.java)
    return QuestionsRepositoryImpl(service, pageSize, lastDays, nowEpochSeconds)
}
