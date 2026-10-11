package com.example.guardband.ui.login

import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.auth.GoogleIdTokenResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Google half of [LoginViewModel], kept apart from [LoginViewModelTest] so
 * the password tests stay readable.
 *
 * Robolectric for the same reason as its sibling: the shared
 * [com.example.guardband.utils.InputValidator] reaches `android.util.Patterns`.
 */
@RunWith(RobolectricTestRunner::class)
class LoginGoogleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val viewModel = LoginViewModel(auth)

    @Test
    fun `tapping the button asks the Activity for a token and shows progress`() = runTest {
        viewModel.onGoogleSignInClicked()

        assertEquals(LoginEvent.RequestGoogleIdToken, viewModel.events.first())
        assertTrue(viewModel.uiState.value.isGoogleLoading)
        assertTrue(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `a returning user goes straight to Home`() = runTest {
        auth.signInWithGoogleResult = Result.success(FakeAuthRepository.RETURNING_GOOGLE_USER)

        viewModel.onGoogleIdToken("id-token")

        assertEquals(LoginEvent.NavigateToLoadingHome, viewModel.events.first())
        assertEquals(1, auth.signInWithGoogleCalls)
        assertFalse(viewModel.uiState.value.isGoogleLoading)
    }

    @Test
    fun `a first-time user goes to complete-profile with name and email prefilled`() = runTest {
        auth.signInWithGoogleResult = Result.success(FakeAuthRepository.NEW_GOOGLE_USER)

        viewModel.onGoogleIdToken("id-token")

        val event = viewModel.events.first()
        assertEquals(
            LoginEvent.NavigateToCompleteProfile(
                name = FakeAuthRepository.DEFAULT_USER.name,
                email = FakeAuthRepository.DEFAULT_USER.email
            ),
            event
        )
    }

    @Test
    fun `the token is forwarded to the repository unchanged`() = runTest {
        viewModel.onGoogleIdToken("the-exact-token")

        viewModel.events.first()
        assertEquals("the-exact-token", auth.lastGoogleIdToken)
    }

    @Test
    fun `a cancelled chooser says nothing at all`() = runTest {
        viewModel.onGoogleSignInClicked()
        assertEquals(LoginEvent.RequestGoogleIdToken, viewModel.events.first())

        viewModel.onGoogleError(GoogleIdTokenResult.Cancelled)

        // No event within the window, and the button is usable again.
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() })
        assertFalse(viewModel.uiState.value.isGoogleLoading)
    }

    @Test
    fun `a provider collision shows the neutral message`() = runTest {
        auth.signInWithGoogleResult =
            Result.failure(AuthError.AccountExistsWithDifferentCredential)

        viewModel.onGoogleIdToken("id-token")

        assertEquals(LoginViewModel.MSG_DIFFERENT_SIGN_IN_METHOD, firstMessage())
    }

    @Test
    fun `the collision message never names the other provider`() = runTest {
        val message = LoginViewModel.MSG_DIFFERENT_SIGN_IN_METHOD.lowercase()

        assertFalse(message.contains("google"))
        assertFalse(message.contains("password"))
    }

    @Test
    fun `a missing Google account and an unavailable provider read differently`() = runTest {
        viewModel.onGoogleError(GoogleIdTokenResult.NoGoogleAccount)
        assertEquals(LoginViewModel.MSG_NO_GOOGLE_ACCOUNT, firstMessage())

        viewModel.onGoogleError(GoogleIdTokenResult.Unavailable)
        assertEquals(LoginViewModel.MSG_GOOGLE_UNAVAILABLE, firstMessage())
    }

    @Test
    fun `a network failure from the chooser reuses the connection message`() = runTest {
        viewModel.onGoogleError(GoogleIdTokenResult.Network)

        assertEquals(LoginViewModel.MSG_NO_CONNECTION, firstMessage())
    }

    @Test
    fun `an unmapped repository failure falls back to the generic Google message`() = runTest {
        auth.signInWithGoogleResult = Result.failure(AuthError.Unknown("ERROR_SOMETHING_NEW"))

        viewModel.onGoogleIdToken("id-token")

        assertEquals(LoginViewModel.MSG_GOOGLE_FAILED, firstMessage())
    }

    @Test
    fun `a second tap while the chooser is open is ignored`() = runTest {
        viewModel.onGoogleSignInClicked()
        viewModel.onGoogleSignInClicked()

        assertEquals(LoginEvent.RequestGoogleIdToken, viewModel.events.first())
        assertNull(withTimeoutOrNull(100) { viewModel.events.first() })
    }

    @Test
    fun `password login is blocked while Google sign-in is in flight`() = runTest {
        viewModel.onGoogleSignInClicked()
        assertEquals(LoginEvent.RequestGoogleIdToken, viewModel.events.first())

        viewModel.onLoginClicked("alex@guardband.com", "password123")

        assertEquals(0, auth.loginCalls)
    }

    private suspend fun firstMessage(): String =
        (viewModel.events.first() as LoginEvent.ShowMessage).text
}
