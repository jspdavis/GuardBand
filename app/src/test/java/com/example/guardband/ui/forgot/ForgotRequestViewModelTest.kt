package com.example.guardband.ui.forgot

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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The request screen: email validation, the enumeration property, and the
 * resend cooldown.
 *
 * **Robolectric**, because `InputValidator.isValidEmail` goes through
 * `android.util.Patterns`.
 *
 * The clock and the cooldown ticker are both injected, so nothing here waits
 * on real time and nothing is left holding an endless `delay`. [now] is moved
 * by hand and [ticks] is emitted by hand; the ViewModel recomputes the
 * countdown from the clock on each tick rather than counting down internally,
 * which is what makes that safe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ForgotRequestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()

    private var now = 1_000_000L
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    private val viewModel = ForgotRequestViewModel(
        authRepository = auth,
        clock = Clock { now },
        cooldownTicks = ticks
    )

    private val events = mutableListOf<ForgotRequestEvent>()

    private val state get() = viewModel.uiState.value

    private fun TestScope.collectEvents() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events += it }
        }
    }

    private fun TestScope.send(email: String = EMAIL) {
        viewModel.onSendClicked(email)
        advanceUntilIdle()
    }

    /** Moves the clock and fires one tick, as the real ticker would. */
    private suspend fun TestScope.elapse(millis: Long) {
        now += millis
        ticks.emit(Unit)
        advanceUntilIdle()
    }

    private fun messages() = events.filterIsInstance<ForgotRequestEvent.ShowMessage>().map { it.text }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `a blank email is refused without a request`() = runTest {
        collectEvents()

        send("   ")

        assertEquals(listOf(ForgotRequestViewModel.MSG_EMAIL_REQUIRED), messages())
        assertEquals(0, auth.sendPasswordResetCalls)
        assertFalse(state.sent)
    }

    @Test
    fun `a malformed email is refused without a request`() = runTest {
        collectEvents()

        send("not-an-email")

        assertEquals(listOf(ForgotRequestViewModel.MSG_EMAIL_INVALID), messages())
        assertEquals(0, auth.sendPasswordResetCalls)
    }

    // ── The enumeration property ──────────────────────────────────────────────

    @Test
    fun `a registered address reaches the sent state`() = runTest {
        collectEvents()

        send()

        assertEquals(1, auth.sendPasswordResetCalls)
        assertTrue(state.sent)
        assertTrue(messages().isEmpty())
    }

    /**
     * The whole point: an unregistered address must be indistinguishable from
     * a registered one, or this screen becomes a way to find out who has an
     * account.
     */
    @Test
    fun `an unregistered address looks exactly the same`() = runTest {
        collectEvents()
        auth.sendPasswordResetResult = Result.failure(AuthError.NoSuchUser)

        send()

        assertTrue(state.sent)
        assertTrue(messages().isEmpty())
        assertEquals(
            ForgotRequestViewModel.RESEND_COOLDOWN_SECONDS,
            state.resendSecondsRemaining
        )
    }

    /** Same again for an address Firebase itself calls malformed. */
    @Test
    fun `an address firebase rejects as invalid looks the same too`() = runTest {
        collectEvents()
        auth.sendPasswordResetResult = Result.failure(AuthError.InvalidEmail)

        send()

        assertTrue(state.sent)
        assertTrue(messages().isEmpty())
    }

    /** Side by side, so a future change cannot quietly separate the two. */
    @Test
    fun `registered and unregistered produce identical state`() = runTest {
        val registered = ForgotRequestViewModel(
            FakeAuthRepository(), Clock { now }, MutableSharedFlow()
        )
        val unregistered = ForgotRequestViewModel(
            FakeAuthRepository(sendPasswordResetResult = Result.failure(AuthError.NoSuchUser)),
            Clock { now },
            MutableSharedFlow()
        )

        registered.onSendClicked(EMAIL)
        unregistered.onSendClicked(EMAIL)
        advanceUntilIdle()

        assertEquals(registered.uiState.value, unregistered.uiState.value)
    }

    // ── Failures that are genuinely shown ─────────────────────────────────────

    /**
     * No network says nothing about the account and the request genuinely did
     * not happen, so this one is an error rather than a silent success.
     */
    @Test
    fun `no connection is reported and does not reach the sent state`() = runTest {
        collectEvents()
        auth.sendPasswordResetResult = Result.failure(AuthError.Network)

        send()

        assertEquals(listOf(ForgotRequestViewModel.MSG_NO_CONNECTION), messages())
        assertFalse(state.sent)
        assertFalse(state.isLoading)
    }

    @Test
    fun `rate limiting is reported`() = runTest {
        collectEvents()
        auth.sendPasswordResetResult = Result.failure(AuthError.TooManyRequests)

        send()

        assertEquals(listOf(ForgotRequestViewModel.MSG_TOO_MANY_ATTEMPTS), messages())
        assertFalse(state.sent)
    }

    @Test
    fun `an unmapped failure gets the generic wording`() = runTest {
        collectEvents()
        auth.sendPasswordResetResult = Result.failure(AuthError.Unknown(null))

        send()

        assertEquals(listOf(ForgotRequestViewModel.MSG_REQUEST_FAILED), messages())
    }

    // ── The resend cooldown ───────────────────────────────────────────────────

    @Test
    fun `sending starts a full cooldown and locks resend`() = runTest {
        collectEvents()

        send()

        assertEquals(
            ForgotRequestViewModel.RESEND_COOLDOWN_SECONDS,
            state.resendSecondsRemaining
        )
        assertFalse(state.canResend)
    }

    @Test
    fun `the countdown follows the injected clock`() = runTest {
        collectEvents()
        send()

        elapse(1_000)
        assertEquals(ForgotRequestViewModel.RESEND_COOLDOWN_SECONDS - 1, state.resendSecondsRemaining)

        elapse(29_000)
        assertEquals(ForgotRequestViewModel.RESEND_COOLDOWN_SECONDS - 30, state.resendSecondsRemaining)
        assertFalse(state.canResend)
    }

    /** Rounded up, so the button unlocks only once the time has genuinely passed. */
    @Test
    fun `a part-second left still reads as one second and stays locked`() = runTest {
        collectEvents()
        send()

        elapse(ForgotRequestViewModel.RESEND_COOLDOWN_MS - 1)

        assertEquals(1, state.resendSecondsRemaining)
        assertFalse(state.canResend)
    }

    @Test
    fun `resend unlocks once the cooldown has passed`() = runTest {
        collectEvents()
        send()

        elapse(ForgotRequestViewModel.RESEND_COOLDOWN_MS)

        assertEquals(0, state.resendSecondsRemaining)
        assertTrue(state.canResend)
    }

    @Test
    fun `a tap during the cooldown sends nothing`() = runTest {
        collectEvents()
        send()

        elapse(10_000)
        send()

        assertEquals(1, auth.sendPasswordResetCalls)
    }

    @Test
    fun `resending after the cooldown sends again and restarts it`() = runTest {
        collectEvents()
        send()
        elapse(ForgotRequestViewModel.RESEND_COOLDOWN_MS)

        send()

        assertEquals(2, auth.sendPasswordResetCalls)
        assertEquals(listOf(ForgotRequestViewModel.MSG_RESENT), messages())
        assertEquals(
            ForgotRequestViewModel.RESEND_COOLDOWN_SECONDS,
            state.resendSecondsRemaining
        )
        assertFalse(state.canResend)
    }

    /**
     * The resend confirmation fires on the same outcomes as the first send,
     * enumeration included, so the two cannot be told apart by whether a
     * confirmation appeared.
     */
    @Test
    fun `resending an unregistered address confirms identically`() = runTest {
        collectEvents()
        send()
        elapse(ForgotRequestViewModel.RESEND_COOLDOWN_MS)
        auth.sendPasswordResetResult = Result.failure(AuthError.NoSuchUser)

        send()

        assertEquals(listOf(ForgotRequestViewModel.MSG_RESENT), messages())
    }

    /** A late or dropped tick cannot leave the countdown permanently wrong. */
    @Test
    fun `a skipped tick does not strand the countdown`() = runTest {
        collectEvents()
        send()

        now += ForgotRequestViewModel.RESEND_COOLDOWN_MS * 3
        ticks.emit(Unit)
        advanceUntilIdle()

        assertEquals(0, state.resendSecondsRemaining)
        assertTrue(state.canResend)
    }

    private companion object {
        const val EMAIL = "alex@guardband.com"
    }
}
