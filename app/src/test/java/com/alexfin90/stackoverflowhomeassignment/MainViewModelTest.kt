package com.alexfin90.stackoverflowhomeassignment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainViewModelTest {

    private val viewModel = MainViewModel()

    @Test
    fun `starts on the home destination without a back arrow`() {
        val state = viewModel.topBar.value

        assertEquals(AppDestination.Home, state.destination)
        assertFalse(state.showBack)
    }

    @Test
    fun `home has no back arrow`() {
        viewModel.onDestinationChanged(AppDestination.Home)

        assertEquals(TopBarUiState(AppDestination.Home, showBack = false), viewModel.topBar.value)
    }
}
