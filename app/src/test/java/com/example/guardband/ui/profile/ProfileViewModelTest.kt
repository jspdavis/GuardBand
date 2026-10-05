package com.example.guardband.ui.profile

import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Profile tab: the session fills the header, the async read fills the location. */
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeUserProfileRepository()

    private fun viewModel(signedIn: Boolean = true) =
        ProfileViewModel(FakeAuthRepository(signedIn = signedIn), profiles)

    @Test
    fun `the stored location replaces the fallback once the read lands`() = runTest {
        profiles.fetchProfileResult = Result.success(
            User(id = "uid-1", name = "Alex Rivera", email = "alex@guardband.com", location = "Cebu City")
        )

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals("Cebu City", state.location)
    }

    @Test
    fun `the header is filled from the session before the read lands`() {
        val vm = viewModel()

        // No flow collection: this is the state the screen renders immediately.
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(FakeAuthRepository.DEFAULT_USER.name, vm.uiState.value.userName)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, vm.uiState.value.email)
        assertEquals(ProfileViewModel.DEFAULT_LOCATION, vm.uiState.value.location)
    }

    @Test
    fun `a missing profile record leaves the session values in place`() = runTest {
        profiles.fetchProfileResult = Result.success(null)

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, state.userName)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, state.email)
        assertEquals(ProfileViewModel.DEFAULT_LOCATION, state.location)
    }

    @Test
    fun `a failed read leaves the session values in place rather than blanking them`() = runTest {
        profiles.fetchProfileResult = Result.failure(AuthError.Network)

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, state.userName)
        assertEquals(ProfileViewModel.DEFAULT_LOCATION, state.location)
    }

    @Test
    fun `a blank stored location falls back rather than showing nothing`() = runTest {
        profiles.fetchProfileResult = Result.success(
            User(id = "uid-1", name = "Alex Rivera", email = "alex@guardband.com", location = "   ")
        )

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals(ProfileViewModel.DEFAULT_LOCATION, state.location)
    }

    @Test
    fun `the stored name wins over the session display name`() = runTest {
        profiles.fetchProfileResult = Result.success(
            User(id = "uid-1", name = "Alex Rivera", email = "alex@guardband.com", location = "Cebu City")
        )

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals("Alex Rivera", state.userName)
    }

    @Test
    fun `no session means no read, and the name falls back`() = runTest {
        val vm = viewModel(signedIn = false)

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(0, profiles.fetchProfileCalls)
        assertEquals(ProfileViewModel.DEFAULT_USER_NAME, state.userName)
        assertFalse(state.isLoading)
    }
}
