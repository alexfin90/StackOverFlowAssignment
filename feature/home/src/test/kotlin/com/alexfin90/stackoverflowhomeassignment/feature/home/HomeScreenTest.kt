package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.TestTags
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.theme.StackOverFlowTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * UI tests of the stateless screen. They run on the JVM with Robolectric, so they need no
 * emulator and run in CI like unit tests.
 */
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = persistentListOf(
        QuestionItem(1, "How to test Compose?", "ann", 3, 0, 1_700_000_000),
        QuestionItem(2, "What is a ViewModel?", null, 5, 2, 1_700_000_000),
    )

    private var clicked: Long? = null
    private var retries = 0
    private var toggles = 0

    private fun show(state: HomeUiState) {
        composeRule.setContent {
            StackOverFlowTheme {
                HomeScreen(
                    state = state,
                    onQuestionClick = { clicked = it },
                    onUnansweredToggled = { toggles++ },
                    onRetry = { retries++ },
                    onLoadMore = {},
                )
            }
        }
    }

    // Happy path
    @Test
    fun questions_areListedAndClickable() {
        show(HomeUiState(content = HomeContent.Questions(items)))

        composeRule.onNodeWithText("How to test Compose?").assertIsDisplayed()
        composeRule.onNodeWithText("What is a ViewModel?").assertIsDisplayed()
        composeRule.onNodeWithText("0 answers").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.questionRow(2)).performClick()

        assertEquals(2L, clicked)
    }

    @Test
    fun unansweredChip_reflectsStateAndNotifiesToggle() {
        show(HomeUiState(unansweredOnly = true, content = HomeContent.Questions(items)))

        composeRule.onNodeWithTag(TestTags.UNANSWERED_FILTER).assertIsSelected()
        composeRule.onNodeWithTag(TestTags.UNANSWERED_FILTER).performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun loading_isShown() {
        show(HomeUiState(content = HomeContent.Loading))

        composeRule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun empty_isShown() {
        show(HomeUiState(content = HomeContent.Empty))

        composeRule.onNode(hasText("No questions found", substring = true)).assertIsDisplayed()
    }

    // Unhappy path
    @Test
    fun networkError_showsMessageAndRetryWorks() {
        show(HomeUiState(content = HomeContent.Error(ErrorReason.Network)))

        composeRule.onNode(hasText("No connection", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ERROR_RETRY).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun serverError_showsServerMessage() {
        show(HomeUiState(content = HomeContent.Error(ErrorReason.Server)))

        composeRule.onNode(hasText("not responding", substring = true)).assertIsDisplayed()
    }
}
