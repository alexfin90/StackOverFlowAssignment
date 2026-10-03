package com.alexfin90.stackoverflowhomeassignment.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.feature.home.HomeRoute

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
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
