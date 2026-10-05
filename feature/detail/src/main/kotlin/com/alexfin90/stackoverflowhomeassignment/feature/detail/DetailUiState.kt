package com.alexfin90.stackoverflowhomeassignment.feature.detail

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
sealed interface DetailUiState {
    data object Loading : DetailUiState

    data class Error(val reason: DetailError) : DetailUiState

    data object NotFound : DetailUiState

    data class Content(val question: QuestionDetail) : DetailUiState
}

enum class DetailError { Network, Server }

@Immutable
data class QuestionDetail(
    val id: Long,
    val title: String,
    val link: String,
    val ownerName: String?,
    val score: Int,
    val answerCount: Int,
    val createdAtEpochSeconds: Long,
    val tags: ImmutableList<String>,
)
