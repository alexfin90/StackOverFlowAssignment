package com.alexfin90.stackoverflowhomeassignment.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.core.domain.GetQuestionUseCase
import com.alexfin90.stackoverflowhomeassignment.core.domain.LoadFailure
import com.alexfin90.stackoverflowhomeassignment.core.domain.QuestionOutcome
import com.alexfin90.stackoverflowsdk.domain.Question
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val getQuestion: GetQuestionUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), DetailViewModelContract {

    // Same argument whether we arrive from the list or from a deep link, and it is restored
    // after process death.
    private val questionId: Long = savedStateHandle.toRoute<Route.QuestionDetail>().questionId

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    override val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    override fun onRetry() = load()

    private fun load() {
        _state.value = DetailUiState.Loading
        viewModelScope.launch {
            _state.value = when (val outcome = getQuestion(questionId)) {
                is QuestionOutcome.Found -> DetailUiState.Content(outcome.question.toDetail())
                QuestionOutcome.NotFound -> DetailUiState.NotFound
                is QuestionOutcome.Failure -> DetailUiState.Error(outcome.reason.toDetailError())
            }
        }
    }
}

private fun LoadFailure.toDetailError(): DetailError = when (this) {
    LoadFailure.Network -> DetailError.Network
    LoadFailure.Server -> DetailError.Server
}

private fun Question.toDetail() = QuestionDetail(
    id = id,
    title = title,
    link = link,
    ownerName = ownerName,
    score = score,
    answerCount = answerCount,
    createdAtEpochSeconds = creationDateEpochSeconds,
    tags = tags.toImmutableList(),
)
