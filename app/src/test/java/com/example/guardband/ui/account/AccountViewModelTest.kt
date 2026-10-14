package com.example.guardband.ui.account

import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.track.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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
 * Account information: the session's details, its own auth gate, the
 * verification status and the Resend cooldown.
 *
 * The clock and the ticker are injected, as on the forgot-password screen, so
 * nothing waits on real time: [now] is moved by hand and [ticks] emitted by
 * hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var now = 1_000_000L
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val auth = FakeAuthRepository(signedIn = true)
    private val events = mutableListOf<AccountEvent>()

    private fun TestScope.open(repo: FakeAuthRepository = auth): AccountViewModel {
        val vm = AccountViewModel(repo, clock = Clock { now }, cooldownTicks = ticks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events += it }
        }
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.tickAfter(millis: Long) {
        now += millis
        ticks.tryEmit(Unit)
        advanceUntilIdle()
    }

    // ── Details and the gate ──────────────────────────────────────────────────

    @Test
    fun `the details come from the session`() = runTest {
        val state = open().uiState.value

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, state.name)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, state.email)
    }

    @Test
    fun `no session goes to Login instead of showing an empty account`() = runTest {
        val vm = AccountViewModel(FakeAuthRepository(signedIn = false), Clock { now }, ticks)

        assertEquals(AccountEvent.NavigateToLogin, vm.events.first())
        assertEquals(AccountUiState(), vm.uiState.value)
    }

    // ── Verification status ───────────────────────────────────────────────────

    @Test
    fun `an unverified password account shows not verified with resend`() = runTest {
        val state = open().uiState.value

        assertFalse(state.emailVerified)
        assertTrue(state.showResend)
        assertTrue(state.resendEnabled)
    }

    @Test
    fun `a google account shows verified and never offers resend`() = runTest {
        auth.providers = setOf(SignInProvider.GOOGLE)

        val state = open().uiState.value

        assertTrue(state.emailVerified)
        assertFalse(state.showResend)
    }

    /** The user opened the link in their inbox; coming back shows it. */
    @Test
    fun `returning to the screen reloads and picks up a verified email`() = runTest {
        val vm = open()
        auth.emailVerifiedAfterReload = true

        vm.onScreenShown()
        advanceUntilIdle()

        assertEquals(1, auth.reloadUserCalls)
        assertTrue(vm.uiState.value.emailVerified)
        assertFalse(vm.uiState.value.showResend)
        // Reloading never signs anyone out.
        assertEquals(0, auth.signOutCalls)
        assertTrue(auth.isLoggedIn())
    }

    @Test
    fun `a failed reload leaves the status as it was`() = runTest {
        val vm = open()
        auth.reloadUserResult = Result.failure(AuthError.Network)
        auth.emailVerifiedAfterReload = true

        vm.onScreenShown()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.emailVerified)
        assertTrue(events.isEmpty())
    }

    // ── Resend and its cooldown ───────────────────────────────────────────────

    @Test
    fun `resend sends once and then cools down`() = runTest {
        val vm = open()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(1, auth.sendEmailVerificationCalls)
        assertEquals(listOf<AccountEvent>(AccountEvent.ShowMessage(AccountViewModel.MSG_SENT)), events)
        assertEquals(AccountViewModel.RESEND_COOLDOWN_SECONDS, vm.uiState.value.resendSecondsRemaining)
        assertFalse(vm.uiState.value.resendEnabled)
    }

    @Test
    fun `a tap during the cooldown sends nothing`() = runTest {
        val vm = open()
        vm.onResendClicked()
        advanceUntilIdle()

        tickAfter(10_000)
        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(1, auth.sendEmailVerificationCalls)
        assertEquals(50, vm.uiState.value.resendSecondsRemaining)
    }

    @Test
    fun `the cooldown counts down from the clock and then unlocks`() = runTest {
        val vm = open()
        vm.onResendClicked()
        advanceUntilIdle()

        tickAfter(59_001)
        assertEquals(1, vm.uiState.value.resendSecondsRemaining)

        tickAfter(999)
        assertEquals(0, vm.uiState.value.resendSecondsRemaining)
        assertTrue(vm.uiState.value.resendEnabled)

        vm.onResendClicked()
        advanceUntilIdle()
        assertEquals(2, auth.sendEmailVerificationCalls)
    }

    /** Firebase's own rate limit: a kind message, and the button waits too. */
    @Test
    fun `too many requests is worded kindly and starts the cooldown`() = runTest {
        auth.sendEmailVerificationResult = Result.failure(AuthError.TooManyRequests)
        val vm = open()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(
            listOf<AccountEvent>(AccountEvent.ShowMessage(AccountViewModel.MSG_TOO_MANY_REQUESTS)),
            events
        )
        assertEquals(AccountViewModel.RESEND_COOLDOWN_SECONDS, vm.uiState.value.resendSecondsRemaining)
    }

    /** Offline: nothing was sent, so there is nothing to wait out. */
    @Test
    fun `offline is reported and resend stays available`() = runTest {
        auth.sendEmailVerificationResult = Result.failure(AuthError.Network)
        val vm = open()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(
            listOf<AccountEvent>(AccountEvent.ShowMessage(AccountViewModel.MSG_NO_CONNECTION)),
            events
        )
        assertEquals(0, vm.uiState.value.resendSecondsRemaining)
        assertTrue(vm.uiState.value.resendEnabled)
    }

    @Test
    fun `an unmapped failure gets the generic wording`() = runTest {
        auth.sendEmailVerificationResult = Result.failure(AuthError.Unknown("ERROR_X"))
        val vm = open()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(
            listOf<AccountEvent>(AccountEvent.ShowMessage(AccountViewModel.MSG_SEND_FAILED)),
            events
        )
    }

    @Test
    fun `a google account cannot send even if asked`() = runTest {
        auth.providers = setOf(SignInProvider.GOOGLE)
        val vm = open()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(0, auth.sendEmailVerificationCalls)
    }
}
