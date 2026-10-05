package com.alexfin90.stackoverflowhomeassignment.feature.detail

import androidx.compose.ui.test.assertIsDisplayed
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

/** UI tests of the stateless screen; they run on the JVM with Robolectric. */
@RunWith(RobolectricTestRunner::class)
class DetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val question = QuestionDetail(
        id = 42, title = "Why does this crash?", link = "https://stackoverflow.com/q/42",
        ownerName = "ann", score = 5, answerCount = 2, createdAtEpochSeconds = 1_700_000_000,
        tags = persistentListOf("kotlin", "compose"),
    )

    private var opened: String? = null
    private var retries = 0

    private fun show(state: DetailUiState) {
        composeRule.setContent {
            StackOverFlowTheme {
                DetailScreen(
                    state = state,
                    onRetry = { retries++ },
                    onOpenInBrowser = { opened = it },
                )
            }
        }
    }

    // Happy path
    @Test
    fun content_showsDetailsAndOpensTheLink() {
        show(DetailUiState.Content(question))

        composeRule.onNodeWithText("Why does this crash?").assertIsDisplayed()
        composeRule.onNodeWithText("kotlin").assertIsDisplayed()
        composeRule.onNode(hasText("2 answers", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithTag(DetailTestTags.OPEN_IN_BROWSER).performClick()

        assertEquals("https://stackoverflow.com/q/42", opened)
    }

    @Test
    fun loading_isShown() {
        show(DetailUiState.Loading)

        composeRule.onNodeWithTag(TestTags.LOADING).assertIsDisplayed()
    }

    // Unhappy path
    @Test
    fun networkError_showsMessageAndRetryWorks() {
        show(DetailUiState.Error(DetailError.Network))

        composeRule.onNode(hasText("No connection", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ERROR_RETRY).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun notFound_isExplained() {
        show(DetailUiState.NotFound)

        composeRule.onNode(hasText("does not exist", substring = true)).assertIsDisplayed()
    }
}
