package com.alexfin90.stackoverflowhomeassignment.core.domain

import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetQuestionUseCaseTest {

    private val repository = FakeQuestionsRepository()
    private val useCase = GetQuestionUseCase(repository)

    @Test
    fun `found question`() = runTest {
        repository.onQuestion = { QuestionResult.Success(question(it)) }

        val result = useCase(42) as QuestionOutcome.Found

        assertEquals(42L, result.question.id)
    }

    @Test
    fun `missing question`() = runTest {
        repository.onQuestion = { QuestionResult.NotFound }

        assertEquals(QuestionOutcome.NotFound, useCase(1))
    }

    @Test
    fun `network error becomes a Network failure`() = runTest {
        repository.onQuestion = { QuestionResult.NetworkError("offline") }

        assertEquals(LoadFailure.Network, (useCase(1) as QuestionOutcome.Failure).reason)
    }

    @Test
    fun `api error becomes a Server failure`() = runTest {
        repository.onQuestion = { QuestionResult.ApiError(500, "boom") }

        assertEquals(LoadFailure.Server, (useCase(1) as QuestionOutcome.Failure).reason)
    }
}
