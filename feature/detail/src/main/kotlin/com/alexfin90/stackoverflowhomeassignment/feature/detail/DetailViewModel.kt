package com.alexfin90.stackoverflowhomeassignment.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowsdk.domain.QuestionResult
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: QuestionsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Same argument whether we arrive from the list or from a deep link, and it is restored
    // after process death.
    private val questionId: Long = savedStateHandle.toRoute<Route.QuestionDetail>().questionId

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        _state.value = DetailUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.getQuestion(questionId)) {
                is QuestionResult.Success -> DetailUiState.Content(result.question.toDetail())
                QuestionResult.NotFound -> DetailUiState.NotFound
                is QuestionResult.NetworkError -> DetailUiState.Error(DetailError.Network)
                is QuestionResult.ApiError -> DetailUiState.Error(DetailError.Server)
            }
        }
    }
}

private fun com.alexfin90.stackoverflowsdk.domain.Question.toDetail() = QuestionDetail(
    id = id,
    title = title,
    link = link,
    ownerName = ownerName,
    score = score,
    answerCount = answerCount,
    createdAtEpochSeconds = creationDateEpochSeconds,
    tags = tags.toImmutableList(),
)
