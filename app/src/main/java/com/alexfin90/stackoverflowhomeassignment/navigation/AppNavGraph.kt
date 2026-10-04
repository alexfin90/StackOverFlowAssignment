package com.alexfin90.stackoverflowhomeassignment.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.feature.home.HomeRoute

/**
 * The destinations of the app. It is a plain extension on [NavGraphBuilder], not a composable, so no
 * composable takes a [NavHostController] as a (compiler-unstable) parameter: the controller is
 * created and held by `StackOverFlowAppContent`, which also hosts the `NavHost`.
 */
fun NavGraphBuilder.appNavGraph(navController: NavHostController) {
    composable<Route.QuestionsList> {
        // The detail destination is added in the next PR.
        HomeRoute(onQuestionClick = {})
    }
}
