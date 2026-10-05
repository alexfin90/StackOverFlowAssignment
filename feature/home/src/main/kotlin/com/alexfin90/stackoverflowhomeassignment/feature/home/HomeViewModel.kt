package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexfin90.stackoverflowsdk.domain.PagerError
import com.alexfin90.stackoverflowsdk.domain.PagerStatus
import com.alexfin90.stackoverflowsdk.domain.QuestionsPager
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerFactory
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The paging itself (joining pages, de-duplicating, one request at a time, what to keep on failure)
 * lives in the SDK's [QuestionsPager]. This ViewModel only chooses which pager is active (one per
 * filter value), asks it to load, and turns its state into what the screen shows.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pagers: QuestionsPagerFactory,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), HomeViewModelContract {

    private val _state = MutableStateFlow(
        HomeUiState(unansweredOnly = savedStateHandle[KEY_UNANSWERED] ?: false),
    )
    override val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private lateinit var pager: QuestionsPager

    /** Everything started for the active pager (collecting it, refresh, loadMore): cancelled together. */
    private var pagerJob: Job? = null
    private lateinit var pagerScope: CoroutineScope

    init {
        start(_state.value.unansweredOnly)
    }

    override fun onUnansweredToggled() {
        val unansweredOnly = !_state.value.unansweredOnly
        savedStateHandle[KEY_UNANSWERED] = unansweredOnly
        start(unansweredOnly)
    }

    override fun onRetry() {
        pagerScope.launch { pager.refresh() }
    }

    /** Safe to call on every scroll event: the pager ignores it if it is already loading. */
    override fun onLoadMore() {
        pagerScope.launch { pager.loadMore() }
    }

    /**
     * A new filter is a new pager. Cancelling the job of the old one stops its request and its
     * collector, so a late answer can never reach the screen.
     */
    private fun start(unansweredOnly: Boolean) {
        pagerJob?.cancel()
        val job = Job(viewModelScope.coroutineContext[Job])
        pagerJob = job
        pagerScope = CoroutineScope(viewModelScope.coroutineContext + job)
        val newPager = pagers.create(unansweredOnly)
        pager = newPager

        _state.update { it.copy(unansweredOnly = unansweredOnly, content = HomeContent.Loading) }
        pagerScope.launch {
            newPager.state.collect { pagerState ->
                _state.update { it.copy(content = pagerState.toContent()) }
            }
        }
        pagerScope.launch { newPager.refresh() }
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
        status is PagerStatus.Failed -> HomeContent.Error(status.error.toErrorReason())
        isLoaded -> HomeContent.Empty
        else -> HomeContent.Loading
    }
}

private fun PagerError.toErrorReason(): ErrorReason = when (this) {
    PagerError.Network -> ErrorReason.Network
    PagerError.Api -> ErrorReason.Server
}
