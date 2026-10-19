package com.example.guardband.ui.reset

import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * New Credentials: checking the link's code, gating Submit on the criteria,
 * and where each failure leaves the screen.
 *
 * `verifyPasswordResetCode` and `confirmPasswordReset` themselves are not
 * covered — they need a real `FirebaseAuth` and the project declares no
 * mocking library. What is covered is everything that decides what the user
 * sees.
 *
 * No Robolectric: `PasswordCriteria` is plain Kotlin and nothing here reaches
 * `android.util.Patterns`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val viewModel = ResetPasswordViewModel(auth)

    private val events = mutableListOf<ResetPasswordEvent>()

    private val state get() = viewModel.uiState.value

    private fun TestScope.collectEvents() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events += it }
        }
    }

    /** Opens a good link and lets the code check settle. */
    private fun TestScope.openLink(code: String = CODE) {
        viewModel.onLinkOpened(code)
        advanceUntilIdle()
    }

    private fun TestScope.submit(
        password: String = GOOD_PASSWORD,
        confirm: String = GOOD_PASSWORD
    ) {
        viewModel.onSubmitClicked(password, confirm)
        advanceUntilIdle()
    }

    // ── Verifying the link ────────────────────────────────────────────────────

    @Test
    fun `a good code opens the form and shows whose account it is`() = runTest {
        collectEvents()
        auth.verifyPasswordResetCodeResult = Result.success("alex@guardband.com")

        openLink()

        assertEquals(CODE, auth.lastResetCode)
        assertEquals(ResetPasswordStage.READY, state.stage)
        assertEquals("alex@guardband.com", state.email)
        assertNull(state.message)
        assertFalse(state.canRetryVerify)
    }

    @Test
    fun `a null code never reaches the repository`() = runTest {
        collectEvents()

        viewModel.onLinkOpened(null)
        advanceUntilIdle()

        assertEquals(0, auth.verifyPasswordResetCodeCalls)
        assertEquals(ResetPasswordStage.LINK_DEAD, state.stage)
        assertEquals(ResetPasswordViewModel.MSG_LINK_UNUSABLE, state.message)
    }

    @Test
    fun `an expired code is a dead end with no retry`() = runTest {
        collectEvents()
        auth.verifyPasswordResetCodeResult = Result.failure(AuthError.ExpiredActionCode)

        openLink()

        assertEquals(ResetPasswordStage.LINK_DEAD, state.stage)
        assertEquals(ResetPasswordViewModel.MSG_LINK_EXPIRED, state.message)
        assertFalse(state.canRetryVerify)
    }

    @Test
    fun `an already-used code says so`() = runTest {
        collectEvents()
        auth.verifyPasswordResetCodeResult = Result.failure(AuthError.InvalidActionCode)

        openLink()

        assertEquals(ResetPasswordViewModel.MSG_LINK_USED, state.message)
    }

    /**
     * A dropped connection says nothing about the code, so the same link is
     * still worth trying. Everything else has settled the question.
     */
    @Test
    fun `a network failure while verifying offers a retry that works`() = runTest {
        collectEvents()
        auth.verifyPasswordResetCodeResult = Result.failure(AuthError.Network)

        openLink()

        assertEquals(ResetPasswordStage.LINK_DEAD, state.stage)
        assertEquals(ResetPasswordViewModel.MSG_NO_CONNECTION, state.message)
        assertTrue(state.canRetryVerify)

        auth.verifyPasswordResetCodeResult = Result.success(FakeAuthRepository.RESET_EMAIL)
        viewModel.onRetryVerifyClicked()
        advanceUntilIdle()

        assertEquals(2, auth.verifyPasswordResetCodeCalls)
        assertEquals(ResetPasswordStage.READY, state.stage)
        assertFalse(state.canRetryVerify)
    }

    @Test
    fun `retry does nothing when there was never a code`() = runTest {
        collectEvents()
        viewModel.onLinkOpened(null)
        advanceUntilIdle()

        viewModel.onRetryVerifyClicked()
        advanceUntilIdle()

        assertEquals(0, auth.verifyPasswordResetCodeCalls)
    }

    // ── The criteria ──────────────────────────────────────────────────────────

    @Test
    fun `the criteria start neutral rather than failed`() {
        assertTrue(state.criteria.untouched)
        assertFalse(state.criteria.allMet)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `the criteria follow what is typed`() = runTest {
        collectEvents()
        openLink()

        viewModel.onPasswordChanged("short", "short")
        assertFalse(state.criteria.longEnough)
        assertTrue(state.criteria.matches)
        assertFalse(state.canSubmit)

        viewModel.onPasswordChanged(GOOD_PASSWORD, "different")
        assertTrue(state.criteria.longEnough)
        assertFalse(state.criteria.matches)
        assertFalse(state.canSubmit)

        viewModel.onPasswordChanged(GOOD_PASSWORD, GOOD_PASSWORD)
        assertTrue(state.criteria.allMet)
        assertTrue(state.canSubmit)
    }

    /** Submit stays dead until the code check has passed, criteria or not. */
    @Test
    fun `the criteria alone do not enable submit before the code is checked`() = runTest {
        collectEvents()
        auth.verifyPasswordResetCodeResult = Result.failure(AuthError.ExpiredActionCode)
        openLink()

        viewModel.onPasswordChanged(GOOD_PASSWORD, GOOD_PASSWORD)

        assertTrue(state.criteria.allMet)
        assertFalse(state.canSubmit)
    }

    // ── Submitting ────────────────────────────────────────────────────────────

    @Test
    fun `submitting forwards the code and the new password`() = runTest {
        collectEvents()
        openLink()

        submit()

        assertEquals(CODE, auth.lastResetCode)
        assertEquals(GOOD_PASSWORD, auth.lastNewPassword)
        assertEquals(ResetPasswordStage.DONE, state.stage)
        assertTrue(ResetPasswordEvent.NavigateToSuccess in events)
    }

    @Test
    fun `a mismatched confirmation is caught before the repository`() = runTest {
        collectEvents()
        openLink()

        submit(password = GOOD_PASSWORD, confirm = "something else")

        assertEquals(0, auth.confirmPasswordResetCalls)
        assertEquals(ResetPasswordStage.READY, state.stage)
        assertEquals(ResetPasswordViewModel.MSG_PASSWORDS_DIFFER, state.fieldError)
    }

    @Test
    fun `a short password is caught before the repository`() = runTest {
        collectEvents()
        openLink()

        submit(password = "short", confirm = "short")

        assertEquals(0, auth.confirmPasswordResetCalls)
        assertEquals(ResetPasswordViewModel.MSG_PASSWORD_TOO_SHORT, state.fieldError)
    }

    /** Firebase's minimum is six and the app asks for eight, so this means it got stricter. */
    @Test
    fun `a server-side weak password lands on the field`() = runTest {
        collectEvents()
        openLink()
        auth.confirmPasswordResetResult = Result.failure(AuthError.WeakPassword)

        submit()

        assertEquals(ResetPasswordStage.READY, state.stage)
        assertEquals(ResetPasswordViewModel.MSG_PASSWORD_REJECTED, state.fieldError)
        assertNull(state.message)
    }

    @Test
    fun `typing again clears a stale field error`() = runTest {
        collectEvents()
        openLink()
        auth.confirmPasswordResetResult = Result.failure(AuthError.WeakPassword)
        submit()

        viewModel.onPasswordChanged("another-password", "another-password")

        assertNull(state.fieldError)
    }

    /**
     * The fields are never cleared, so a retry re-sends the same text. That is
     * what "keeps what was typed" amounts to in a ViewModel that does not hold
     * the password.
     */
    @Test
    fun `a network failure is retryable with the same input`() = runTest {
        collectEvents()
        openLink()
        auth.confirmPasswordResetResult = Result.failure(AuthError.Network)

        submit()

        assertEquals(ResetPasswordStage.READY, state.stage)
        assertTrue(
            ResetPasswordEvent.ShowMessage(ResetPasswordViewModel.MSG_NO_CONNECTION) in events
        )
        assertNull(state.fieldError)
        assertTrue(state.canSubmit)

        auth.confirmPasswordResetResult = Result.success(Unit)
        submit()

        assertEquals(2, auth.confirmPasswordResetCalls)
        assertEquals(GOOD_PASSWORD, auth.lastNewPassword)
        assertEquals(ResetPasswordStage.DONE, state.stage)
    }

    /**
     * The code is single-use and the failed attempt may well have spent it, so
     * a retry would be a loop that cannot succeed.
     */
    @Test
    fun `a code that dies on submit is a dead end, not a retry`() = runTest {
        collectEvents()
        openLink()
        auth.confirmPasswordResetResult = Result.failure(AuthError.InvalidActionCode)

        submit()

        assertEquals(ResetPasswordStage.LINK_DEAD, state.stage)
        assertEquals(ResetPasswordViewModel.MSG_LINK_USED, state.message)
        assertFalse(state.canSubmit)

        submit()
        assertEquals(1, auth.confirmPasswordResetCalls)
    }

    @Test
    fun `an unmapped submit failure is retryable with generic wording`() = runTest {
        collectEvents()
        openLink()
        auth.confirmPasswordResetResult = Result.failure(AuthError.Unknown(null))

        submit()

        assertEquals(ResetPasswordStage.READY, state.stage)
        assertTrue(
            ResetPasswordEvent.ShowMessage(ResetPasswordViewModel.MSG_RESET_FAILED) in events
        )
    }

    @Test
    fun `submitting does nothing before the code is checked`() = runTest {
        collectEvents()

        submit()

        assertEquals(0, auth.confirmPasswordResetCalls)
    }

    @Test
    fun `asking for a new link navigates to the request screen`() = runTest {
        collectEvents()
        auth.verifyPasswordResetCodeResult = Result.failure(AuthError.ExpiredActionCode)
        openLink()

        viewModel.onRequestNewLinkClicked()
        advanceUntilIdle()

        assertTrue(ResetPasswordEvent.NavigateToRequest in events)
    }

    private companion object {
        const val CODE = "oob-code-1234"
        const val GOOD_PASSWORD = "newpassword123"
    }
}
