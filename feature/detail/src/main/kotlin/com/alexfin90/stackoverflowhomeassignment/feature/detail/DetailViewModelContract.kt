package com.alexfin90.stackoverflowhomeassignment.feature.detail

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

/**
 * What the detail screen needs from its ViewModel. The screen depends on this `@Stable` interface
 * and not on the concrete `DetailViewModel` (which the Compose compiler reports as unstable because
 * of its mutable properties); previews and UI tests can pass a fake without Hilt.
 */
@Stable
interface DetailViewModelContract {
    val state: StateFlow<DetailUiState>

    fun onRetry()
}
