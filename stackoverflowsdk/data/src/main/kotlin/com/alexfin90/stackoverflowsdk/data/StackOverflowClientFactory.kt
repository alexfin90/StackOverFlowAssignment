package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.di.NetworkModule
import com.alexfin90.stackoverflowsdk.domain.StackOverflowClient
import com.alexfin90.stackoverflowsdk.domain.StackOverflowLogger

/**
 * Only way for the facade module to obtain the implementation: everything else in
 * this module is `internal`.
 *
 * Public only because Kotlin `internal` stops at the Gradle module boundary. Host apps cannot
 * reach it: the facade depends on `:data` with `implementation`. Not part of the supported API.
 *
 * @param logger when not `null` every call is logged through it; `null` means no logging at all.
 */
public fun createStackOverflowClient(
    baseUrl: String,
    pageSize: Int,
    lastDays: Int,
    connectTimeoutSeconds: Long = 10,
    callTimeoutSeconds: Long = 30,
    logger: StackOverflowLogger? = null,
    nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
): StackOverflowClient {
    val client = NetworkModule.provideOkHttpClient(connectTimeoutSeconds, callTimeoutSeconds)
    val retrofit = NetworkModule.provideRetrofit(baseUrl, client, NetworkModule.provideJson())
    val service = NetworkModule.provideService(retrofit)
    val remote = NetworkModule.provideRemoteDataSource(service)
    val sdkClient: StackOverflowClient =
        StackOverflowClientImpl(remote, pageSize, lastDays, nowEpochSeconds)
    return if (logger != null) LoggingStackOverflowClient(sdkClient, logger) else sdkClient
}
