package com.alexfin90.stackoverflowhomeassignment.core.domain

import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult

class FakeQuestionsRepository : QuestionsRepository {
    var onLatest: suspend (unansweredOnly: Boolean, page: Int) -> QuestionsResult =
        { _, _ -> QuestionsResult.Success(emptyList(), hasMore = false) }
    var onQuestion: suspend (id: Long) -> QuestionResult = { QuestionResult.NotFound }

    /** Every list call received, as (unansweredOnly, page). */
    val calls = mutableListOf<Pair<Boolean, Int>>()

    override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int): QuestionsResult {
        calls += unansweredOnly to page
        return onLatest(unansweredOnly, page)
    }

    override suspend fun getQuestion(id: Long): QuestionResult = onQuestion(id)
}

fun question(id: Long) = Question(
    id = id, title = "Question $id", link = "https://so/$id", creationDateEpochSeconds = 0,
    answerCount = 0, score = 0, ownerName = null, tags = emptyList(),
)
