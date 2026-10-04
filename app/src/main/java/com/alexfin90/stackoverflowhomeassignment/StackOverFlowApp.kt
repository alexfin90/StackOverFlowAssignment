package com.alexfin90.stackoverflowhomeassignment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.AppTopBar
import com.alexfin90.stackoverflowhomeassignment.navigation.appNavGraph

/**
 * Root of the UI, below the theme: `StackOverFlowTheme { StackOverFlowApp() }`.
 *
 * The stateful layer, "App -> AppContent -> NavHost": it reads the [MainViewModelContract], the
 * state common to every destination, and passes it down.
 */
@Composable
fun StackOverFlowApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModelContract = hiltViewModel<MainViewModel>(),
) {
    val topBar by viewModel.topBar.collectAsStateWithLifecycle()
    StackOverFlowAppContent(
        topBar = topBar,
        onDestinationChanged = viewModel::onDestinationChanged,
        modifier = modifier,
    )
}

/**
 * The **only** `Scaffold` of the app: it owns the top bar and the window insets, and the `NavHost`
 * is in its content. The screens inside have no `Scaffold` of their own.
 *
 * The [androidx.navigation.NavHostController] is created here, not received as a parameter: the
 * class is not annotated as stable, so a parameter of that type would be reported as unstable.
 */
@Composable
private fun StackOverFlowAppContent(
    topBar: TopBarUiState,
    onDestinationChanged: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentOnDestinationChanged by rememberUpdatedState(onDestinationChanged)

    LaunchedEffect(backStackEntry) {
        currentOnDestinationChanged(backStackEntry?.destination.toAppDestination())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(topBar.destination.titleRes()),
                onBack = if (topBar.showBack) ({ navController.popBackStack() }) else null,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.QuestionsList,
            modifier = Modifier.padding(innerPadding),
        ) {
            appNavGraph(navController)
        }
    }
}

private fun NavDestination?.toAppDestination(): AppDestination = when {
    this?.hasRoute<Route.QuestionsList>() == true -> AppDestination.Home
    else -> AppDestination.Home
}

private fun AppDestination.titleRes(): Int = when (this) {
    AppDestination.Home -> R.string.title_home
}
