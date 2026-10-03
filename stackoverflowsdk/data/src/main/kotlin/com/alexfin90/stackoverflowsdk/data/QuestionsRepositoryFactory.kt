package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.di.NetworkModule
import com.alexfin90.stackoverflowsdk.domain.InternalStackOverflowApi
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository

/**
 * Only way for the facade module to obtain the implementation: everything else in
 * this module is `internal`.
 */
@InternalStackOverflowApi
public fun createQuestionsRepository(
    baseUrl: String,
    pageSize: Int,
    lastDays: Int,
    connectTimeoutSeconds: Long = 10,
    callTimeoutSeconds: Long = 30,
    nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
): QuestionsRepository {
    val client = NetworkModule.provideOkHttpClient(connectTimeoutSeconds, callTimeoutSeconds)
    val retrofit = NetworkModule.provideRetrofit(baseUrl, client, NetworkModule.provideJson())
    val service = NetworkModule.provideService(retrofit)
    return QuestionsRepositoryImpl(service, pageSize, lastDays, nowEpochSeconds)
}
