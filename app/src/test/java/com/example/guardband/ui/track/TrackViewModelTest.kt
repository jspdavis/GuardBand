package com.example.guardband.ui.track

import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Track tab: the user chip's location, and the placeholder click handlers. */
class TrackViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeUserProfileRepository()

    private fun viewModel(signedIn: Boolean = true) =
        TrackViewModel(FakeAuthRepository(signedIn = signedIn), profiles)

    private val storedProfile = User(
        id = "uid-1",
        name = "Alex Rivera",
        email = "alex@guardband.com",
        location = "Cebu City"
    )

    @Test
    fun `the chip shows the stored location once the read lands`() = runTest {
        profiles.fetchProfileResult = Result.success(storedProfile)

        val state = viewModel().uiState.first { it.userLocation != TrackViewModel.DEFAULT_LOCATION }

        assertEquals("Cebu City", state.userLocation)
    }

    @Test
    fun `the chip starts on the fallback, because the session cannot supply a location`() {
        val vm = viewModel()

        assertEquals(TrackViewModel.DEFAULT_LOCATION, vm.uiState.value.userLocation)
        assertEquals(FakeAuthRepository.DEFAULT_USER.name, vm.uiState.value.userName)
    }

    @Test
    fun `a missing record leaves the fallback in place`() = runTest {
        profiles.fetchProfileResult = Result.success(null)
        val vm = viewModel()

        profiles.let { } // read has already been launched by init
        assertEquals(TrackViewModel.DEFAULT_LOCATION, vm.uiState.first().userLocation)
    }

    @Test
    fun `a failed read leaves the fallback in place rather than reporting an error`() = runTest {
        profiles.fetchProfileResult = Result.failure(AuthError.Network)
        val vm = viewModel()

        assertEquals(TrackViewModel.DEFAULT_LOCATION, vm.uiState.first().userLocation)
    }

    @Test
    fun `a blank stored location leaves the fallback in place`() = runTest {
        profiles.fetchProfileResult = Result.success(storedProfile.copy(location = "   "))
        val vm = viewModel()

        assertEquals(TrackViewModel.DEFAULT_LOCATION, vm.uiState.first().userLocation)
    }

    @Test
    fun `no session means no read`() = runTest {
        viewModel(signedIn = false)

        assertEquals(0, profiles.fetchProfileCalls)
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
}
