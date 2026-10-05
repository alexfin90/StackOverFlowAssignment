package com.alexfin90.stackoverflowhomeassignment.feature.detail

import androidx.lifecycle.SavedStateHandle
import com.alexfin90.stackoverflowhomeassignment.core.domain.GetQuestionUseCase
import com.alexfin90.stackoverflowsdk.domain.Question
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository(var result: QuestionResult) : QuestionsRepository {
        val requestedIds = mutableListOf<Long>()
        override suspend fun getLatestQuestions(unansweredOnly: Boolean, page: Int): QuestionsResult =
            QuestionsResult.Success(emptyList(), false)
        override suspend fun getQuestion(id: Long): QuestionResult {
            requestedIds += id
            return result
        }
    }

    private val question = Question(
        id = 42, title = "Why?", link = "https://so/42", creationDateEpochSeconds = 1,
        answerCount = 2, score = 5, ownerName = "ann", tags = listOf("kotlin", "android"),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    // Same shape SavedStateHandle gets from the navigation route (and from a deep link).
    private fun handle(id: Long) = SavedStateHandle(mapOf("questionId" to id))

    @Test
    fun `loads the question whose id comes from the route`() = runTest(dispatcher) {
        val repository = FakeRepository(QuestionResult.Success(question))
        val vm = DetailViewModel(GetQuestionUseCase(repository), handle(42))

        assertEquals(DetailUiState.Loading, vm.state.value)
        advanceUntilIdle()

        assertEquals(listOf(42L), repository.requestedIds)
        val content = vm.state.value as DetailUiState.Content
        assertEquals("Why?", content.question.title)
        assertEquals(listOf("kotlin", "android"), content.question.tags)
    }

    @Test
    fun `unknown id shows not found`() = runTest(dispatcher) {
        val vm = DetailViewModel(GetQuestionUseCase(FakeRepository(QuestionResult.NotFound)), handle(1))
        advanceUntilIdle()

        assertEquals(DetailUiState.NotFound, vm.state.value)
    }

    @Test
    fun `network error then retry recovers`() = runTest(dispatcher) {
        val repository = FakeRepository(QuestionResult.NetworkError("offline"))
        val vm = DetailViewModel(GetQuestionUseCase(repository), handle(42))
        advanceUntilIdle()
        assertEquals(DetailUiState.Error(DetailError.Network), vm.state.value)

        repository.result = QuestionResult.Success(question)
        vm.onRetry()
        assertEquals(DetailUiState.Loading, vm.state.value)
        advanceUntilIdle()

        assertTrue(vm.state.value is DetailUiState.Content)
    }

    @Test
    fun `api error maps to server error`() = runTest(dispatcher) {
        val vm = DetailViewModel(GetQuestionUseCase(FakeRepository(QuestionResult.ApiError(500, "boom"))), handle(1))
        advanceUntilIdle()

        assertEquals(DetailUiState.Error(DetailError.Server), vm.state.value)
    }
}
