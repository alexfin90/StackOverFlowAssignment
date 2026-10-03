package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsResult
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
    private val repository: QuestionsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(unansweredOnly = savedStateHandle[KEY_UNANSWERED] ?: false),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var nextPage = FIRST_PAGE

    init {
        refresh()
    }

    fun onUnansweredToggled() {
        val unansweredOnly = !_state.value.unansweredOnly
        savedStateHandle[KEY_UNANSWERED] = unansweredOnly
        _state.update { it.copy(unansweredOnly = unansweredOnly) }
        refresh()
    }

    fun onRetry() = refresh()

    fun onLoadMore() {
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
            when (val result = repository.getLatestQuestions(unansweredOnly, page)) {
                is QuestionsResult.Success -> onSuccess(page, result)
                is QuestionsResult.NetworkError -> onError(page, ErrorReason.Network)
                is QuestionsResult.ApiError -> onError(page, ErrorReason.Server)
            }
        }
    }

    private fun onSuccess(page: Int, result: QuestionsResult.Success) {
        nextPage = page + 1
        val loaded = result.questions.map { it.toItem() }
        _state.update { current ->
            val previous = (current.content as? HomeContent.Questions)?.items.orEmpty()
            // The 30-day window slides between pages, so the same question can appear twice.
            val all = if (page == FIRST_PAGE) loaded else (previous + loaded).distinctBy { it.id }
            current.copy(
                content = if (all.isEmpty()) {
                    HomeContent.Empty
                } else {
                    HomeContent.Questions(all.toImmutableList(), canLoadMore = result.hasMore)
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
