package com.alexfin90.stackoverflowhomeassignment.fake

import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.delay

/** In-memory [QuestionsRepository] with a small delay so loading states are visible. */
class FakeQuestionsRepository(
    private val latencyMillis: Long = 800,
    private val pageSize: Int = 10,
) : QuestionsRepository {

    override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int): QuestionsResult {
        delay(latencyMillis)
        val source = if (unansweredOnly) fakeQuestions.filter { it.answerCount == 0 } else fakeQuestions
        val from = (page - 1) * pageSize
        val items = source.drop(from).take(pageSize)
        return QuestionsResult.Success(items, hasMore = from + pageSize < source.size)
    }

    override suspend fun getQuestion(id: Long): QuestionResult {
        delay(latencyMillis)
        return fakeQuestions.firstOrNull { it.id == id }?.let(QuestionResult::Success)
            ?: QuestionResult.NotFound
    }
}
