package com.alexfin90.stackoverflowhomeassignment.core.domain

import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import javax.inject.Inject

/** The questions loaded so far, after adding a page. */
class QuestionsPage(
    val questions: List<Question>,
    /** `true` when the SDK says another page exists. */
    val hasMore: Boolean,
)

sealed interface QuestionsPageResult {
    class Success(val page: QuestionsPage) : QuestionsPageResult

    class Failure(val reason: LoadFailure) : QuestionsPageResult
}

/**
 * Loads one page of the latest questions and **adds it to what is already loaded**.
 *
 * This is the logic that does not belong in a ViewModel: the SDK returns pages, the screen shows a
 * growing list, and the 30-day window slides between requests, so the same question can come back
 * in two pages and must appear once.
 */
class LoadQuestionsPageUseCase @Inject constructor(
    private val repository: QuestionsRepository,
) {
    /**
     * @param page 1-based. Page 1 starts over and ignores [alreadyLoaded].
     * @param alreadyLoaded the questions of the previous pages, in order.
     */
    suspend operator fun invoke(
        unansweredOnly: Boolean,
        page: Int,
        alreadyLoaded: List<Question> = emptyList(),
    ): QuestionsPageResult =
        when (val result = repository.getLatestQuestions(unansweredOnly, page)) {
            is QuestionsResult.Success -> QuestionsPageResult.Success(
                QuestionsPage(
                    questions = merge(alreadyLoaded, result.questions, page),
                    hasMore = result.hasMore,
                ),
            )
            is QuestionsResult.NetworkError -> QuestionsPageResult.Failure(LoadFailure.Network)
            is QuestionsResult.ApiError -> QuestionsPageResult.Failure(LoadFailure.Server)
        }

    private fun merge(alreadyLoaded: List<Question>, loaded: List<Question>, page: Int): List<Question> =
        if (page == FIRST_PAGE) loaded else (alreadyLoaded + loaded).distinctBy { it.id }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
