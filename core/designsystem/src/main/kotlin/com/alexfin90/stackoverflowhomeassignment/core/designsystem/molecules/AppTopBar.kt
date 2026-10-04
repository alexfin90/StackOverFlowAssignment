package com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.TestTags

/**
 * The top bar of the whole app, used by the single app-level `Scaffold`.
 *
 * @param onBack when not `null` a back arrow is shown and calls it; `null` on the start destination.
 * @param backLabel accessibility label of the arrow, provided by the caller (this module has no strings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.testTag(TestTags.TOP_BAR_BACK)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backLabel,
                    )
                }
            }
        },
    )
}
