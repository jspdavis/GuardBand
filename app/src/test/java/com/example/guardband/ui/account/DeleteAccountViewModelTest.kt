package com.example.guardband.ui.account

import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.auth.GoogleIdTokenResult
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
 * The Delete account state machine: which credential it asks for, the order of
 * the two deletes, and what a retry resumes.
 *
 * The repositories themselves are not covered — `reauthenticate` and `delete`
 * need a real `FirebaseAuth`, and the project declares no mocking library. The
 * logic worth protecting is the sequencing and the guards, and all of that
 * lives here.
 *
 * Unlike the older ViewModel tests, these collect the event channel into
 * [events] and drive the dispatcher with `advanceUntilIdle` rather than
 * relying on `events.first()` to suspend at the right moment. This flow emits
 * several events in a row, so "the first one" is not a useful question, and a
 * retry has to be stepped through deliberately.
 *
 * No Robolectric: nothing here reaches `android.util.Patterns`.
 * [com.example.guardband.utils.InputValidator.isBlank] is plain Kotlin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(signedIn = true)
    private val profiles = FakeUserProfileRepository()
    private val viewModel = DeleteAccountViewModel(auth, profiles)

    private val uid = FakeAuthRepository.DEFAULT_USER.id

    /** Every event the ViewModel has emitted, in order. */
    private val events = mutableListOf<DeleteAccountEvent>()

    private val state get() = viewModel.uiState.value

    /**
     * Starts the event collector. Every test calls this first.
     *
     * On [UnconfinedTestDispatcher] deliberately, which is the documented way
     * to drain a flow into a list: the collector resumes the moment an element
     * is available, so the list is current without a test having to work out
     * how many dispatches each emission is behind.
     */
    private fun TestScope.collectEvents() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events += it }
        }
    }

    /** The row, then Delete in the modal. Nothing is written by either. */
    private fun TestScope.confirmDelete() {
        viewModel.onDeleteAccountClicked()
        viewModel.onConfirmed()
        advanceUntilIdle()
    }

    /** The ordinary path for a password account, run to completion. */
    private fun TestScope.submitPassword(password: String = "password123") {
        confirmDelete()
        viewModel.onPasswordSubmitted(password)
        advanceUntilIdle()
    }

    // ── Choosing the credential ───────────────────────────────────────────────

    @Test
    fun `a password account is asked for its password`() = runTest {
        collectEvents()
        auth.providers = setOf(SignInProvider.PASSWORD)

        confirmDelete()

        assertEquals(SignInProvider.PASSWORD, state.method)
        assertEquals(
            listOf(DeleteAccountEvent.ShowConfirmation, DeleteAccountEvent.AskForPassword),
            events
        )
    }

    @Test
    fun `a google account is asked for a fresh token`() = runTest {
        collectEvents()
        auth.providers = setOf(SignInProvider.GOOGLE)

        confirmDelete()

        assertEquals(SignInProvider.GOOGLE, state.method)
        assertEquals(
            listOf(DeleteAccountEvent.ShowConfirmation, DeleteAccountEvent.RequestGoogleIdToken),
            events
        )
    }

    /**
     * A linked account could report both. Password wins: either credential
     * satisfies Firebase, so the tie-break is UX, and typing a password needs
     * no chooser and no second account picked by mistake.
     */
    @Test
    fun `a mixed account is asked for its password, not google`() = runTest {
        collectEvents()
        auth.providers = setOf(SignInProvider.GOOGLE, SignInProvider.PASSWORD)

        confirmDelete()

        assertEquals(SignInProvider.PASSWORD, state.method)
        assertTrue(DeleteAccountEvent.AskForPassword in events)
        assertFalse(DeleteAccountEvent.RequestGoogleIdToken in events)
    }

    /** No recognised provider: it refuses to start rather than guessing one. */
    @Test
    fun `an account with no usable provider cannot start the flow`() = runTest {
        collectEvents()
        auth.providers = emptySet()

        viewModel.onDeleteAccountClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(
                DeleteAccountEvent.ShowMessage(DeleteAccountViewModel.MSG_CANNOT_REAUTHENTICATE)
            ),
            events
        )
        assertEquals(DeleteAccountStep.IDLE, state.step)
        assertNull(state.method)
        assertEquals(0, profiles.deleteProfileCalls)
        assertEquals(0, auth.deleteAccountCalls)
    }

    @Test
    fun `no session sends the user to login and deletes nothing`() = runTest {
        val signedOut = FakeAuthRepository(signedIn = false)
        val vm = DeleteAccountViewModel(signedOut, profiles)
        val seen = mutableListOf<DeleteAccountEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { seen += it }
        }

        vm.onDeleteAccountClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(
                DeleteAccountEvent.ShowMessage(DeleteAccountViewModel.MSG_SESSION_EXPIRED),
                DeleteAccountEvent.NavigateToLogin
            ),
            seen
        )
        assertEquals(0, profiles.deleteProfileCalls)
        assertEquals(0, signedOut.deleteAccountCalls)
    }

    // ── Cancelling ────────────────────────────────────────────────────────────

    @Test
    fun `cancel before anything is written resets the flow`() = runTest {
        collectEvents()

        viewModel.onDeleteAccountClicked()
        viewModel.onCancelled()
        advanceUntilIdle()

        assertEquals(DeleteAccountStep.IDLE, state.step)
        assertNull(state.method)
        assertEquals(0, profiles.deleteProfileCalls)
    }

    @Test
    fun `the modal alone writes nothing`() = runTest {
        collectEvents()

        confirmDelete()

        assertEquals(0, auth.reauthenticateWithPasswordCalls)
        assertEquals(0, profiles.deleteProfileCalls)
        assertEquals(0, auth.deleteAccountCalls)
    }

    // ── Re-authentication ─────────────────────────────────────────────────────

    @Test
    fun `a blank password is refused without calling the repository`() = runTest {
        collectEvents()

        confirmDelete()
        viewModel.onPasswordSubmitted("   ")
        advanceUntilIdle()

        assertTrue(
            DeleteAccountEvent.ShowMessage(DeleteAccountViewModel.MSG_PASSWORD_REQUIRED) in events
        )
        assertEquals(0, auth.reauthenticateWithPasswordCalls)
    }

    @Test
    fun `the password is forwarded untouched`() = runTest {
        collectEvents()

        submitPassword("  spaces inside  ")

        assertEquals("  spaces inside  ", auth.lastReauthPassword)
    }

    @Test
    fun `a wrong password fails the flow and deletes nothing`() = runTest {
        collectEvents()
        auth.reauthenticateWithPasswordResult = Result.failure(AuthError.InvalidCredentials)

        submitPassword()

        assertEquals(DeleteAccountStep.FAILED, state.step)
        assertEquals(DeleteAccountViewModel.MSG_WRONG_PASSWORD, state.errorMessage)
        assertFalse(state.reauthenticated)
        assertEquals(0, profiles.deleteProfileCalls)
        assertEquals(0, auth.deleteAccountCalls)
    }

    @Test
    fun `an unmapped reauth failure gets the generic wording`() = runTest {
        collectEvents()
        auth.reauthenticateWithPasswordResult = Result.failure(AuthError.Unknown(null))

        submitPassword()

        assertEquals(DeleteAccountViewModel.MSG_REAUTH_FAILED, state.errorMessage)
        assertEquals(0, profiles.deleteProfileCalls)
    }

    @Test
    fun `a dead session found during reauth sends the user to login`() = runTest {
        collectEvents()
        auth.reauthenticateWithPasswordResult = Result.failure(AuthError.NotSignedIn)

        submitPassword()

        assertEquals(DeleteAccountStep.IDLE, state.step)
        assertTrue(DeleteAccountEvent.NavigateToLogin in events)
        assertEquals(0, profiles.deleteProfileCalls)
    }

    @Test
    fun `the wrong google account is named as such`() = runTest {
        collectEvents()
        auth.providers = setOf(SignInProvider.GOOGLE)
        auth.reauthenticateWithGoogleResult = Result.failure(AuthError.UserMismatch)

        confirmDelete()
        viewModel.onGoogleIdToken("token-1")
        advanceUntilIdle()

        assertEquals("token-1", auth.lastReauthGoogleIdToken)
        assertEquals(DeleteAccountViewModel.MSG_WRONG_GOOGLE_ACCOUNT, state.errorMessage)
        assertEquals(0, profiles.deleteProfileCalls)
    }

    /** Dismissing the chooser is a deliberate back-out, so it says nothing. */
    @Test
    fun `a cancelled chooser is silent and resets the flow`() = runTest {
        collectEvents()
        auth.providers = setOf(SignInProvider.GOOGLE)

        confirmDelete()
        viewModel.onGoogleError(GoogleIdTokenResult.Cancelled)
        advanceUntilIdle()

        assertEquals(DeleteAccountStep.IDLE, state.step)
        assertNull(state.errorMessage)
        assertEquals(0, auth.reauthenticateWithGoogleCalls)
        assertFalse(events.any { it is DeleteAccountEvent.ShowMessage })
    }

    @Test
    fun `an unavailable chooser is reported`() = runTest {
        collectEvents()
        auth.providers = setOf(SignInProvider.GOOGLE)

        confirmDelete()
        viewModel.onGoogleError(GoogleIdTokenResult.Unavailable)
        advanceUntilIdle()

        assertEquals(DeleteAccountStep.FAILED, state.step)
        assertEquals(DeleteAccountViewModel.MSG_GOOGLE_UNAVAILABLE, state.errorMessage)
    }

    // ── The deletes, in order ─────────────────────────────────────────────────

    @Test
    fun `success removes the data, then the account, then leaves`() = runTest {
        collectEvents()
        profiles.saveProfile(uid, "Alex Rivera", "alex@guardband.com")

        submitPassword()

        assertEquals(listOf(uid), profiles.deletedUids)
        assertEquals(1, auth.deleteAccountCalls)
        assertFalse(profiles.saved.containsKey(uid))
        assertEquals(DeleteAccountStep.DONE, state.step)
        assertTrue(state.profileDeleted)
        assertNull(state.errorMessage)
        assertEquals(DeleteAccountEvent.NavigateToLogin, events.last())
    }

    /** `delete()` ends the session itself; the explicit signOut is belt and braces. */
    @Test
    fun `the session is signed out once the account is gone`() = runTest {
        collectEvents()

        submitPassword()

        assertEquals(1, auth.signOutCalls)
        assertFalse(auth.isLoggedIn())
    }

    /**
     * The data goes first, while `auth.uid` can still authorise it. If that
     * write fails the account must be left alone, which is also what proves the
     * order.
     */
    @Test
    fun `a failed data delete leaves the account alone`() = runTest {
        collectEvents()
        profiles.deleteProfileResult = Result.failure(AuthError.Unknown(null))

        submitPassword()

        assertEquals(1, profiles.deleteProfileCalls)
        assertEquals(0, auth.deleteAccountCalls)
        assertEquals(DeleteAccountStep.FAILED, state.step)
        assertEquals(DeleteAccountViewModel.MSG_DATA_DELETE_FAILED, state.errorMessage)
        assertFalse(state.profileDeleted)
        assertTrue(state.reauthenticated)
    }

    @Test
    fun `a refused data delete says so`() = runTest {
        collectEvents()
        profiles.deleteProfileResult = Result.failure(AuthError.PermissionDenied)

        submitPassword()

        assertEquals(DeleteAccountViewModel.MSG_DATA_DELETE_REFUSED, state.errorMessage)
    }

    @Test
    fun `a failed account delete reports the data as already gone`() = runTest {
        collectEvents()
        auth.deleteAccountResult = Result.failure(AuthError.Unknown(null))

        submitPassword()

        assertEquals(1, profiles.deleteProfileCalls)
        assertEquals(1, auth.deleteAccountCalls)
        assertEquals(DeleteAccountStep.FAILED, state.step)
        assertEquals(DeleteAccountViewModel.MSG_ACCOUNT_DELETE_FAILED, state.errorMessage)
        assertTrue(state.profileDeleted)
        assertTrue(state.isUnfinished)
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    /** The whole point of the guards: the data delete is not run twice. */
    @Test
    fun `retry after a failed account delete re-runs only the account delete`() = runTest {
        collectEvents()
        auth.deleteAccountResult = Result.failure(AuthError.Unknown(null))
        submitPassword()

        auth.deleteAccountResult = Result.success(Unit)
        viewModel.onRetryClicked()
        advanceUntilIdle()

        assertEquals(1, profiles.deleteProfileCalls)
        assertEquals(2, auth.deleteAccountCalls)
        assertEquals(1, auth.reauthenticateWithPasswordCalls)
        assertEquals(DeleteAccountStep.DONE, state.step)
    }

    @Test
    fun `retry after a failed data delete re-runs both steps`() = runTest {
        collectEvents()
        profiles.deleteProfileResult = Result.failure(AuthError.Unknown(null))
        submitPassword()

        profiles.deleteProfileResult = Result.success(Unit)
        viewModel.onRetryClicked()
        advanceUntilIdle()

        assertEquals(2, profiles.deleteProfileCalls)
        assertEquals(1, auth.deleteAccountCalls)
        assertEquals(1, auth.reauthenticateWithPasswordCalls)
        assertEquals(DeleteAccountStep.DONE, state.step)
    }

    /** No fresh credential yet, so Retry has to ask for one rather than call again. */
    @Test
    fun `retry after a wrong password asks for the password again`() = runTest {
        collectEvents()
        auth.reauthenticateWithPasswordResult = Result.failure(AuthError.InvalidCredentials)
        submitPassword()
        events.clear()

        viewModel.onRetryClicked()
        advanceUntilIdle()

        assertEquals(listOf(DeleteAccountEvent.AskForPassword), events)
        assertEquals(1, auth.reauthenticateWithPasswordCalls)
    }

    /**
     * Firebase closed the window between the re-auth and the delete, so the
     * guard has to be given up and the credential asked for a second time.
     */
    @Test
    fun `a recent-login failure sends retry back through the prompt`() = runTest {
        collectEvents()
        auth.deleteAccountResult = Result.failure(AuthError.RequiresRecentLogin)
        submitPassword()

        assertTrue(state.profileDeleted)
        assertFalse(state.reauthenticated)
        assertEquals(DeleteAccountViewModel.MSG_NEEDS_RECENT_LOGIN, state.errorMessage)

        events.clear()
        viewModel.onRetryClicked()
        advanceUntilIdle()
        assertEquals(listOf(DeleteAccountEvent.AskForPassword), events)

        auth.deleteAccountResult = Result.success(Unit)
        viewModel.onPasswordSubmitted("password123")
        advanceUntilIdle()

        // Still one data delete: the second credential does not undo the guard.
        assertEquals(1, profiles.deleteProfileCalls)
        assertEquals(2, auth.deleteAccountCalls)
        assertEquals(2, auth.reauthenticateWithPasswordCalls)
        assertEquals(DeleteAccountStep.DONE, state.step)
    }

    // ── The point of no return ────────────────────────────────────────────────

    /**
     * Once the data is gone the user must keep a way to finish, so Cancel
     * cannot drop them back to an idle screen that says nothing is wrong.
     */
    @Test
    fun `cancel is ignored once the data is already deleted`() = runTest {
        collectEvents()
        auth.deleteAccountResult = Result.failure(AuthError.Unknown(null))
        submitPassword()

        viewModel.onCancelled()
        advanceUntilIdle()

        assertEquals(DeleteAccountStep.FAILED, state.step)
        assertTrue(state.profileDeleted)
        assertEquals(DeleteAccountViewModel.MSG_ACCOUNT_DELETE_FAILED, state.errorMessage)
    }

    /** Tapping the row again in that state resumes rather than re-confirming. */
    @Test
    fun `the row resumes an unfinished delete instead of re-confirming`() = runTest {
        collectEvents()
        auth.deleteAccountResult = Result.failure(AuthError.Unknown(null))
        submitPassword()
        events.clear()

        auth.deleteAccountResult = Result.success(Unit)
        viewModel.onDeleteAccountClicked()
        advanceUntilIdle()

        assertFalse(DeleteAccountEvent.ShowConfirmation in events)
        assertEquals(1, profiles.deleteProfileCalls)
        assertEquals(2, auth.deleteAccountCalls)
        assertEquals(DeleteAccountStep.DONE, state.step)
    }

    /**
     * Signed out between the two deletes: the data is gone and the account is
     * not, and no retry on this device can fix it, so the message says what to
     * do next instead of pretending Retry will work.
     */
    @Test
    fun `a session lost after the data delete explains what is left`() = runTest {
        collectEvents()
        auth.deleteAccountResult = Result.failure(AuthError.Unknown(null))
        submitPassword()
        auth.forceSignOut()

        viewModel.onRetryClicked()
        advanceUntilIdle()

        assertEquals(DeleteAccountStep.FAILED, state.step)
        assertEquals(DeleteAccountViewModel.MSG_SESSION_LOST_MID_DELETE, state.errorMessage)
        assertTrue(state.profileDeleted)
    }

    @Test
    fun `a finished flow is inert`() = runTest {
        collectEvents()

        submitPassword()
        assertEquals(DeleteAccountStep.DONE, state.step)

        viewModel.onDeleteAccountClicked()
        advanceUntilIdle()

        assertEquals(1, auth.deleteAccountCalls)
        assertEquals(1, profiles.deleteProfileCalls)
    }
}
