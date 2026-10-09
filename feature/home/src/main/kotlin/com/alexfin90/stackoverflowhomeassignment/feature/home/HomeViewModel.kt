package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexfin90.stackoverflowhomeassignment.core.dispatcher.DefaultDispatcher
import com.alexfin90.stackoverflowhomeassignment.core.domain.toLoadFailure
import com.alexfin90.stackoverflowsdk.domain.PagerStatus
import com.alexfin90.stackoverflowsdk.domain.QuestionsPager
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerFactory
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The paging itself (joining pages, de-duplicating, one request at a time, what to keep on failure)
 * lives in the SDK's [QuestionsPager]. This ViewModel only turns the chosen filter into a pager and
 * the pager's state into what the screen shows.
 *
 * The screen state is a single flow: the filter is the input, and `flatMapLatest` gives each value
 * its own pager. Changing the filter cancels the previous inner flow, and with it the collector, the
 * refresh and the "load more" of the old pager, so a late answer can never reach the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pagers: QuestionsPagerFactory,
    private val savedStateHandle: SavedStateHandle,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel(), HomeViewModelContract {

    private val unansweredOnly = savedStateHandle.getStateFlow(KEY_UNANSWERED, false)

    // Events for the active pager. Dropped if nothing is running, which is fine: they are requests, not state.
    private val retryRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val loadMoreRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Lazily, not WhileSubscribed: the pager and the pages already loaded live as long as the ViewModel,
    // so coming back from the background does not start again from page 1.
    override val state: StateFlow<HomeUiState> = unansweredOnly
        .flatMapLatest { unanswered -> screenState(unanswered) }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = HomeUiState(unansweredOnly = unansweredOnly.value),
        )

    override fun onUnansweredToggled() {
        savedStateHandle[KEY_UNANSWERED] = !unansweredOnly.value
    }

    override fun onRetry() {
        retryRequests.tryEmit(Unit)
    }

    /** Safe to call on every scroll event: the pager ignores it if it is already loading. */
    override fun onLoadMore() {
        loadMoreRequests.tryEmit(Unit)
    }

    private fun screenState(unanswered: Boolean): Flow<HomeUiState> = channelFlow {
        val pager = pagers.create(unanswered)
        launch { pager.refresh() }
        launch { retryRequests.collect { pager.refresh() } }
        launch { loadMoreRequests.collect { pager.loadMore() } }
        pager.state.collect { send(HomeUiState(unanswered, it.toContent())) }
    }

    private companion object {
        const val KEY_UNANSWERED = "unanswered_only"
    }
}

private fun QuestionsPagerState.toContent(): HomeContent {
    val status = status // a property of another module cannot be smart-cast
    return when {
        // With questions to show, a refresh or a failed "load more" keeps them on screen.
        questions.isNotEmpty() -> HomeContent.Questions(
            items = questions.map { it.toItem() }.toImmutableList(),
            canLoadMore = hasMore,
            isLoadingMore = status == PagerStatus.LoadingMore,
        )
        status is PagerStatus.Failed -> HomeContent.Error(status.error.toLoadFailure())
        isLoaded -> HomeContent.Empty
        else -> HomeContent.Loading
    }
}
