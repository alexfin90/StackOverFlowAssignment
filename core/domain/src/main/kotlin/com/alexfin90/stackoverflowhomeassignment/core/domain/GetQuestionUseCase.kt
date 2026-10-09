package com.alexfin90.stackoverflowhomeassignment.core.domain

import com.alexfin90.stackoverflowhomeassignment.core.domain.QuestionOutcome.*
import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import javax.inject.Inject

sealed interface QuestionOutcome {
    class Found(val question: Question) : QuestionOutcome

    /** The id does not exist, or the question was deleted. */
    data object NotFound : QuestionOutcome

    class Failure(val reason: LoadFailure) : QuestionOutcome
}

/** Loads one question by id and translates the SDK result into the app's [QuestionOutcome]. */
class GetQuestionUseCase @Inject constructor(
    private val repository: QuestionsRepository,
) {
    suspend operator fun invoke(id: Long): QuestionOutcome =
        when (val result = repository.getQuestion(id)) {
            is QuestionResult.Success -> Found(result.question)
            QuestionResult.NotFound -> QuestionOutcome.NotFound
            is QuestionResult.NetworkError -> Failure(LoadFailure.Network)
            is QuestionResult.ApiError -> Failure(LoadFailure.Server)
            is QuestionResult.RateLimited -> Failure(LoadFailure.RateLimited(result.retryAfterSeconds))
        }
}
