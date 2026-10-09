package com.alexfin90.stackoverflowsdk.data.di

import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.data.remote.UrlConnectionQuestionsRemoteDataSource
import kotlinx.serialization.json.Json

/**
 * Single place where the networking stack is assembled (the SDK equivalent of a DI module).
 * No DI framework on purpose: it would be imposed on the host app.
 */
internal object NetworkModule {

    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    /**
     * [connectTimeoutSeconds]: max time to open the connection (fails fast without network).
     * [callTimeoutSeconds]: hard cap for the whole call, including slow or trickling responses.
     */
    fun provideRemoteDataSource(
        baseUrl: String,
        connectTimeoutSeconds: Long,
        callTimeoutSeconds: Long,
        json: Json = provideJson(),
    ): QuestionsRemoteDataSource =
        UrlConnectionQuestionsRemoteDataSource(
            baseUrl = baseUrl,
            json = json,
            connectTimeoutMillis = (connectTimeoutSeconds * MILLIS_PER_SECOND).toInt(),
            callTimeoutMillis = callTimeoutSeconds * MILLIS_PER_SECOND,
        )

    private const val MILLIS_PER_SECOND = 1_000L
}
