package com.alexfin90.stackoverflowhomeassignment.core.designsystem

/** Shared test tags, so UI tests in any module can find design system components. */
object TestTags {
    const val LOADING = "loading"
    const val ERROR = "error"
    const val ERROR_RETRY = "error_retry"
    const val EMPTY = "empty"
    const val UNANSWERED_FILTER = "unanswered_filter"
    const val QUESTIONS_LIST = "questions_list"
    const val TOP_BAR_BACK = "top_bar_back"
    fun questionRow(id: Long) = "question_row_$id"
}
