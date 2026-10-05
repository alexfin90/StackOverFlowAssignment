package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.lifecycle.SavedStateHandle
import com.alexfin90.stackoverflowsdk.domain.PagerError
import com.alexfin90.stackoverflowsdk.domain.PagerStatus
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The ViewModel against a hand-driven pager: it only maps the pager's state and forwards events. */
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val pagers = FakePagerFactory()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = HomeViewModel(pagers, handle)

    private fun HomeViewModel.questions() = state.value.content as HomeContent.Questions

    private fun pagerState(
        vararg ids: Long,
        hasMore: Boolean = false,
        status: PagerStatus = PagerStatus.Idle,
        isLoaded: Boolean = true,
    ) = QuestionsPagerState(ids.map { question(it) }, hasMore, status, isLoaded)

    @Test
    fun `starts loading and refreshes the first pager`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(HomeContent.Loading, vm.state.value.content)
        assertEquals(1, pagers.created.size)
        assertFalse(pagers.last.unansweredOnly)
        assertEquals(1, pagers.last.refreshes)
    }

    @Test
    fun `shows the pager's questions`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        pagers.last.state.value = pagerState(1, 2, hasMore = true)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.questions().items.map { it.id })
        assertTrue(vm.questions().canLoadMore)
        assertFalse(vm.questions().isLoadingMore)
    }

    @Test
    fun `loaded and empty shows the empty state, not loaded shows loading`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(HomeContent.Loading, vm.state.value.content)

        pagers.last.state.value = pagerState(isLoaded = true)
        advanceUntilIdle()

        assertEquals(HomeContent.Empty, vm.state.value.content)
    }

    @Test
    fun `a failure before any question maps to the matching error`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        pagers.last.state.value = pagerState(isLoaded = false, status = PagerStatus.Failed(PagerError.Network))
        advanceUntilIdle()
        assertEquals(HomeContent.Error(ErrorReason.Network), vm.state.value.content)

        pagers.last.state.value = pagerState(isLoaded = false, status = PagerStatus.Failed(PagerError.Api))
        advanceUntilIdle()
        assertEquals(HomeContent.Error(ErrorReason.Server), vm.state.value.content)
    }

    @Test
    fun `retry asks the same pager to refresh again`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRetry()
        advanceUntilIdle()

        assertEquals(1, pagers.created.size)
        assertEquals(2, pagers.last.refreshes)
    }

    @Test
    fun `toggling the filter creates a new pager for the new filter and refreshes it`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onUnansweredToggled()
        advanceUntilIdle()

        assertTrue(vm.state.value.unansweredOnly)
        assertEquals(2, pagers.created.size)
        assertTrue(pagers.last.unansweredOnly)
        assertEquals(1, pagers.last.refreshes)
    }

    @Test
    fun `the old pager can no longer reach the screen after a filter change`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val old = pagers.last

        vm.onUnansweredToggled()
        advanceUntilIdle()
        old.state.value = pagerState(1, 2, 3) // a late answer of the previous filter
        advanceUntilIdle()

        assertEquals(HomeContent.Loading, vm.state.value.content)

        pagers.last.state.value = pagerState(9)
        advanceUntilIdle()
        assertEquals(listOf(9L), vm.questions().items.map { it.id })
    }

    @Test
    fun `the filter is restored from the saved state`() = runTest(mainRule.dispatcher) {
        val handle = SavedStateHandle()
        viewModel(handle).onUnansweredToggled()
        advanceUntilIdle()

        val recreated = viewModel(handle)
        advanceUntilIdle()

        assertTrue(recreated.state.value.unansweredOnly)
        assertTrue(pagers.last.unansweredOnly)
    }

    @Test
    fun `load more is forwarded to the pager`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLoadMore()
        advanceUntilIdle()

        assertEquals(1, pagers.last.loadMores)
    }

    @Test
    fun `shows the footer loader while the pager loads more`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        pagers.last.state.value = pagerState(1, 2, hasMore = true, status = PagerStatus.LoadingMore)
        advanceUntilIdle()

        assertTrue(vm.questions().isLoadingMore)
    }

    @Test
    fun `a failed load more keeps the list`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        pagers.last.state.value =
            pagerState(1, 2, hasMore = true, status = PagerStatus.Failed(PagerError.Network))
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.questions().items.map { it.id })
        assertFalse(vm.questions().isLoadingMore)
    }

    @Test
    fun `refreshing with questions already shown keeps them on screen`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        pagers.last.state.value = pagerState(1, 2, status = PagerStatus.Refreshing)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.questions().items.map { it.id })
    }
}
