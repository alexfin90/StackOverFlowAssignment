package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.lifecycle.SavedStateHandle
import com.alexfin90.stackoverflowhomeassignment.core.domain.LoadQuestionsPageUseCase
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeQuestionsRepository()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        HomeViewModel(LoadQuestionsPageUseCase(repository), handle)

    private fun HomeViewModel.questions() = state.value.content as HomeContent.Questions

    @Test
    fun `starts in loading and requests the first page unfiltered`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        assertEquals(HomeContent.Loading, vm.state.value.content)
        advanceUntilIdle()

        assertEquals(listOf(false to 1), repository.calls)
    }

    @Test
    fun `success shows the questions`() = runTest(mainRule.dispatcher) {
        repository.onLatest = { _, _ ->
            QuestionsResult.Success(listOf(question(1), question(2)), hasMore = true)
        }
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.questions().items.map { it.id })
        assertTrue(vm.questions().canLoadMore)
    }

    @Test
    fun `empty result shows the empty state`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(HomeContent.Empty, vm.state.value.content)
    }

    @Test
    fun `network error then retry recovers`() = runTest(mainRule.dispatcher) {
        repository.onLatest = { _, _ -> QuestionsResult.NetworkError("offline") }
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(HomeContent.Error(ErrorReason.Network), vm.state.value.content)

        repository.onLatest = { _, _ -> QuestionsResult.Success(listOf(question(1)), false) }
        vm.onRetry()
        assertEquals(HomeContent.Loading, vm.state.value.content)
        advanceUntilIdle()

        assertEquals(1, vm.questions().items.size)
    }

    @Test
    fun `api error maps to server error`() = runTest(mainRule.dispatcher) {
        repository.onLatest = { _, _ -> QuestionsResult.ApiError(500, "boom") }
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(HomeContent.Error(ErrorReason.Server), vm.state.value.content)
    }

    @Test
    fun `toggling the filter reloads from page one with unanswered only`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onUnansweredToggled()
        advanceUntilIdle()

        assertTrue(vm.state.value.unansweredOnly)
        assertEquals(listOf(false to 1, true to 1), repository.calls)
    }

    @Test
    fun `filter survives recreation through SavedStateHandle`() = runTest(mainRule.dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.onUnansweredToggled()
        advanceUntilIdle()

        val recreated = viewModel(handle)
        advanceUntilIdle()

        assertTrue(recreated.state.value.unansweredOnly)
    }

    @Test
    fun `stale response is ignored when the filter changes while loading`() = runTest(mainRule.dispatcher) {
        repository.onLatest = { unanswered, _ ->
            if (unanswered) {
                QuestionsResult.Success(listOf(question(2, answers = 0)), false)
            } else {
                delay(1_000)
                QuestionsResult.Success(listOf(question(1)), false)
            }
        }
        val vm = viewModel()
        vm.onUnansweredToggled()
        advanceUntilIdle()

        assertEquals(listOf(2L), vm.questions().items.map { it.id })
    }

    @Test
    fun `load more appends the next page and removes duplicates`() = runTest(mainRule.dispatcher) {
        repository.onLatest = { _, page ->
            if (page == 1) {
                QuestionsResult.Success(listOf(question(1), question(2)), hasMore = true)
            } else {
                QuestionsResult.Success(listOf(question(2), question(3)), hasMore = false)
            }
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLoadMore()
        assertTrue(vm.questions().isLoadingMore)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L, 3L), vm.questions().items.map { it.id })
        assertFalse(vm.questions().canLoadMore)
        assertFalse(vm.questions().isLoadingMore)
        assertEquals(2, repository.calls.last().second)
    }

    @Test
    fun `load more does nothing when there are no more pages`() = runTest(mainRule.dispatcher) {
        repository.onLatest = { _, _ -> QuestionsResult.Success(listOf(question(1)), hasMore = false) }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLoadMore()
        advanceUntilIdle()

        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `failed load more keeps the list`() = runTest(mainRule.dispatcher) {
        repository.onLatest = { _, page ->
            if (page == 1) {
                QuestionsResult.Success(listOf(question(1)), hasMore = true)
            } else {
                QuestionsResult.NetworkError("offline")
            }
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLoadMore()
        advanceUntilIdle()

        assertEquals(listOf(1L), vm.questions().items.map { it.id })
        assertFalse(vm.questions().isLoadingMore)
    }

    @Test
    fun `changing the filter after loading more replaces the list, it does not append`() = runTest(mainRule.dispatcher) {
        repository.onLatest = { unanswered, page ->
            when {
                unanswered -> QuestionsResult.Success(listOf(question(9, answers = 0)), hasMore = false)
                page == 1 -> QuestionsResult.Success(listOf(question(1), question(2)), hasMore = true)
                else -> QuestionsResult.Success(listOf(question(3)), hasMore = false)
            }
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onLoadMore()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), vm.questions().items.map { it.id })

        vm.onUnansweredToggled()
        advanceUntilIdle()

        assertEquals(listOf(9L), vm.questions().items.map { it.id })
    }
}
