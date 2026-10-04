package com.alexfin90.stackoverflowhomeassignment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.exposeTestTagsAsResourceId
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.AppTopBar
import com.alexfin90.stackoverflowhomeassignment.navigation.AppNavHost

/**
 * Root of the UI, below the theme: `StackOverFlowTheme { StackOverFlowApp() }`.
 *
 * The stateful layer, "App -> AppContent -> NavHost": it owns the [NavHostController], tells the
 * [MainViewModelContract] where the navigation is, and passes the resulting top bar state down.
 */
@Composable
fun StackOverFlowApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModelContract = hiltViewModel<MainViewModel>(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val topBar by viewModel.topBar.collectAsStateWithLifecycle()

    LaunchedEffect(backStackEntry) {
        viewModel.onDestinationChanged(backStackEntry?.destination.toAppDestination())
    }

    StackOverFlowAppContent(
        topBar = topBar,
        navController = navController,
        modifier = modifier,
    )
}

/**
 * Stateless layer: the **only** `Scaffold` of the app. It owns the top bar and the window insets;
 * the screens inside the `NavHost` are plain content with no `Scaffold` of their own.
 */
@Composable
private fun StackOverFlowAppContent(
    topBar: TopBarUiState,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // Test tags become resource-ids for UiAutomator/Appium, in debug builds only.
            .then(if (BuildConfig.DEBUG) Modifier.exposeTestTagsAsResourceId() else Modifier),
        topBar = {
            AppTopBar(
                title = stringResource(topBar.destination.titleRes()),
                onBack = if (topBar.showBack) ({ navController.popBackStack() }) else null,
                backLabel = stringResource(R.string.back),
            )
        },
    ) { innerPadding ->
        AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

private fun NavDestination?.toAppDestination(): AppDestination = when {
    this?.hasRoute<Route.QuestionDetail>() == true -> AppDestination.Detail
    else -> AppDestination.Home
}

private fun AppDestination.titleRes(): Int = when (this) {
    AppDestination.Home -> R.string.title_home
    AppDestination.Detail -> R.string.title_detail
}
