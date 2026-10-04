package com.alexfin90.stackoverflowhomeassignment.feature.home

import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult

class FakeQuestionsRepository : QuestionsRepository {

    var onLatest: suspend (unansweredOnly: Boolean, page: Int) -> QuestionsResult =
        { _, _ -> QuestionsResult.Success(emptyList(), hasMore = false) }

    /** Every call received, as (unansweredOnly, page). */
    val calls = mutableListOf<Pair<Boolean, Int>>()

    override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int): QuestionsResult {
        calls += unansweredOnly to page
        return onLatest(unansweredOnly, page)
    }

    override suspend fun getQuestion(id: Long): QuestionResult = QuestionResult.NotFound
}

fun question(id: Long, answers: Int = 1, title: String = "Question $id") = Question(
    id = id,
    title = title,
    link = "https://stackoverflow.com/q/$id",
    creationDateEpochSeconds = 1_700_000_000,
    answerCount = answers,
    score = 1,
    ownerName = "user$id",
    tags = emptyList(),
)
