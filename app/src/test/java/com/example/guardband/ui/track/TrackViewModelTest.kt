package com.example.guardband.ui.track

import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Track tab: the user chip's name, and the placeholder click handlers.
 *
 * The chip used to carry the user's typed city from `users/{uid}`; that field
 * is gone, so the tab no longer reads the profile record at all and takes only
 * an [com.example.guardband.data.repository.AuthRepository].
 */
class TrackViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(signedIn: Boolean = true) =
        TrackViewModel(FakeAuthRepository(signedIn = signedIn))

    @Test
    fun `the chip shows the session's name`() {
        val vm = viewModel()

        // No flow collection: this is the state the screen renders immediately.
        assertEquals(FakeAuthRepository.DEFAULT_USER.name, vm.uiState.value.userName)
    }

    @Test
    fun `no session falls back to the default name`() {
        val vm = viewModel(signedIn = false)

        assertEquals(TrackViewModel.DEFAULT_USER_NAME, vm.uiState.value.userName)
    }

    @Test
    fun `check-in explains that the band sends it, rather than sending one`() = runTest {
        val vm = viewModel()

        vm.onCheckInClicked()

        assertEquals(
            TrackViewModel.MSG_CHECK_IN_ON_BAND,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
    }

    @Test
    fun `the map controls say the map is not available yet`() = runTest {
        val vm = viewModel()

        vm.onRecenterClicked()

        assertEquals(
            TrackViewModel.MSG_MAP_UNAVAILABLE,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
    }

    @Test
    fun `the layers control says the same`() = runTest {
        val vm = viewModel()

        vm.onLayersClicked()

        assertEquals(
            TrackViewModel.MSG_MAP_UNAVAILABLE,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
    }
}
