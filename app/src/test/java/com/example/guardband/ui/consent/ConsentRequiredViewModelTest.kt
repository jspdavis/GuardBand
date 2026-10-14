package com.example.guardband.ui.consent

import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.utils.HostedPages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The consent screen at the gate: Agree writes the same record as sign-up
 * for the live session and only then continues; Decline signs out and writes
 * nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsentRequiredViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(signedIn = true)
    private val profiles = FakeUserProfileRepository()
    private val uid = FakeAuthRepository.DEFAULT_USER.id

    private val viewModel by lazy { ConsentRequiredViewModel(auth, profiles) }

    /** Collects every event the screen sends from here on. */
    private fun TestScope.events(): List<ConsentRequiredEvent> {
        val seen = mutableListOf<ConsentRequiredEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { seen += it }
        }
        return seen
    }

    private fun TestScope.agree() {
        viewModel.onConsentChanged(true)
        viewModel.onAgreeClicked()
        advanceUntilIdle()
    }

    // ── Agree ─────────────────────────────────────────────────────────────────

    @Test
    fun `agree records the current version for the signed-in user, then continues`() = runTest {
        val seen = events()

        agree()

        assertEquals(1, profiles.recordConsentCalls)
        assertEquals(UserProfileRepository.CONSENT_VERSION, profiles.recordedConsent[uid])
        assertEquals(listOf<ConsentRequiredEvent>(ConsentRequiredEvent.ContinueToHome), seen)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    /** Consent only: the profile and the contacts are not touched. */
    @Test
    fun `agree writes nothing but the consent record`() = runTest {
        agree()

        assertEquals(0, profiles.saveProfileCalls)
        assertEquals(0, profiles.finalizeSignUpCalls)
        assertEquals(0, profiles.markDeletionPendingCalls)
    }

    @Test
    fun `without the box ticked nothing is written`() = runTest {
        val seen = events()

        viewModel.onAgreeClicked()
        advanceUntilIdle()

        assertEquals(0, profiles.recordConsentCalls)
        assertEquals(
            listOf<ConsentRequiredEvent>(
                ConsentRequiredEvent.ShowMessage(ConsentRequiredViewModel.MSG_CONSENT_REQUIRED)
            ),
            seen
        )
    }

    @Test
    fun `unticking the box blocks agreeing again`() = runTest {
        viewModel.onConsentChanged(true)
        viewModel.onConsentChanged(false)
        viewModel.onAgreeClicked()
        advanceUntilIdle()

        assertEquals(0, profiles.recordConsentCalls)
    }

    @Test
    fun `the checkbox reaches the state so Agree can enable`() {
        assertFalse(viewModel.uiState.value.consentGiven)

        viewModel.onConsentChanged(true)

        assertTrue(viewModel.uiState.value.consentGiven)
    }

    // ── A failed write ────────────────────────────────────────────────────────

    @Test
    fun `a failed write stays on the screen and offers retry`() = runTest {
        profiles.recordConsentResult = Result.failure(AuthError.Network)
        val seen = events()

        agree()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(profiles.recordedConsent[uid])
        assertEquals(
            listOf<ConsentRequiredEvent>(
                ConsentRequiredEvent.ShowMessage(ConsentRequiredViewModel.MSG_NO_CONNECTION)
            ),
            seen
        )
    }

    @Test
    fun `a refused write is worded as refused`() = runTest {
        profiles.recordConsentResult = Result.failure(AuthError.PermissionDenied)
        val seen = events()

        agree()

        assertEquals(
            ConsentRequiredEvent.ShowMessage(ConsentRequiredViewModel.MSG_SAVE_REFUSED),
            seen.single()
        )
    }

    @Test
    fun `any other failure gets the general wording`() = runTest {
        profiles.recordConsentResult = Result.failure(AuthError.Unknown(null))
        val seen = events()

        agree()

        assertEquals(
            ConsentRequiredEvent.ShowMessage(ConsentRequiredViewModel.MSG_SAVE_FAILED),
            seen.single()
        )
    }

    @Test
    fun `retry after a failed write records consent and continues`() = runTest {
        profiles.recordConsentResult = Result.failure(AuthError.Network)
        val seen = events()
        agree()

        profiles.recordConsentResult = Result.success(Unit)
        viewModel.onAgreeClicked()
        advanceUntilIdle()

        assertEquals(2, profiles.recordConsentCalls)
        assertEquals(UserProfileRepository.CONSENT_VERSION, profiles.recordedConsent[uid])
        assertFalse(viewModel.uiState.value.saveFailed)
        assertEquals(ConsentRequiredEvent.ContinueToHome, seen.last())
    }

    /**
     * Offline, an RTDB write queues instead of failing. Without a bound the
     * spinner never ends and Decline stays disabled.
     */
    @Test
    fun `a write that never answers becomes a retry after the timeout`() = runTest {
        profiles.recordConsentHeldUntil = CompletableDeferred()
        val seen = events()
        viewModel.onConsentChanged(true)
        viewModel.onAgreeClicked()

        advanceTimeBy(ConsentRequiredViewModel.WRITE_TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)

        advanceTimeBy(2)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals(
            listOf<ConsentRequiredEvent>(
                ConsentRequiredEvent.ShowMessage(ConsentRequiredViewModel.MSG_NO_CONNECTION)
            ),
            seen
        )
    }

    /** A second tap while the write is in flight must not write twice. */
    @Test
    fun `agree is ignored while a write is in flight`() = runTest {
        viewModel.onConsentChanged(true)
        viewModel.onAgreeClicked()
        viewModel.onAgreeClicked()
        advanceUntilIdle()

        assertEquals(1, profiles.recordConsentCalls)
    }

    // ── The session ───────────────────────────────────────────────────────────

    /** The uid is the live session's; with none, nothing is written anywhere. */
    @Test
    fun `with no session nothing is written and the user goes to login`() = runTest {
        auth.forceSignOut()
        val seen = events()

        agree()

        assertEquals(0, profiles.recordConsentCalls)
        assertEquals(
            listOf(
                ConsentRequiredEvent.ShowMessage(ConsentRequiredViewModel.MSG_SESSION_EXPIRED),
                ConsentRequiredEvent.NavigateToLogin
            ),
            seen
        )
    }

    // ── Decline ───────────────────────────────────────────────────────────────

    @Test
    fun `decline signs out, writes nothing and explains`() = runTest {
        profiles.saveProfile(uid, "Alex Rivera", "alex@guardband.com")
        val savesBefore = profiles.saveProfileCalls
        val seen = events()

        viewModel.onDeclineClicked()
        advanceUntilIdle()

        assertEquals(1, auth.signOutCalls)
        assertFalse(auth.isLoggedIn())
        assertEquals(0, profiles.recordConsentCalls)
        assertNull(profiles.recordedConsent[uid])
        assertEquals(savesBefore, profiles.saveProfileCalls)
        assertEquals(0, profiles.markDeletionPendingCalls)
        assertEquals(Pair("Alex Rivera", "alex@guardband.com"), profiles.saved[uid])
        assertEquals(
            listOf<ConsentRequiredEvent>(
                ConsentRequiredEvent.Declined(ConsentRequiredViewModel.MSG_DECLINED)
            ),
            seen
        )
    }

    /** Even with the box ticked: declining is declining. */
    @Test
    fun `decline with the box ticked still writes nothing`() = runTest {
        viewModel.onConsentChanged(true)

        viewModel.onDeclineClicked()
        advanceUntilIdle()

        assertEquals(0, profiles.recordConsentCalls)
        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun `decline is ignored while an agreement is being written`() = runTest {
        viewModel.onConsentChanged(true)
        viewModel.onAgreeClicked()
        viewModel.onDeclineClicked()
        advanceUntilIdle()

        assertEquals(0, auth.signOutCalls)
        assertEquals(UserProfileRepository.CONSENT_VERSION, profiles.recordedConsent[uid])
    }

    // ── The notice ────────────────────────────────────────────────────────────

    @Test
    fun `the notice link opens the hosted notice, before agreeing`() = runTest {
        val seen = events()

        viewModel.onPrivacyNoticeClicked()

        assertEquals(
            listOf<ConsentRequiredEvent>(
                ConsentRequiredEvent.OpenPrivacyNotice(HostedPages.privacyNotice)
            ),
            seen
        )
    }

    @Test
    fun `no browser is worded, not a crash`() = runTest {
        val seen = events()

        viewModel.onPrivacyNoticeUnavailable()

        assertEquals(
            listOf<ConsentRequiredEvent>(
                ConsentRequiredEvent.ShowMessage(ConsentRequiredViewModel.MSG_NO_BROWSER)
            ),
            seen
        )
    }
}
