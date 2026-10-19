package com.example.guardband.ui.verify

import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.track.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The verification gate: who is let through, when, and the ways out that do
 * not need the inbox. Clock and ticker injected, as on Account information.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyRequiredViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var now = 5_000_000L
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val auth = FakeAuthRepository(signedIn = true, emailVerified = false)
    private val events = mutableListOf<VerifyRequiredEvent>()

    private fun TestScope.open(repo: FakeAuthRepository = auth): VerifyRequiredViewModel {
        val vm = VerifyRequiredViewModel(repo, Clock { now }, ticks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events += it }
        }
        advanceUntilIdle()
        return vm
    }

    // ── Opening ───────────────────────────────────────────────────────────────

    @Test
    fun `an unverified account stays and is shown its own address`() = runTest {
        val vm = open()

        assertTrue(events.isEmpty())
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, vm.uiState.value.email)
    }

    @Test
    fun `no session goes to login`() = runTest {
        open(FakeAuthRepository(signedIn = false))

        assertEquals(listOf<VerifyRequiredEvent>(VerifyRequiredEvent.NavigateToLogin), events)
    }

    @Test
    fun `an account that is already verified goes straight home`() = runTest {
        open(FakeAuthRepository(signedIn = true, emailVerified = true))

        assertEquals(listOf<VerifyRequiredEvent>(VerifyRequiredEvent.ContinueToHome), events)
    }

    @Test
    fun `a google account is waved through`() = runTest {
        open(FakeAuthRepository(signedIn = true, providers = setOf(SignInProvider.GOOGLE)))

        assertEquals(listOf<VerifyRequiredEvent>(VerifyRequiredEvent.ContinueToHome), events)
    }

    // ── Being let through ─────────────────────────────────────────────────────

    /** The ordinary path: tap the link in the inbox, come back, carry on. */
    @Test
    fun `returning after opening the link lets them through`() = runTest {
        val vm = open()
        auth.emailVerifiedAfterReload = true

        vm.onScreenShown()
        advanceUntilIdle()

        assertEquals(1, auth.reloadUserCalls)
        assertEquals(listOf<VerifyRequiredEvent>(VerifyRequiredEvent.ContinueToHome), events)
        assertEquals(0, auth.signOutCalls)
    }

    @Test
    fun `returning before opening the link stays, silently`() = runTest {
        val vm = open()

        vm.onScreenShown()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `the button says so when it is not verified yet`() = runTest {
        val vm = open()

        vm.onCheckClicked()
        advanceUntilIdle()

        assertEquals(
            listOf<VerifyRequiredEvent>(VerifyRequiredEvent.ShowMessage(VerifyRequiredViewModel.MSG_NOT_YET)),
            events
        )
    }

    @Test
    fun `the button lets them through once verified`() = runTest {
        val vm = open()
        auth.emailVerifiedAfterReload = true

        vm.onCheckClicked()
        advanceUntilIdle()

        assertEquals(listOf<VerifyRequiredEvent>(VerifyRequiredEvent.ContinueToHome), events)
    }

    @Test
    fun `checking offline says so and keeps them here`() = runTest {
        auth.reloadUserResult = Result.failure(AuthError.Network)
        auth.emailVerifiedAfterReload = true
        val vm = open()

        vm.onCheckClicked()
        advanceUntilIdle()

        assertEquals(
            listOf<VerifyRequiredEvent>(VerifyRequiredEvent.ShowMessage(VerifyRequiredViewModel.MSG_NO_CONNECTION)),
            events
        )
    }

    /** Offline on the quiet check: no toast every time the screen comes back. */
    @Test
    fun `a failed quiet check says nothing`() = runTest {
        auth.reloadUserResult = Result.failure(AuthError.Network)
        val vm = open()

        vm.onScreenShown()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `an account deleted elsewhere goes to login`() = runTest {
        auth.reloadUserResult = Result.failure(AuthError.UserDisabled)
        val vm = open()

        vm.onScreenShown()
        advanceUntilIdle()

        assertEquals(listOf<VerifyRequiredEvent>(VerifyRequiredEvent.NavigateToLogin), events)
    }

    // ── Resend ────────────────────────────────────────────────────────────────

    @Test
    fun `resend sends once, then cools down and unlocks`() = runTest {
        val vm = open()

        vm.onResendClicked()
        advanceUntilIdle()
        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(1, auth.sendEmailVerificationCalls)
        assertEquals(60, vm.uiState.value.resendSecondsRemaining)
        assertFalse(vm.uiState.value.resendEnabled)

        now += 60_000
        ticks.tryEmit(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.resendEnabled)
    }

    @Test
    fun `too many requests is worded kindly and starts the cooldown`() = runTest {
        auth.sendEmailVerificationResult = Result.failure(AuthError.TooManyRequests)
        val vm = open()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(
            listOf<VerifyRequiredEvent>(
                VerifyRequiredEvent.ShowMessage(VerifyRequiredViewModel.MSG_TOO_MANY_REQUESTS)
            ),
            events
        )
        assertEquals(60, vm.uiState.value.resendSecondsRemaining)
    }

    @Test
    fun `offline resend is reported and stays available`() = runTest {
        auth.sendEmailVerificationResult = Result.failure(AuthError.Network)
        val vm = open()

        vm.onResendClicked()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.resendEnabled)
        assertEquals(
            listOf<VerifyRequiredEvent>(VerifyRequiredEvent.ShowMessage(VerifyRequiredViewModel.MSG_NO_CONNECTION)),
            events
        )
    }

    // ── Ways out ──────────────────────────────────────────────────────────────

    @Test
    fun `log out signs out and leaves`() = runTest {
        val vm = open()

        vm.onLogOutClicked()
        advanceUntilIdle()

        assertEquals(1, auth.signOutCalls)
        assertEquals(listOf<VerifyRequiredEvent>(VerifyRequiredEvent.LoggedOut), events)
    }

    /** A mistyped address can never receive the link; deleting is the way out. */
    @Test
    fun `wrong email opens account information and signs nobody out`() = runTest {
        val vm = open()

        vm.onDeleteAccountClicked()
        advanceUntilIdle()

        assertEquals(listOf<VerifyRequiredEvent>(VerifyRequiredEvent.OpenAccount), events)
        assertEquals(0, auth.signOutCalls)
    }
}
