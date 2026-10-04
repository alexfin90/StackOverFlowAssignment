package com.alexfin90.stackoverflowhomeassignment.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.DeepLinks
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.feature.detail.DetailRoute
import com.alexfin90.stackoverflowhomeassignment.feature.home.HomeRoute

/**
 * The destinations of the app. It is a plain extension on [NavGraphBuilder], not a composable, so no
 * composable takes a [NavHostController] as a (compiler-unstable) parameter: the controller is
 * created and held by `StackOverFlowAppContent`, which also hosts the `NavHost`.
 */
fun NavGraphBuilder.appNavGraph(navController: NavHostController) {
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
