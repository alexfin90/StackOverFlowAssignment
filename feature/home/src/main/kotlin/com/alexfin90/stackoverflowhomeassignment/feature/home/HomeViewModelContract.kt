package com.alexfin90.stackoverflowhomeassignment.feature.home

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

/**
 * What the home screen needs from its ViewModel. The screen depends on this interface, not on the
 * concrete `HomeViewModel`:
 *
 * - `@Stable` tells the Compose compiler the type is safe to skip on recomposition (a concrete
 *   ViewModel has mutable properties, so the compiler reports it as unstable);
 * - previews and UI tests can pass a simple fake without Hilt or a repository.
 */
@Stable
interface HomeViewModelContract {
    val state: StateFlow<HomeUiState>

    fun onUnansweredToggled()

    fun onRetry()

    fun onLoadMore()
}
