package com.alexfin90.stackoverflowhomeassignment

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Owns the state of the shared top bar, so the root composable only renders it. */
@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel(), MainViewModelContract {

    private val _topBar = MutableStateFlow(TopBarUiState())
    override val topBar: StateFlow<TopBarUiState> = _topBar.asStateFlow()

    override fun onDestinationChanged(destination: AppDestination) {
        _topBar.value = when (destination) {
            // The start destination has no back arrow.
            AppDestination.Home -> TopBarUiState(destination = AppDestination.Home, showBack = false)
            AppDestination.Detail -> TopBarUiState(destination = AppDestination.Detail, showBack = true)
        }
    }
}
