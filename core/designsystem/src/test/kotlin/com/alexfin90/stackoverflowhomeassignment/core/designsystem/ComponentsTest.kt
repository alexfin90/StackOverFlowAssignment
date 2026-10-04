package com.alexfin90.stackoverflowhomeassignment.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.atoms.LoadingIndicator
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.atoms.UnansweredFilterChip
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.AppTopBar
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.EmptyState
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.ErrorState
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.QuestionRow
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.UiQuestionRow
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.theme.StackOverFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val row = UiQuestionRow(
        id = 7,
        title = "How do I test Compose?",
        subtitle = "jane · score 3",
        answerCountLabel = "0 answers",
        isUnanswered = true,
    )

    @Test
    fun questionRow_showsContentAndReportsClickWithId() {
        var clicked: Long? = null
        composeRule.setContent {
            StackOverFlowTheme { QuestionRow(question = row, onClick = { clicked = it }) }
        }

        composeRule.onNodeWithText("How do I test Compose?").assertIsDisplayed()
        composeRule.onNodeWithText("jane · score 3").assertIsDisplayed()
        composeRule.onNodeWithText("0 answers").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.questionRow(7)).performClick()

        assertEquals(7L, clicked)
    }

    @Test
    fun errorState_retryInvokesCallback() {
        var retries = 0
        composeRule.setContent {
            StackOverFlowTheme { ErrorState("No connection", "Retry", onRetry = { retries++ }) }
        }

        composeRule.onNodeWithText("No connection").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ERROR_RETRY).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun filterChip_togglesOnClick() {
        var toggles = 0
        composeRule.setContent {
            StackOverFlowTheme { UnansweredFilterChip("Unanswered", selected = false, onToggle = { toggles++ }) }
        }

        composeRule.onNodeWithTag(TestTags.UNANSWERED_FILTER).performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun loadingAndEmpty_areDisplayed() {
        composeRule.setContent {
            StackOverFlowTheme {
                LoadingIndicator()
                EmptyState("Nothing here")
            }
        }

        composeRule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
    }

    @Test
    fun questionRows_titlesAlignWhateverTheAnswerLabel() {
        composeRule.setContent {
            StackOverFlowTheme {
                Column {
                    QuestionRow(row.copy(id = 1, title = "First", answerCountLabel = "0 answers"), onClick = {})
                    QuestionRow(row.copy(id = 2, title = "Second", answerCountLabel = "1 answer"), onClick = {})
                    QuestionRow(row.copy(id = 3, title = "Third", answerCountLabel = "12 answers"), onClick = {})
                }
            }
        }

        val lefts = listOf("First", "Second", "Third").map {
            composeRule.onNodeWithText(it).getBoundsInRoot().left
        }

        assertEquals(1, lefts.toSet().size)
    }

    @Test
    fun appTopBar_showsTheTitleAndNoArrowOnTheStartDestination() {
        composeRule.setContent {
            StackOverFlowTheme { AppTopBar(title = "Latest questions") }
        }

        composeRule.onNodeWithText("Latest questions").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.TOP_BAR_BACK).assertDoesNotExist()
    }

    @Test
    fun appTopBar_backArrowCallsOnBack() {
        var backs = 0
        composeRule.setContent {
            StackOverFlowTheme { AppTopBar(title = "Question", onBack = { backs++ }, backLabel = "Back") }
        }

        composeRule.onNodeWithTag(TestTags.TOP_BAR_BACK).performClick()

        assertEquals(1, backs)
    }
}
