package com.alexfin90.stackoverflowhomeassignment

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexfin90.stackoverflowhomeassignment.navigation.AppNavHost

/**
 * Root of the UI, below the theme: `StackOverFlowTheme { StackOverFlowApp() }`.
 *
 * This is the stateful layer: anything global (a ViewModel with app-wide state, such as a
 * connectivity banner) would be read here and passed down to [StackOverFlowAppContent], as in
 * "App -> AppContent -> NavHost". There is no global state yet, so it only forwards.
 */
@Composable
fun StackOverFlowApp(modifier: Modifier = Modifier) {
    StackOverFlowAppContent(modifier = modifier)
}

/** Stateless layer: the app scaffold and the navigation graph. */
@Composable
private fun StackOverFlowAppContent(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Each screen has its own Scaffold and top bar and already handles the system bars:
        // applying the insets here too would add the padding twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        AppNavHost(modifier = Modifier.padding(innerPadding))
    }
}
