package com.alexfin90.stackoverflowhomeassignment.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.TestTags
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.atoms.LoadingIndicator
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.atoms.UnansweredFilterChip
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.EmptyState
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.ErrorState
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.QuestionRow
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.UiQuestionRow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Stateful entry point: connects the ViewModel to the stateless [HomeScreen]. */
@Composable
fun HomeRoute(
    onQuestionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModelContract = hiltViewModel<HomeViewModel>(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onQuestionClick = onQuestionClick,
        onUnansweredToggled = viewModel::onUnansweredToggled,
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        modifier = modifier,
    )
}

/**
 * The content of the home destination. The app-level `Scaffold` owns the top bar and the insets, so
 * this screen has neither: it only lays out the filter and the content below it.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onQuestionClick: (Long) -> Unit,
    onUnansweredToggled: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            UnansweredFilterChip(
                label = stringResource(R.string.home_filter_unanswered),
                selected = state.unansweredOnly,
                onToggle = onUnansweredToggled,
            )
        }
        when (val content = state.content) {
            HomeContent.Loading -> LoadingIndicator()
            is HomeContent.Error -> ErrorState(
                message = stringResource(
                    if (content.reason == ErrorReason.Network) {
                        R.string.home_error_network
                    } else {
                        R.string.home_error_server
                    },
                ),
                retryLabel = stringResource(R.string.home_retry),
                onRetry = onRetry,
            )
            HomeContent.Empty -> EmptyState(stringResource(R.string.home_empty))
            is HomeContent.Questions -> QuestionsList(
                content = content,
                onQuestionClick = onQuestionClick,
                onLoadMore = onLoadMore,
            )
        }
    }
}

@Composable
private fun QuestionsList(
    content: HomeContent.Questions,
    onQuestionClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(TestTags.QUESTIONS_LIST),
    ) {
        items(
            items = content.items,
            key = { it.id },
            contentType = { ROW_CONTENT_TYPE },
        ) { item ->
            QuestionRow(question = rememberUiRow(item), onClick = onQuestionClick)
            HorizontalDivider()
        }
        if (content.isLoadingMore) {
            item(key = "loading_more", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** Calls [onLoadMore] when the user gets within a few items of the end of the list. */
@Composable
private fun LoadMoreEffect(listState: LazyListState, onLoadMore: () -> Unit) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }
}

/** Formats a [QuestionItem] with string resources; remembered so it is not rebuilt on every recomposition. */
@Composable
private fun rememberUiRow(item: QuestionItem): UiQuestionRow {
    val answers = pluralStringResource(R.plurals.home_answers, item.answerCount, item.answerCount)
    val owner = item.ownerName ?: stringResource(R.string.home_unknown_author)
    val time = remember(item.createdAtEpochSeconds) {
        DateUtils.getRelativeTimeSpanString(
            item.createdAtEpochSeconds * 1000,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    val subtitle = stringResource(R.string.home_subtitle, owner, item.score, time)
    return remember(item, answers, subtitle) {
        UiQuestionRow(
            id = item.id,
            title = item.title,
            subtitle = subtitle,
            answerCountLabel = answers,
            isUnanswered = item.answerCount == 0,
        )
    }
}

private const val ROW_CONTENT_TYPE = "question"
private const val LOAD_MORE_THRESHOLD = 3
