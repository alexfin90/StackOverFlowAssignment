package com.alexfin90.stackoverflowhomeassignment

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

/** The destinations of the app, as far as the shared top bar is concerned. */
enum class AppDestination { Home, Detail }

/** What the shared top bar shows. The title is a destination, not a string: the UI resolves it. */
@Immutable
data class TopBarUiState(
    val destination: AppDestination = AppDestination.Home,
    val showBack: Boolean = false,
)

/**
 * What the app root needs from its ViewModel, the part of the screen common to every destination.
 * `@Stable` so the composable taking it can be skipped, and so a fake can be passed in tests.
 */
@Stable
interface MainViewModelContract {
    val topBar: StateFlow<TopBarUiState>

    /** Called by the UI each time the navigation moves to another destination. */
    fun onDestinationChanged(destination: AppDestination)
}
