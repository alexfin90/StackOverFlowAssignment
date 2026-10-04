package com.alexfin90.stackoverflowhomeassignment.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.feature.home.HomeRoute

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Route.QuestionsList,
        modifier = modifier,
    ) {
        composable<Route.QuestionsList> {
            // The detail destination is added in the next PR.
            HomeRoute(onQuestionClick = {})
        }
    }
}
