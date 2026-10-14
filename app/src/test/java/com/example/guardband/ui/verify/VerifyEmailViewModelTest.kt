package com.example.guardband.ui.verify

import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The verify-email link: apply the code, then say whether it worked. */
class VerifyEmailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(signedIn = true)
    private val viewModel by lazy { VerifyEmailViewModel(auth) }
    private val state get() = viewModel.uiState.value

    @Test
    fun `a good code verifies and the code is forwarded untouched`() = runTest {
        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        assertEquals("oob-1", auth.lastVerificationCode)
        assertEquals(VerifyEmailStage.VERIFIED, state.stage)
        assertEquals(VerifyEmailViewModel.MSG_VERIFIED, state.message)
    }

    /** So Account information says "Verified" without anyone signing out. */
    @Test
    fun `a signed-in session is reloaded after verifying`() = runTest {
        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        assertEquals(1, auth.reloadUserCalls)
        assertEquals(0, auth.signOutCalls)
    }

    /** The code is the authorisation; nobody need be signed in. */
    @Test
    fun `it works with nobody signed in, and reloads nothing`() = runTest {
        val signedOut = FakeAuthRepository(signedIn = false)
        val vm = VerifyEmailViewModel(signedOut)

        vm.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        assertEquals(VerifyEmailStage.VERIFIED, vm.uiState.value.stage)
        assertEquals(0, signedOut.reloadUserCalls)
    }

    @Test
    fun `a failed reload does not undo the success`() = runTest {
        auth.reloadUserResult = Result.failure(AuthError.Network)

        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        assertEquals(VerifyEmailStage.VERIFIED, state.stage)
    }

    @Test
    fun `an unsupported link says so and calls nothing`() = runTest {
        viewModel.onLinkOpened(code = null, unsupported = true)
        advanceUntilIdle()

        assertEquals(VerifyEmailStage.UNSUPPORTED, state.stage)
        assertEquals(VerifyEmailViewModel.MSG_UNSUPPORTED, state.message)
        assertEquals(0, auth.applyEmailVerificationCalls)
    }

    @Test
    fun `a link with no usable code fails without calling firebase`() = runTest {
        viewModel.onLinkOpened(code = null, unsupported = false)
        advanceUntilIdle()

        assertEquals(VerifyEmailStage.FAILED, state.stage)
        assertEquals(VerifyEmailViewModel.MSG_LINK_UNUSABLE, state.message)
        assertFalse(state.canRetry)
        assertEquals(0, auth.applyEmailVerificationCalls)
    }

    @Test
    fun `an expired link points at resend, with no retry`() = runTest {
        auth.applyEmailVerificationResult = Result.failure(AuthError.ExpiredActionCode)

        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        assertEquals(VerifyEmailViewModel.MSG_LINK_EXPIRED, state.message)
        assertFalse(state.canRetry)
    }

    /** Also what a crafted verifyEmail link carrying another action's code gets. */
    @Test
    fun `a used or wrong-kind code is reported as used`() = runTest {
        auth.applyEmailVerificationResult = Result.failure(AuthError.InvalidActionCode)

        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        assertEquals(VerifyEmailStage.FAILED, state.stage)
        assertEquals(VerifyEmailViewModel.MSG_LINK_USED, state.message)
    }

    @Test
    fun `offline offers retry, and retry uses the same code`() = runTest {
        auth.applyEmailVerificationResult = Result.failure(AuthError.Network)
        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()
        assertTrue(state.canRetry)
        assertEquals(VerifyEmailViewModel.MSG_NO_CONNECTION, state.message)

        auth.applyEmailVerificationResult = Result.success(Unit)
        viewModel.onRetryClicked()
        advanceUntilIdle()

        assertEquals(2, auth.applyEmailVerificationCalls)
        assertEquals("oob-1", auth.lastVerificationCode)
        assertEquals(VerifyEmailStage.VERIFIED, state.stage)
    }

    @Test
    fun `retry is refused when the failure was not the connection`() = runTest {
        auth.applyEmailVerificationResult = Result.failure(AuthError.ExpiredActionCode)
        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        viewModel.onRetryClicked()
        advanceUntilIdle()

        assertEquals(1, auth.applyEmailVerificationCalls)
    }

    @Test
    fun `too many attempts gets its own wording`() = runTest {
        auth.applyEmailVerificationResult = Result.failure(AuthError.TooManyRequests)

        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        assertEquals(VerifyEmailViewModel.MSG_TOO_MANY_ATTEMPTS, state.message)
    }

    @Test
    fun `continue leaves for the app`() = runTest {
        viewModel.onLinkOpened(code = "oob-1", unsupported = false)
        advanceUntilIdle()

        viewModel.onContinueClicked()

        assertEquals(VerifyEmailEvent.Continue, viewModel.events.first())
    }
}
