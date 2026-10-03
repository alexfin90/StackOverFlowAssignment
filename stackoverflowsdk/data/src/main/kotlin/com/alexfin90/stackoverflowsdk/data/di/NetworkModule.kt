package com.alexfin90.stackoverflowsdk.data.di

import com.alexfin90.stackoverflowsdk.data.remote.StackExchangeService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

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
    fun provideOkHttpClient(connectTimeoutSeconds: Long, callTimeoutSeconds: Long): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .build()

    fun provideRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun provideService(retrofit: Retrofit): StackExchangeService =
        retrofit.create(StackExchangeService::class.java)
}
