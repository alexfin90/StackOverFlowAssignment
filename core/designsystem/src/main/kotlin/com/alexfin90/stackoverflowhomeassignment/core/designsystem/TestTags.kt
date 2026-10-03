package com.alexfin90.stackoverflowhomeassignment.core.designsystem

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Shared test tags, so UI tests in any module (and QA automation) can find design system components.
 *
 * Conventions: lowercase `snake_case`, stable (renaming a tag breaks the tests that use it) and
 * independent of the displayed text, which changes with the language. Dynamic parts are an id,
 * built by a function (see [questionRow]).
 */
object TestTags {
    const val LOADING = "loading"
    const val ERROR = "error"
    const val ERROR_RETRY = "error_retry"
    const val EMPTY = "empty"
    const val UNANSWERED_FILTER = "unanswered_filter"
    const val QUESTIONS_LIST = "questions_list"
    fun questionRow(id: Long) = "question_row_$id"
}

/**
 * Makes every `testTag` below this node visible to UiAutomator, and therefore to Appium, as the
 * `resource-id` of the element. Without it Compose tags are invisible outside Compose tests.
 *
 * Put it once on the root of the UI, and only on builds used for testing: it exposes internal names.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.exposeTestTagsAsResourceId(): Modifier = semantics { testTagsAsResourceId = true }
