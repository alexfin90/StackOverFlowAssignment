package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.TestTags
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.theme.StackOverFlowTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the stateful [HomeRoute] with a fake [HomeViewModelContract]: no Hilt, no repository.
 * This is what depending on the contract makes possible.
 */
@RunWith(RobolectricTestRunner::class)
class HomeRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeHomeViewModel(initial: HomeUiState) : HomeViewModelContract {
        override val state: MutableStateFlow<HomeUiState> = MutableStateFlow(initial)
        var toggles = 0
        var retries = 0
        var loadMores = 0

        override fun onUnansweredToggled() { toggles++ }
        override fun onRetry() { retries++ }
        override fun onLoadMore() { loadMores++ }
    }

    private val items = persistentListOf(
        QuestionItem(1, "How to test Compose?", "ann", 3, 0, 1_700_000_000),
    )

    private var clicked: Long? = null

    private fun show(viewModel: HomeViewModelContract) {
        composeRule.setContent {
            StackOverFlowTheme {
                HomeRoute(onQuestionClick = { clicked = it }, viewModel = viewModel)
            }
        }
    }

    @Test
    fun route_rendersTheStateOfTheViewModelAndForwardsClicks() {
        val vm = FakeHomeViewModel(HomeUiState(content = HomeContent.Questions(items)))
        show(vm)

        composeRule.onNodeWithText("How to test Compose?").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.questionRow(1)).performClick()
        composeRule.onNodeWithTag(TestTags.UNANSWERED_FILTER).performClick()

        assertEquals(1L, clicked)
        assertEquals(1, vm.toggles)
    }

    @Test
    fun route_followsStateChangesAndForwardsRetry() {
        val vm = FakeHomeViewModel(HomeUiState(content = HomeContent.Loading))
        show(vm)
        composeRule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()

        vm.state.value = HomeUiState(content = HomeContent.Error(ErrorReason.Network))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ERROR_RETRY).performClick()

        assertEquals(1, vm.retries)
    }
}
