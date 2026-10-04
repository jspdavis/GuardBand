package com.example.guardband.ui.forgot

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

/** Robolectric: validation uses `android.util.Patterns`. */
@RunWith(RobolectricTestRunner::class)
class ForgotRequestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val viewModel = ForgotRequestViewModel(auth)

    @Test
    fun `a blank email is rejected before the repository is called`() = runTest {
        viewModel.onSendClicked("")

        assertEquals(ForgotRequestViewModel.MSG_EMAIL_REQUIRED, firstMessage())
        assertEquals(0, auth.sendPasswordResetCalls)
    }

    @Test
    fun `a malformed email is rejected before the repository is called`() = runTest {
        viewModel.onSendClicked("not-an-email")

        assertEquals(ForgotRequestViewModel.MSG_EMAIL_INVALID, firstMessage())
        assertEquals(0, auth.sendPasswordResetCalls)
    }

    @Test
    fun `a sent email confirms`() = runTest {
        viewModel.onSendClicked("alex@guardband.com")

        assertEquals(ForgotRequestEvent.NavigateToSent, viewModel.events.first())
        assertEquals(1, auth.sendPasswordResetCalls)
    }

    @Test
    fun `an unknown account confirms exactly like a real one, revealing nothing`() = runTest {
        auth.sendPasswordResetResult = Result.failure(AuthError.NoSuchUser)
        viewModel.onSendClicked("nobody@guardband.com")

        assertEquals(ForgotRequestEvent.NavigateToSent, viewModel.events.first())
    }

    @Test
    fun `a network failure is reported, because the request did not happen`() = runTest {
        auth.sendPasswordResetResult = Result.failure(AuthError.Network)
        viewModel.onSendClicked("alex@guardband.com")

        assertEquals(ForgotRequestViewModel.MSG_NO_CONNECTION, firstMessage())
    }

    @Test
    fun `being rate limited is reported`() = runTest {
        auth.sendPasswordResetResult = Result.failure(AuthError.TooManyRequests)
        viewModel.onSendClicked("alex@guardband.com")

        assertEquals(ForgotRequestViewModel.MSG_TOO_MANY_ATTEMPTS, firstMessage())
    }

    @Test
    fun `a second tap while the request is in flight is ignored`() = runTest {
        viewModel.onSendClicked("alex@guardband.com")
        viewModel.onSendClicked("alex@guardband.com")

        assertEquals(ForgotRequestEvent.NavigateToSent, viewModel.events.first())
        assertEquals(1, auth.sendPasswordResetCalls)
    }

    private suspend fun firstMessage(): String =
        (viewModel.events.first() as ForgotRequestEvent.ShowMessage).text
}
