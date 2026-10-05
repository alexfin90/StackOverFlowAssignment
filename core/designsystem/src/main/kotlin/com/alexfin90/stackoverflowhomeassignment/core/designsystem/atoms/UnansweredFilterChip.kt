package com.alexfin90.stackoverflowhomeassignment.core.designsystem.atoms

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.TestTags

/** Toggle chip; the label is provided by the caller so this module stays free of strings. */
@Composable
fun UnansweredFilterChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(label) },
        modifier = modifier.testTag(TestTags.UNANSWERED_FILTER),
    )
}
