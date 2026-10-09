package com.alexfin90.stackoverflowsdk.data

import com.alexfin90.stackoverflowsdk.data.di.NetworkModule
import com.alexfin90.stackoverflowsdk.data.logging.LogcatSink
import com.alexfin90.stackoverflowsdk.data.remote.QuestionsRemoteDataSource
import com.alexfin90.stackoverflowsdk.domain.QuestionsPager
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerFactory
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository

/**
 * Everything the facade hands out, built over **one** HTTP client and one configuration.
 *
 * Public only because Kotlin `internal` stops at the Gradle module boundary; host apps cannot reach
 * it (the facade depends on `:data` with `implementation`). Not part of the supported API.
 */
public class QuestionsServices(
    public val repository: QuestionsRepository,
    public val pagers: QuestionsPagerFactory,
)

/** @param loggingEnabled when `true` every repository call is logged to Logcat. */
public fun createQuestionsServices(
    baseUrl: String,
    pageSize: Int,
    lastDays: Int,
    connectTimeoutSeconds: Long = 10,
    callTimeoutSeconds: Long = 30,
    loggingEnabled: Boolean = false,
    nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
): QuestionsServices {
    val client = NetworkModule.provideOkHttpClient(connectTimeoutSeconds, callTimeoutSeconds)
    val retrofit = NetworkModule.provideRetrofit(baseUrl, client, NetworkModule.provideJson())
    val service = NetworkModule.provideService(retrofit)
    val remote = NetworkModule.provideRemoteDataSource(service)
    val repository: QuestionsRepository = QuestionsRepositoryImpl(remote, pageSize, lastDays, nowEpochSeconds)
    return QuestionsServices(
        repository = if (loggingEnabled) LoggingQuestionsRepository(repository, LogcatSink) else repository,
        pagers = pagerFactory(remote, pageSize, lastDays, nowEpochSeconds),
    )
}

private fun pagerFactory(
    remote: QuestionsRemoteDataSource,
    pageSize: Int,
    lastDays: Int,
    nowEpochSeconds: () -> Long,
) = object : QuestionsPagerFactory {
    override fun create(unansweredOnly: Boolean): QuestionsPager =
        QuestionsPagerImpl(remote, unansweredOnly, pageSize, lastDays, nowEpochSeconds)
}
