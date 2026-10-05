package com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules

import androidx.compose.runtime.Immutable

/**
 * Ready-to-render model of a question row. Strings are already formatted by the feature
 * module, so the design system has no knowledge of the SDK or of localisation.
 */
@Immutable
data class UiQuestionRow(
    val id: Long,
    val title: String,
    val subtitle: String,
    val answerCountLabel: String,
    val isUnanswered: Boolean,
)
