package com.example.guardband.ui.home

import com.example.guardband.data.model.ConsentRecord
import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Home host's gate: no session goes to Login, an unverified password
 * account to the verification gate, an unfinished deletion to finish it, and
 * an account with no consent record to the consent screen - all before the
 * tabs show. A record that cannot be read is a retry, never a way in and
 * never the consent screen.
 *
 * Every way into the app passes through here - a restored session from
 * Splash, an email login, a Google sign-in, the end of sign-up - which is why
 * these are caught here rather than in one sign-in path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeUserProfileRepository()
    private val uid = FakeAuthRepository.DEFAULT_USER.id

    /** The gate under test and every event it has sent so far. */
    private class Opened(val vm: HomeViewModel, val events: List<HomeEvent>) {
        val gate: HomeGateState get() = vm.gate.value
    }

    /**
     * Builds the gate and lets it finish. A verified password account unless
     * a test says otherwise, so the record tests are not stopped at the
     * verification gate first.
     */
    private fun TestScope.open(
        signedIn: Boolean = true,
        verified: Boolean = true,
        providers: Set<SignInProvider> = setOf(SignInProvider.PASSWORD),
        user: User = FakeAuthRepository.DEFAULT_USER,
        settle: Boolean = true
    ): Opened {
        val auth = FakeAuthRepository(
            signedIn = signedIn,
            emailVerified = verified,
            providers = providers,
            user = user
        )
        val vm = HomeViewModel(auth, profiles)
        val seen = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { seen += it }
        }
        if (settle) advanceUntilIdle() else runCurrent()
        return Opened(vm, seen)
    }

    /** A profile with a consent record at [version], as sign-up leaves one. */
    private suspend fun agreed(version: String, forUid: String = uid) {
        profiles.finalizeSignUp(forUid, "Alex Rivera", "alex@guardband.com", emptyList(), version)
    }

    // ── Session ───────────────────────────────────────────────────────────────

    @Test
    fun `no session goes to login without reading the profile`() = runTest {
        val opened = open(signedIn = false)

        assertEquals(listOf<HomeEvent>(HomeEvent.NavigateToLogin), opened.events)
        assertEquals(HomeGateState.CHECKING, opened.gate)
        assertEquals(0, profiles.profileStateCalls)
        assertEquals(0, profiles.consentRecordCalls)
    }

    // ── Consent present ───────────────────────────────────────────────────────

    /** Agreed before the notice was reworded: not re-prompted. */
    @Test
    fun `consent at 1_0 lets the user straight in`() = runTest {
        agreed("1.0")

        val opened = open()

        assertTrue(opened.events.isEmpty())
        assertEquals(HomeGateState.OPEN, opened.gate)
    }

    @Test
    fun `consent at 1_1 lets the user straight in`() = runTest {
        agreed("1.1")

        val opened = open()

        assertTrue(opened.events.isEmpty())
        assertEquals(HomeGateState.OPEN, opened.gate)
    }

    @Test
    fun `an account with consent is never rewritten by the gate`() = runTest {
        agreed("1.0")
        val finalizeCallsBefore = profiles.finalizeSignUpCalls

        open()

        assertEquals(0, profiles.recordConsentCalls)
        assertEquals(0, profiles.saveProfileCalls)
        assertEquals(finalizeCallsBefore, profiles.finalizeSignUpCalls)
        assertEquals("1.0", profiles.recordedConsent[uid])
    }

    /**
     * Sign-up's own write stored the consent, so the user who has just
     * finished the wizard walks through: no flag, no second consent step.
     */
    @Test
    fun `a user who just finished sign-up is not asked again`() = runTest {
        profiles.finalizeSignUp(
            uid, "Alex Rivera", "alex@guardband.com", emptyList(),
            UserProfileRepository.CONSENT_VERSION
        )

        val opened = open()

        assertTrue(opened.events.isEmpty())
        assertEquals(HomeGateState.OPEN, opened.gate)
    }

    @Test
    fun `a pinned consent record is honoured`() = runTest {
        profiles.consentRecordResult = Result.success(ConsentRecord(version = "1.1"))

        assertEquals(HomeGateState.OPEN, open().gate)
    }

    // ── Consent absent ────────────────────────────────────────────────────────

    /** The Console-made account and the missing Google record, after provisioning. */
    @Test
    fun `a profile with no consent record goes to the consent screen`() = runTest {
        profiles.saveProfile(uid, "Alex Rivera", "alex@guardband.com")

        val opened = open()

        assertEquals(listOf<HomeEvent>(HomeEvent.RequireConsent), opened.events)
        assertEquals(HomeGateState.CHECKING, opened.gate)
    }

    /** No record at all (provisioning's write failed): no consent either. */
    @Test
    fun `an account with no record yet goes to the consent screen`() = runTest {
        val opened = open()

        assertEquals(listOf<HomeEvent>(HomeEvent.RequireConsent), opened.events)
        assertEquals(HomeGateState.CHECKING, opened.gate)
    }

    @Test
    fun `the gate itself writes nothing`() = runTest {
        profiles.saveProfile(uid, "Alex Rivera", "alex@guardband.com")
        val savesBefore = profiles.saveProfileCalls

        open()

        assertEquals(0, profiles.recordConsentCalls)
        assertEquals(savesBefore, profiles.saveProfileCalls)
        assertEquals(0, profiles.finalizeSignUpCalls)
    }

    /** One account's consent says nothing about the account now signed in. */
    @Test
    fun `switching to a different account checks that account's own record`() = runTest {
        agreed(UserProfileRepository.CONSENT_VERSION)
        val other = User(id = "uid-2", name = "Sam Cruz", email = "sam@guardband.com")

        val opened = open(user = other)

        assertEquals(listOf<HomeEvent>(HomeEvent.RequireConsent), opened.events)
        assertEquals(HomeGateState.CHECKING, opened.gate)
    }

    @Test
    fun `switching to a different account that has agreed lets it in`() = runTest {
        profiles.saveProfile(uid, "Alex Rivera", "alex@guardband.com")
        agreed("1.0", forUid = "uid-2")
        val other = User(id = "uid-2", name = "Sam Cruz", email = "sam@guardband.com")

        assertEquals(HomeGateState.OPEN, open(user = other).gate)
    }

    // ── Read failed ───────────────────────────────────────────────────────────

    /** Offline or refused: a retry, not the app and not the consent screen. */
    @Test
    fun `a failed consent read offers retry, never the consent screen`() = runTest {
        profiles.consentRecordResult = Result.failure(AuthError.Network)

        val opened = open()

        assertTrue(opened.events.isEmpty())
        assertEquals(HomeGateState.FAILED, opened.gate)
    }

    /**
     * Until 2026-10-14 a failed marker read let the user in. Now it cannot:
     * "could not tell" would open the app to an account with no consent.
     */
    @Test
    fun `a failed marker read offers retry rather than letting the user in`() = runTest {
        profiles.profileStateResult = Result.failure(AuthError.Network)

        val opened = open()

        assertTrue(opened.events.isEmpty())
        assertEquals(HomeGateState.FAILED, opened.gate)
        assertEquals(0, profiles.consentRecordCalls)
    }

    @Test
    fun `a refused read offers retry`() = runTest {
        profiles.consentRecordResult = Result.failure(AuthError.PermissionDenied)

        assertEquals(HomeGateState.FAILED, open().gate)
    }

    /** An RTDB read made offline does not fail; it waits. The timeout ends it. */
    @Test
    fun `a read that never answers becomes a retry after the timeout`() = runTest {
        profiles.readsHeldUntil = CompletableDeferred()

        val opened = open(settle = false)
        advanceTimeBy(HomeViewModel.GATE_READ_TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(HomeGateState.CHECKING, opened.gate)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(HomeGateState.FAILED, opened.gate)
        assertTrue(opened.events.isEmpty())
    }

    @Test
    fun `retry after a failure lets a consenting user in`() = runTest {
        agreed("1.0")
        profiles.consentRecordResult = Result.failure(AuthError.Network)
        val opened = open()
        assertEquals(HomeGateState.FAILED, opened.gate)

        profiles.consentRecordResult = null
        opened.vm.onRetryClicked()
        advanceUntilIdle()

        assertEquals(HomeGateState.OPEN, opened.gate)
        assertTrue(opened.events.isEmpty())
    }

    @Test
    fun `retry after a failure can still find no consent`() = runTest {
        profiles.consentRecordResult = Result.failure(AuthError.Network)
        val opened = open()

        profiles.consentRecordResult = null
        opened.vm.onRetryClicked()
        advanceUntilIdle()

        assertEquals(listOf<HomeEvent>(HomeEvent.RequireConsent), opened.events)
    }

    @Test
    fun `retry is ignored unless the read failed`() = runTest {
        agreed("1.0")
        val opened = open()
        val readsBefore = profiles.consentRecordCalls

        opened.vm.onRetryClicked()
        advanceUntilIdle()

        assertEquals(readsBefore, profiles.consentRecordCalls)
        assertEquals(HomeGateState.OPEN, opened.gate)
    }

    // ── Still loading ─────────────────────────────────────────────────────────

    /** No flash of the tabs, and none of the consent screen, mid-read. */
    @Test
    fun `while the record is loading the tabs stay covered and nothing navigates`() = runTest {
        val held = CompletableDeferred<Unit>()
        profiles.readsHeldUntil = held

        val opened = open(settle = false)

        assertEquals(HomeGateState.CHECKING, opened.gate)
        assertTrue(opened.events.isEmpty())

        held.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf<HomeEvent>(HomeEvent.RequireConsent), opened.events)
    }

    // ── Pending deletion ──────────────────────────────────────────────────────

    /** Profile removed, Auth delete failed, user gave up: back to finish it. */
    @Test
    fun `an unfinished deletion is resumed instead of opening the tabs`() = runTest {
        profiles.pendingDeletion += uid

        val opened = open()

        assertEquals(listOf<HomeEvent>(HomeEvent.ResumeDeletion), opened.events)
        assertEquals(HomeGateState.CHECKING, opened.gate)
    }

    /** The marker has no consent under it; deletion still wins. */
    @Test
    fun `a pending deletion wins over missing consent`() = runTest {
        profiles.pendingDeletion += uid

        assertEquals(listOf<HomeEvent>(HomeEvent.ResumeDeletion), open().events)
        assertEquals(0, profiles.consentRecordCalls)
    }

    @Test
    fun `another account's marker does not redirect this one`() = runTest {
        agreed("1.0")
        profiles.pendingDeletion += "uid-somebody-else"

        val opened = open()

        assertTrue(opened.events.isEmpty())
        assertEquals(HomeGateState.OPEN, opened.gate)
    }

    @Test
    fun `a pinned pending state is honoured`() = runTest {
        profiles.profileStateResult = Result.success(ProfileState.DELETION_PENDING)

        assertEquals(listOf<HomeEvent>(HomeEvent.ResumeDeletion), open().events)
    }

    // ── The verification gate ─────────────────────────────────────────────────

    @Test
    fun `an unverified password account is sent to verify, not into the tabs`() = runTest {
        val opened = open(verified = false)

        assertEquals(listOf<HomeEvent>(HomeEvent.RequireEmailVerification), opened.events)
        assertEquals(HomeGateState.CHECKING, opened.gate)
    }

    /** Google proved the address; there is no inbox step to wait for. */
    @Test
    fun `a google account is never gated on verification`() = runTest {
        agreed("1.0")

        val opened = open(verified = false, providers = setOf(SignInProvider.GOOGLE))

        assertTrue(opened.events.isEmpty())
        assertEquals(HomeGateState.OPEN, opened.gate)
    }

    /** No flow could verify it, so locking it out would strand it. */
    @Test
    fun `an account with no known provider is not gated on verification`() = runTest {
        agreed("1.0")

        assertTrue(open(verified = false, providers = emptySet()).events.isEmpty())
    }

    /**
     * The gate is synchronous and comes first. The record reads are skipped;
     * the gate screen's "Delete this account" reaches Account information,
     * which resumes a pending deletion itself.
     */
    @Test
    fun `the verification gate comes before the record checks`() = runTest {
        profiles.pendingDeletion += uid

        assertEquals(
            listOf<HomeEvent>(HomeEvent.RequireEmailVerification),
            open(verified = false).events
        )
        assertEquals(0, profiles.profileStateCalls)
        assertEquals(0, profiles.consentRecordCalls)
    }

    @Test
    fun `a verified account with a pending deletion still resumes it`() = runTest {
        profiles.pendingDeletion += uid

        assertEquals(listOf<HomeEvent>(HomeEvent.ResumeDeletion), open(verified = true).events)
    }
}
