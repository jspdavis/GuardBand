package com.example.guardband.ui.profile

import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.avatar.AvatarPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Profile tab: the session fills the header immediately, then the
 * `users/{uid}` read replaces it with the stored record.
 *
 * The location field is gone, so the read now only carries the name and email.
 */
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeUserProfileRepository()

    private fun viewModel(signedIn: Boolean = true) =
        ProfileViewModel(FakeAuthRepository(signedIn = signedIn), profiles)

    /** A Google photo link with the size suffix Google normally sends. */
    private val googlePhoto = "https://lh3.googleusercontent.com/a/ACg8ocK=s96-c"

    private fun sessionWithPhoto(provider: SignInProvider) = ProfileViewModel(
        FakeAuthRepository(
            signedIn = true,
            providers = setOf(provider),
            user = FakeAuthRepository.DEFAULT_USER.copy(photoUrl = googlePhoto)
        ),
        profiles
    )

    private val storedProfile =
        User(id = "uid-1", name = "Alex Rivera", email = "alex@guardband.com")

    @Test
    fun `the header is filled from the session before the read lands`() {
        val vm = viewModel()

        // No flow collection: this is the state the screen renders immediately.
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(FakeAuthRepository.DEFAULT_USER.name, vm.uiState.value.userName)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, vm.uiState.value.email)
    }

    @Test
    fun `the stored record replaces the session values once the read lands`() = runTest {
        profiles.fetchProfileResult = Result.success(storedProfile)

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals("Alex Rivera", state.userName)
        assertEquals("alex@guardband.com", state.email)
    }

    @Test
    fun `a missing profile record leaves the session values in place`() = runTest {
        profiles.fetchProfileResult = Result.success(null)

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, state.userName)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, state.email)
    }

    @Test
    fun `a failed read leaves the session values in place rather than blanking them`() = runTest {
        profiles.fetchProfileResult = Result.failure(AuthError.Network)

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, state.userName)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, state.email)
    }

    @Test
    fun `a blank stored name falls back to the session rather than showing nothing`() = runTest {
        profiles.fetchProfileResult = Result.success(storedProfile.copy(name = "   "))

        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, state.userName)
    }

    @Test
    fun `no session means no read, and the name falls back`() = runTest {
        val vm = viewModel(signedIn = false)

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(0, profiles.fetchProfileCalls)
        assertEquals(ProfileViewModel.DEFAULT_USER_NAME, state.userName)
        assertFalse(state.isLoading)
    }

    // ── Avatar ────────────────────────────────────────────────────────────────

    @Test
    fun `a Google session's photo becomes the avatar before the read lands`() {
        val vm = sessionWithPhoto(SignInProvider.GOOGLE)

        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocK=s${AvatarPhoto.SIZE_PX}-c",
            vm.uiState.value.avatarUrl
        )
    }

    @Test
    fun `the stored record landing keeps the session's photo`() = runTest {
        profiles.fetchProfileResult = Result.success(storedProfile)

        val state = sessionWithPhoto(SignInProvider.GOOGLE).uiState.first { !it.isLoading }

        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocK=s${AvatarPhoto.SIZE_PX}-c",
            state.avatarUrl
        )
    }

    @Test
    fun `an email session keeps the default avatar even with a photo link`() {
        assertNull(sessionWithPhoto(SignInProvider.PASSWORD).uiState.value.avatarUrl)
    }

    @Test
    fun `no session keeps the default avatar`() {
        assertNull(viewModel(signedIn = false).uiState.value.avatarUrl)
    }
}
