package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.compose.runtime.Immutable
import com.alexfin90.stackoverflowhomeassignment.core.domain.LoadFailure
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Everything the screen needs, as one immutable value. `ImmutableList` + `@Immutable` let the
 * Compose compiler skip recomposition of unchanged parts.
 */
@Immutable
data class HomeUiState(
    val unansweredOnly: Boolean = false,
    val content: HomeContent = HomeContent.Loading,
)

@Immutable
sealed interface HomeContent {
    data object Loading : HomeContent

    data class Error(val reason: LoadFailure) : HomeContent

    data object Empty : HomeContent

    data class Questions(
        val items: ImmutableList<QuestionItem> = persistentListOf(),
        val canLoadMore: Boolean = false,
        val isLoadingMore: Boolean = false,
    ) : HomeContent
}

/** Presentation model: raw values, formatting with resources is done in the UI layer. */
@Immutable
data class QuestionItem(
    val id: Long,
    val title: String,
    val ownerName: String?,
    val score: Int,
    val answerCount: Int,
    val createdAtEpochSeconds: Long,
)
