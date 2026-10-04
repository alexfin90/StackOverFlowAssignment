package com.alexfin90.stackoverflowhomeassignment.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.DeepLinks
import com.alexfin90.stackoverflowhomeassignment.core.common.navigation.Route
import com.alexfin90.stackoverflowhomeassignment.feature.detail.DetailRoute
import com.alexfin90.stackoverflowhomeassignment.feature.home.HomeRoute

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Route.QuestionsList,
        modifier = modifier,
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
            DetailRoute()
        }
    }
}
