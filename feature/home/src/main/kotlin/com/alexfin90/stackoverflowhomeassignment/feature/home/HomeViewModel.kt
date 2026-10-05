package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexfin90.stackoverflowhomeassignment.core.domain.LoadFailure
import com.alexfin90.stackoverflowhomeassignment.core.domain.LoadQuestionsPageUseCase
import com.alexfin90.stackoverflowhomeassignment.core.domain.QuestionsPage
import com.alexfin90.stackoverflowhomeassignment.core.domain.QuestionsPageResult
import com.alexfin90.stackoverflowsdk.domain.Question
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val loadQuestionsPage: LoadQuestionsPageUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), HomeViewModelContract {

    private val _state = MutableStateFlow(
        HomeUiState(unansweredOnly = savedStateHandle[KEY_UNANSWERED] ?: false),
    )
    override val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var nextPage = FIRST_PAGE

    /**
     * The questions of the pages loaded so far. The use case adds the next page to them; on page 1 it
     * ignores them, so the "start over" rule lives only there.
     */
    private var loaded: List<Question> = emptyList()

    init {
        refresh()
    }

    override fun onUnansweredToggled() {
        val unansweredOnly = !_state.value.unansweredOnly
        savedStateHandle[KEY_UNANSWERED] = unansweredOnly
        _state.update { it.copy(unansweredOnly = unansweredOnly) }
        refresh()
    }

    override fun onRetry() = refresh()

    override fun onLoadMore() {
        val content = _state.value.content
        if (content !is HomeContent.Questions || !content.canLoadMore || content.isLoadingMore) return
        _state.update { it.copy(content = content.copy(isLoadingMore = true)) }
        load(nextPage)
    }

    /** Restarts from page 1. Cancelling the previous job drops any stale in-flight response. */
    private fun refresh() {
        nextPage = FIRST_PAGE
        _state.update { it.copy(content = HomeContent.Loading) }
        load(FIRST_PAGE)
    }

    private fun load(page: Int) {
        loadJob?.cancel()
        val unansweredOnly = _state.value.unansweredOnly
        loadJob = viewModelScope.launch {
            when (val result = loadQuestionsPage(unansweredOnly, page, loaded)) {
                is QuestionsPageResult.Success -> onSuccess(page, result.page)
                is QuestionsPageResult.Failure -> onError(page, result.reason.toErrorReason())
            }
        }
    }

    private fun onSuccess(page: Int, loadedPage: QuestionsPage) {
        nextPage = page + 1
        loaded = loadedPage.questions
        val items = loaded.map { it.toItem() }
        _state.update { current ->
            current.copy(
                content = if (items.isEmpty()) {
                    HomeContent.Empty
                } else {
                    HomeContent.Questions(items.toImmutableList(), canLoadMore = loadedPage.hasMore)
                },
            )
        }
    }

    private fun onError(page: Int, reason: ErrorReason) {
        _state.update { current ->
            val content = current.content
            when {
                // A failed "load more" keeps the list; scrolling to the end tries again.
                page > FIRST_PAGE && content is HomeContent.Questions ->
                    current.copy(content = content.copy(isLoadingMore = false))
                else -> current.copy(content = HomeContent.Error(reason))
            }
        }
    }

    private companion object {
        const val KEY_UNANSWERED = "unanswered_only"
        const val FIRST_PAGE = 1
    }
}

private fun LoadFailure.toErrorReason(): ErrorReason = when (this) {
    LoadFailure.Network -> ErrorReason.Network
    LoadFailure.Server -> ErrorReason.Server
}
