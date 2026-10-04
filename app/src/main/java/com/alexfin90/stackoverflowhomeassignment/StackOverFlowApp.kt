package com.alexfin90.stackoverflowhomeassignment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.AppTopBar
import com.alexfin90.stackoverflowhomeassignment.navigation.AppNavHost

/**
 * Root of the UI, below the theme: `StackOverFlowTheme { StackOverFlowApp() }`.
 *
 * This is the stateful layer: it owns the [NavHostController], the state that decides the top bar.
 * It could also read any app-wide ViewModel and pass the result down to [StackOverFlowAppContent],
 * as in "App -> AppContent -> NavHost".
 */
@Composable
fun StackOverFlowApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    StackOverFlowAppContent(navController = navController, modifier = modifier)
}

/**
 * Stateless layer: the **only** `Scaffold` of the app. It owns the top bar and the window insets;
 * the screens inside the `NavHost` are plain content with no `Scaffold` of their own.
 */
@Composable
private fun StackOverFlowAppContent(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AppTopBar(title = stringResource(R.string.title_home)) },
    ) { innerPadding ->
        AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}
