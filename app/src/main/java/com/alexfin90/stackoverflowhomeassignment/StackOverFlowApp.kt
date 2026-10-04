package com.alexfin90.stackoverflowhomeassignment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexfin90.stackoverflowhomeassignment.navigation.AppNavHost

/**
 * Root of the UI, below the theme: `StackOverFlowTheme { StackOverFlowApp() }`.
 *
 * It is the single place for anything that must wrap the whole app (today only the navigation;
 * a top bar, a snackbar host or a global modifier would go here).
 */
@Composable
fun StackOverFlowApp(modifier: Modifier = Modifier) {
    AppNavHost(modifier = modifier.fillMaxSize())
}
