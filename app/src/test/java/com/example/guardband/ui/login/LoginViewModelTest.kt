package com.example.guardband.ui.login

import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric, not plain JUnit: validation goes through
 * [com.example.guardband.utils.InputValidator], which uses
 * `android.util.Patterns`.
 */
@RunWith(RobolectricTestRunner::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val viewModel = LoginViewModel(auth)

    @Test
    fun `a blank email is rejected before the repository is called`() = runTest {
        viewModel.onLoginClicked("", "password123")

        assertEquals(LoginViewModel.MSG_EMAIL_REQUIRED, firstMessage())
        assertEquals(0, auth.loginCalls)
    }

    @Test
    fun `a malformed email is rejected before the repository is called`() = runTest {
        viewModel.onLoginClicked("not-an-email", "password123")

        assertEquals(LoginViewModel.MSG_EMAIL_INVALID, firstMessage())
        assertEquals(0, auth.loginCalls)
    }

    @Test
    fun `a blank password is rejected before the repository is called`() = runTest {
        viewModel.onLoginClicked("alex@guardband.com", "")

        assertEquals(LoginViewModel.MSG_PASSWORD_REQUIRED, firstMessage())
        assertEquals(0, auth.loginCalls)
    }

    @Test
    fun `a successful login navigates on and clears the loading flag`() = runTest {
        viewModel.onLoginClicked("alex@guardband.com", "password123")

        assertEquals(LoginEvent.NavigateToLoadingHome, viewModel.events.first())
        assertEquals(1, auth.loginCalls)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `wrong credentials and an unknown account read the same`() = runTest {
        auth.loginResult = Result.failure(AuthError.InvalidCredentials)
        viewModel.onLoginClicked("alex@guardband.com", "wrong-password")
        val wrongPassword = firstMessage()

        val other = LoginViewModel(
            FakeAuthRepository(loginResult = Result.failure(AuthError.NoSuchUser))
        )
        other.onLoginClicked("nobody@guardband.com", "password123")
        val unknownAccount = (other.events.first() as LoginEvent.ShowMessage).text

        assertEquals(LoginViewModel.MSG_INVALID_CREDENTIALS, wrongPassword)
        assertEquals(wrongPassword, unknownAccount)
    }

    @Test
    fun `a network failure gets its own message`() = runTest {
        auth.loginResult = Result.failure(AuthError.Network)
        viewModel.onLoginClicked("alex@guardband.com", "password123")

        assertEquals(LoginViewModel.MSG_NO_CONNECTION, firstMessage())
    }

    @Test
    fun `an unmapped failure falls back to the generic message, never the code`() = runTest {
        auth.loginResult = Result.failure(AuthError.Unknown("ERROR_SOMETHING_NEW"))
        viewModel.onLoginClicked("alex@guardband.com", "password123")

        assertEquals(LoginViewModel.MSG_LOGIN_FAILED, firstMessage())
    }

    @Test
    fun `a second tap while the request is in flight is ignored`() = runTest {
        viewModel.onLoginClicked("alex@guardband.com", "password123")
        viewModel.onLoginClicked("alex@guardband.com", "password123")

        assertEquals(LoginEvent.NavigateToLoadingHome, viewModel.events.first())
        assertEquals(1, auth.loginCalls)
    }

    private suspend fun firstMessage(): String =
        (viewModel.events.first() as LoginEvent.ShowMessage).text
}
