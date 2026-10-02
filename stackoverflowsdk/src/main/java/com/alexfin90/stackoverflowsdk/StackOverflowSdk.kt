package com.alexfin90.stackoverflowsdk

import com.alexfin90.stackoverflowsdk.data.createQuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.InternalStackOverflowApi
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository

/**
 * Entry point of the SDK.
 *
 * ```
 * val questions: QuestionsRepository = StackOverflowSdk.create()
 * when (val result = questions.getLatestQuestions(unansweredOnly = true)) {
 *     is QuestionsResult.Success -> ...
 *     is QuestionsResult.NetworkError -> ...
 *     is QuestionsResult.ApiError -> ...
 * }
 * ```
 *
 * [create] does no I/O and needs no `Context`, so it is safe to call at app startup. Network work
 * happens only inside the `suspend` calls, on the caller's coroutine.
 */
public object StackOverflowSdk {

    /** Creates a repository. Keep and reuse the instance: it owns the HTTP client. */
    @OptIn(InternalStackOverflowApi::class)
    public fun create(config: StackOverflowConfig = StackOverflowConfig()): QuestionsRepository =
        createQuestionsRepository(
            baseUrl = config.baseUrl,
            pageSize = config.pageSize,
            lastDays = config.lastDays,
        )
}
