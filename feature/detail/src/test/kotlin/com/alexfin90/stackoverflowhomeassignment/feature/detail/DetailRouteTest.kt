package com.alexfin90.stackoverflowhomeassignment.feature.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.TestTags
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.theme.StackOverFlowTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The stateful [DetailRoute] against a fake [DetailViewModelContract]: no Hilt, no repository. */
@RunWith(RobolectricTestRunner::class)
class DetailRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeDetailViewModel(initial: DetailUiState) : DetailViewModelContract {
        override val state: MutableStateFlow<DetailUiState> = MutableStateFlow(initial)
        var retries = 0
        override fun onRetry() { retries++ }
    }

    private val question = QuestionDetail(
        id = 42, title = "Why does this crash?", link = "https://stackoverflow.com/q/42",
        ownerName = "ann", score = 5, answerCount = 2, createdAtEpochSeconds = 1_700_000_000,
        tags = persistentListOf("kotlin"),
    )

    private fun show(viewModel: DetailViewModelContract) {
        composeRule.setContent {
            StackOverFlowTheme { DetailRoute(viewModel = viewModel) }
        }
    }

    @Test
    fun route_rendersTheStateOfTheViewModel() {
        show(FakeDetailViewModel(DetailUiState.Content(question)))

        composeRule.onNodeWithText("Why does this crash?").assertIsDisplayed()
        composeRule.onNodeWithTag(DetailTestTags.OPEN_IN_BROWSER).assertIsDisplayed()
    }

    @Test
    fun route_followsStateChangesAndForwardsRetry() {
        val vm = FakeDetailViewModel(DetailUiState.Loading)
        show(vm)
        composeRule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()

        vm.state.value = DetailUiState.Error(DetailError.Network)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ERROR_RETRY).performClick()

        assertEquals(1, vm.retries)
    }
}
