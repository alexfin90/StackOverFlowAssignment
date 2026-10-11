package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.api.StackOverflowLogger
import com.alexfin90.stackoverflowsdk.data.di.NetworkModule
import com.alexfin90.stackoverflowsdk.data.logging.HostLoggerSink
import com.alexfin90.stackoverflowsdk.data.logging.LogSink
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.data.logging.LogcatSink

/**
 * Only way for the facade module to obtain the implementation: everything else in
 * this module is `internal`.
 *
 * Public only because Kotlin `internal` stops at the Gradle module boundary. Host apps cannot
 * reach it: the facade depends on `:data` with `implementation`. Not part of the supported API.
 *
 * @param loggingEnabled when `true` every call is logged to Logcat; `false` means no logging at all.
 * @param logger when not `null`, every call is logged to it instead of Logcat, whatever [loggingEnabled] says.
 */
public fun createQuestionsRepository(
    baseUrl: String,
    pageSize: Int,
    lastDays: Int,
    connectTimeoutSeconds: Long = 10,
    callTimeoutSeconds: Long = 30,
    loggingEnabled: Boolean = false,
    logger: StackOverflowLogger? = null,
    nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
): QuestionsRepository {
    val client = NetworkModule.provideOkHttpClient(connectTimeoutSeconds, callTimeoutSeconds)
    val retrofit = NetworkModule.provideRetrofit(baseUrl, client, NetworkModule.provideJson())
    val service = NetworkModule.provideService(retrofit)
    val remote = NetworkModule.provideRemoteDataSource(service)
    val repository: QuestionsRepository =
        QuestionsRepositoryImpl(remote, pageSize, lastDays, nowEpochSeconds)
    val sink: LogSink? = when {
        logger != null -> HostLoggerSink(logger)
        loggingEnabled -> LogcatSink
        else -> null
    }
    // No sink, no decorator: the default path pays nothing.
    return if (sink != null) LoggingQuestionsRepository(repository, sink) else repository
}
