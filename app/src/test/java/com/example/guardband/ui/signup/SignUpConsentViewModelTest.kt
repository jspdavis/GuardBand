package com.example.guardband.ui.signup

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The commit: account creation, the atomic write, the retry split, and
 * complete-profile mode.
 *
 * This logic used to live on the contacts step; it moved here when Consent
 * became the last screen. Credential *shape* is no longer checked here —
 * [SignUpAccountViewModelTest] owns that, because step 1 is where it is typed.
 *
 * No Robolectric: nothing here reaches `android.util.Patterns` any more.
 */
class SignUpConsentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val profiles = FakeUserProfileRepository()
    private val viewModel = SignUpConsentViewModel(auth, profiles)

    private val contacts = listOf(
        EmergencyContact(id = "draft-1", name = "Jordan Lee", phone = "+63-917-000-0000", relationship = "Friend")
    )

    /** Consent given, then submit — the ordinary path through the screen. */
    private fun submit(
        vm: SignUpConsentViewModel = viewModel,
        name: String = "Alex Rivera",
        email: String = "alex@guardband.com",
        password: String = "password123",
        staged: List<EmergencyContact> = contacts,
        completeProfile: Boolean = false
    ) {
        vm.onConsentChanged(true)
        vm.onCreateAccountClicked(name, email, password, staged, completeProfile)
    }

    private suspend fun firstMessage(vm: SignUpConsentViewModel = viewModel) =
        (vm.events.first() as SignUpConsentEvent.ShowMessage).text

    // ── Consent gates the commit ──────────────────────────────────────────────

    @Test
    fun `without consent nothing is created`() = runTest {
        viewModel.onCreateAccountClicked("Alex Rivera", "alex@guardband.com", "password123", contacts, false)

        assertEquals(SignUpConsentViewModel.MSG_CONSENT_REQUIRED, firstMessage())
        assertEquals(0, auth.createAccountCalls)
        assertEquals(0, profiles.finalizeSignUpCalls)
    }

    @Test
    fun `the checkbox reaches the state so the button can enable`() {
        assertFalse(viewModel.uiState.value.consentGiven)

        viewModel.onConsentChanged(true)

        assertTrue(viewModel.uiState.value.consentGiven)
    }

    @Test
    fun `unticking it blocks the commit again`() = runTest {
        viewModel.onConsentChanged(true)
        viewModel.onConsentChanged(false)
        viewModel.onCreateAccountClicked("Alex Rivera", "alex@guardband.com", "password123", contacts, false)

        assertEquals(SignUpConsentViewModel.MSG_CONSENT_REQUIRED, firstMessage())
        assertEquals(0, auth.createAccountCalls)
    }

    // ── The happy path ────────────────────────────────────────────────────────

    @Test
    fun `the wizard's values are forwarded to createAccount`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals(
            listOf("Alex Rivera", "alex@guardband.com", "password123"),
            auth.lastCreateAccountArgs
        )
    }

    @Test
    fun `the profile and the contacts are written in one finalize call`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals(1, profiles.finalizeSignUpCalls)
        assertEquals(
            Pair("Alex Rivera", FakeAuthRepository.DEFAULT_USER.email),
            profiles.saved[FakeAuthRepository.DEFAULT_USER.id]
        )
        assertEquals(
            listOf("Jordan Lee"),
            profiles.finalizedContacts[FakeAuthRepository.DEFAULT_USER.id]!!.map { it.name }
        )
    }

    @Test
    fun `consent is recorded with the write, not merely collected`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals(
            UserProfileRepository.CONSENT_VERSION,
            profiles.recordedConsent[FakeAuthRepository.DEFAULT_USER.id]
        )
    }

    @Test
    fun `the contact phone is handed over as typed, for the repository to normalize`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals(
            listOf("+63-917-000-0000"),
            profiles.finalizedContacts[FakeAuthRepository.DEFAULT_USER.id]!!.map { it.phone }
        )
    }

    @Test
    fun `success navigates to Loading`() = runTest {
        submit()

        assertEquals(SignUpConsentEvent.NavigateToLoadingHome, viewModel.events.first())
    }

    // ── Account-creation failures ─────────────────────────────────────────────

    @Test
    fun `a taken email gets its own message`() = runTest {
        auth.createAccountResult = Result.failure(AuthError.EmailAlreadyInUse)
        submit()

        assertEquals(SignUpConsentViewModel.MSG_EMAIL_IN_USE, firstMessage())
    }

    @Test
    fun `a Google-owned address gets the same message, so neither provider leaks`() = runTest {
        auth.createAccountResult =
            Result.failure(AuthError.AccountExistsWithDifferentCredential)
        submit()

        assertEquals(SignUpConsentViewModel.MSG_EMAIL_IN_USE, firstMessage())
    }

    @Test
    fun `an unmapped failure falls back to the generic message, never the code`() = runTest {
        auth.createAccountResult = Result.failure(AuthError.Unknown(null))
        submit()

        assertEquals(SignUpConsentViewModel.MSG_SIGN_UP_FAILED, firstMessage())
    }

    @Test
    fun `a failed account creation leaves nothing to retry`() = runTest {
        auth.createAccountResult = Result.failure(AuthError.Network)
        submit()
        firstMessage()

        assertFalse(viewModel.uiState.value.canRetry)
        assertFalse(viewModel.uiState.value.accountCreated)
    }

    @Test
    fun `a second tap while the request is in flight is ignored`() = runTest {
        submit()
        submit()
        viewModel.events.first()

        assertEquals(1, auth.createAccountCalls)
    }

    // ── The write, and the retry split ────────────────────────────────────────

    @Test
    fun `a failed write offers a retry instead of a dead end`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        submit()
        firstMessage()

        assertTrue(viewModel.uiState.value.canRetry)
        assertTrue(viewModel.uiState.value.accountCreated)
    }

    @Test
    fun `retrying re-runs the write and never creates the account again`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        submit()
        firstMessage()

        profiles.finalizeSignUpResult = Result.success(Unit)
        submit()

        assertEquals(SignUpConsentEvent.NavigateToLoadingHome, viewModel.events.first())
        // The whole point of the split: one account, two writes.
        assertEquals(1, auth.createAccountCalls)
        assertEquals(2, profiles.finalizeSignUpCalls)
    }

    @Test
    fun `a retry can fail again and still offer another retry`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        submit()
        firstMessage()
        submit()
        firstMessage()

        assertTrue(viewModel.uiState.value.canRetry)
        assertEquals(1, auth.createAccountCalls)
    }

    @Test
    fun `a refused write says so, and still offers a retry`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.PermissionDenied)
        submit()

        assertEquals(SignUpConsentViewModel.MSG_SAVE_REFUSED, firstMessage())
        assertTrue(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `a write failure never reports the sign-up itself as failed`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        submit()

        // The account exists; telling the user sign-up failed would be a lie.
        val message = firstMessage()
        assertFalse(message == SignUpConsentViewModel.MSG_SIGN_UP_FAILED)
        assertEquals(SignUpConsentViewModel.MSG_NO_CONNECTION, message)
    }

    // ── Complete-profile mode (first-time Google) ─────────────────────────────

    private fun completeProfileViewModel() =
        SignUpConsentViewModel(FakeAuthRepository(signedIn = true), profiles)

    @Test
    fun `complete-profile mode writes the profile and never creates an account`() = runTest {
        val vm = completeProfileViewModel()
        submit(vm = vm, completeProfile = true)
        vm.events.first()

        assertEquals(0, auth.createAccountCalls)
        assertEquals(1, profiles.finalizeSignUpCalls)
    }

    @Test
    fun `complete-profile mode uses the session email, not whatever was passed`() = runTest {
        val vm = completeProfileViewModel()
        submit(vm = vm, email = "attacker@example.com", completeProfile = true)
        vm.events.first()

        assertEquals(
            FakeAuthRepository.DEFAULT_USER.email,
            profiles.saved[FakeAuthRepository.DEFAULT_USER.id]!!.second
        )
    }

    @Test
    fun `complete-profile mode still saves the emergency contacts`() = runTest {
        val vm = completeProfileViewModel()
        submit(vm = vm, completeProfile = true)
        vm.events.first()

        assertEquals(
            listOf("Jordan Lee"),
            profiles.finalizedContacts[FakeAuthRepository.DEFAULT_USER.id]!!.map { it.name }
        )
    }

    @Test
    fun `complete-profile mode still records consent`() = runTest {
        val vm = completeProfileViewModel()
        submit(vm = vm, completeProfile = true)
        vm.events.first()

        assertEquals(
            UserProfileRepository.CONSENT_VERSION,
            profiles.recordedConsent[FakeAuthRepository.DEFAULT_USER.id]
        )
    }

    @Test
    fun `complete-profile mode reports a lost session instead of writing`() = runTest {
        val vm = SignUpConsentViewModel(FakeAuthRepository(signedIn = false), profiles)
        submit(vm = vm, completeProfile = true)

        assertEquals(SignUpConsentViewModel.MSG_SESSION_EXPIRED, firstMessage(vm))
        assertEquals(0, profiles.finalizeSignUpCalls)
    }

    @Test
    fun `a failed write in complete-profile mode is reported and does not navigate`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        val vm = completeProfileViewModel()
        submit(vm = vm, completeProfile = true)

        assertEquals(SignUpConsentViewModel.MSG_NO_CONNECTION, firstMessage(vm))
        assertTrue(vm.uiState.value.canRetry)
    }
}
