package com.alexfin90.stackoverflowhomeassignment

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.DeepLinks
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.exposeTestTagsAsResourceId
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.molecules.AppTopBar
import com.alexfin90.stackoverflowhomeassignment.feature.detail.DetailRoute
import com.alexfin90.stackoverflowhomeassignment.feature.home.HomeRoute

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
 */
@Composable
private fun StackOverFlowAppContent(
    topBar: TopBarUiState,
    onDestinationChanged: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The back arrow asks the system for "back": the NavHost below already handles it, so the
    // Scaffold does not need the navigation controller.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // Test tags become resource-ids for UiAutomator/Appium, in debug builds only.
            .then(if (BuildConfig.DEBUG) Modifier.exposeTestTagsAsResourceId() else Modifier),
        topBar = {
            AppTopBar(
                title = stringResource(topBar.destination.titleRes()),
                onBack = if (topBar.showBack) ({ backDispatcher?.onBackPressed() }) else null,
                backLabel = stringResource(R.string.back),
            )
        },
    ) { innerPadding ->
        StackOverFlowNavHost(
            onDestinationChanged = onDestinationChanged,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * The navigation graph. It creates its own `NavHostController` (no unstable parameter) and only
 * tells the outside world where the navigation is.
 */
@Composable
private fun StackOverFlowNavHost(
    onDestinationChanged: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentOnDestinationChanged by rememberUpdatedState(onDestinationChanged)

    LaunchedEffect(backStackEntry) {
        currentOnDestinationChanged(backStackEntry?.destination.toAppDestination())
    }

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Route.QuestionsList,
    ) {
        composable<Route.QuestionsList> {
            HomeRoute(
                onQuestionClick = { id -> navController.navigate(Route.QuestionDetail(id)) },
            )
        }
        composable<Route.QuestionDetail>(
            deepLinks = listOf(
                // stackoverflowdemo://question/{questionId}
                navDeepLink<Route.QuestionDetail>(basePath = DeepLinks.QUESTION_DETAIL_BASE),
            ),
        ) {
            // The back arrow is in the shared top bar of the single Scaffold.
            DetailRoute()
        }
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
