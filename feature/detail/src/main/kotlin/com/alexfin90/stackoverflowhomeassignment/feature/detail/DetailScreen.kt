package com.alexfin90.stackoverflowhomeassignment.feature.detail

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.TestTags
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.atoms.LoadingIndicator
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.ErrorState

object DetailTestTags {
    const val BACK = "detail_back"
    const val OPEN_IN_BROWSER = "detail_open_in_browser"
    const val CONTENT = "detail_content"
}

/** Stateful entry point: ViewModel + opening the link in the browser. */
@Composable
fun DetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    DetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onOpenInBrowser = uriHandler::openUri,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(DetailTestTags.BACK)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                DetailUiState.Loading -> LoadingIndicator()
                is DetailUiState.Error -> ErrorState(
                    message = stringResource(
                        if (state.reason == DetailError.Network) {
                            R.string.detail_error_network
                        } else {
                            R.string.detail_error_server
                        },
                    ),
                    retryLabel = stringResource(R.string.detail_retry),
                    onRetry = onRetry,
                )
                DetailUiState.NotFound -> ErrorState(
                    message = stringResource(R.string.detail_not_found),
                    retryLabel = stringResource(R.string.detail_retry),
                    onRetry = onRetry,
                )
                is DetailUiState.Content -> QuestionContent(state.question, onOpenInBrowser)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionContent(question: QuestionDetail, onOpenInBrowser: (String) -> Unit) {
    val owner = question.ownerName ?: stringResource(R.string.detail_unknown_author)
    val time = remember(question.createdAtEpochSeconds) {
        DateUtils.getRelativeTimeSpanString(
            question.createdAtEpochSeconds * 1000,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(DetailTestTags.CONTENT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = question.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.detail_meta, owner, time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(R.plurals.detail_answers, question.answerCount, question.answerCount) +
                " · " + stringResource(R.string.detail_score, question.score),
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            question.tags.forEach { tag -> TagChip(tag) }
        }
        Button(
            onClick = { onOpenInBrowser(question.link) },
            modifier = Modifier.fillMaxWidth().testTag(DetailTestTags.OPEN_IN_BROWSER),
        ) {
            Text(stringResource(R.string.detail_open_in_browser))
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
