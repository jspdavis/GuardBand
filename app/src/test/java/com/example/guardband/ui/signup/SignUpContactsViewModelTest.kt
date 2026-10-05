package com.example.guardband.ui.signup

import com.example.guardband.data.repository.AuthError
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric: credential validation uses `android.util.Patterns`. */
@RunWith(RobolectricTestRunner::class)
class SignUpContactsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val profiles = FakeUserProfileRepository()
    private val viewModel = SignUpContactsViewModel(auth, profiles)

    // ── Credential validation ─────────────────────────────────────────────────

    @Test
    fun `a password under 8 characters is rejected before the repository is called`() = runTest {
        submit(password = "pass123")

        assertEquals(SignUpContactsViewModel.MSG_PASSWORD_TOO_SHORT, firstMessage())
        assertEquals(0, auth.createAccountCalls)
    }

    @Test
    fun `exactly 8 characters is accepted`() = runTest {
        submit(password = "pass1234")

        assertEquals(SignUpContactsEvent.NavigateToLoadingHome, viewModel.events.first())
        assertEquals(1, auth.createAccountCalls)
    }

    @Test
    fun `a malformed email is rejected before the repository is called`() = runTest {
        submit(email = "not-an-email")

        assertEquals(SignUpContactsViewModel.MSG_EMAIL_INVALID, firstMessage())
        assertEquals(0, auth.createAccountCalls)
    }

    @Test
    fun `the name from the earlier step is forwarded to createAccount`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals(
            listOf("Alex Rivera", "alex@guardband.com", "password123"),
            auth.lastCreateAccountArgs
        )
    }

    // ── The atomic write ──────────────────────────────────────────────────────

    @Test
    fun `the profile and the contact are written in one finalize call`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals(1, profiles.finalizeSignUpCalls)
        assertEquals(0, profiles.saveProfileCalls)

        val uid = FakeAuthRepository.DEFAULT_USER.id
        assertEquals(
            Triple("Alex Rivera", FakeAuthRepository.DEFAULT_USER.email, "Cebu City"),
            profiles.saved[uid]
        )
        assertEquals("Jordan Lee", profiles.finalizedContacts[uid]!!.single().name)
    }

    @Test
    fun `the location from the earlier step reaches the write`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals("Cebu City", profiles.saved[FakeAuthRepository.DEFAULT_USER.id]!!.third)
    }

    @Test
    fun `a blank contact block is no contact rather than an error`() = runTest {
        viewModel.onSubmitClicked(
            name = "Alex Rivera",
            location = "Cebu City",
            email = "alex@guardband.com",
            password = "password123",
            contactName = "",
            contactPhone = "",
            contactRelationship = ""
        )

        assertEquals(SignUpContactsEvent.NavigateToLoadingHome, viewModel.events.first())
        assertTrue(profiles.finalizedContacts[FakeAuthRepository.DEFAULT_USER.id]!!.isEmpty())
    }

    @Test
    fun `an invalid contact phone is caught before the account is created`() = runTest {
        submit(contactPhone = "0912")

        assertEquals(SignUpContactsViewModel.MSG_CONTACT_PHONE_INVALID, firstMessage())
        // The whole point: a bad phone must never be the reason an
        // already-created account cannot be finished.
        assertEquals(0, auth.createAccountCalls)
        assertEquals(0, profiles.finalizeSignUpCalls)
    }

    @Test
    fun `a contact phone without a name is an error, not a silent drop`() = runTest {
        submit(contactName = "", contactPhone = "09171234567")

        assertEquals(SignUpContactsViewModel.MSG_CONTACT_NAME_INVALID, firstMessage())
        assertEquals(0, auth.createAccountCalls)
    }

    @Test
    fun `the contact phone is handed over as typed, for the repository to normalize`() = runTest {
        submit(contactPhone = "0917 123 4567")
        viewModel.events.first()

        assertEquals(
            "0917 123 4567",
            profiles.finalizedContacts[FakeAuthRepository.DEFAULT_USER.id]!!.single().phone
        )
    }

    // ── Account-creation failures ─────────────────────────────────────────────

    @Test
    fun `a taken email gets its own message`() = runTest {
        auth.createAccountResult = Result.failure(AuthError.EmailAlreadyInUse)
        submit()

        assertEquals(SignUpContactsViewModel.MSG_EMAIL_IN_USE, firstMessage())
    }

    @Test
    fun `an unmapped failure falls back to the generic message, never the code`() = runTest {
        auth.createAccountResult = Result.failure(AuthError.Unknown("ERROR_SOMETHING_NEW"))
        submit()

        assertEquals(SignUpContactsViewModel.MSG_SIGN_UP_FAILED, firstMessage())
    }

    @Test
    fun `a failed account creation leaves nothing to retry`() = runTest {
        auth.createAccountResult = Result.failure(AuthError.Network)
        submit()
        firstMessage()

        assertFalse(viewModel.uiState.value.accountCreated)
        assertFalse(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `a second tap while the request is in flight is ignored`() = runTest {
        submit()
        submit()

        assertEquals(SignUpContactsEvent.NavigateToLoadingHome, viewModel.events.first())
        assertEquals(1, auth.createAccountCalls)
    }

    // ── Recoverable failure after the account exists ──────────────────────────

    @Test
    fun `a failed write offers a retry instead of a dead end`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        submit()

        assertEquals(SignUpContactsViewModel.MSG_NO_CONNECTION, firstMessage())
        assertTrue(viewModel.uiState.value.accountCreated)
        assertTrue(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `retrying re-runs the write and never creates the account again`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        submit()
        firstMessage()

        profiles.finalizeSignUpResult = Result.success(Unit)
        submit()

        assertEquals(SignUpContactsEvent.NavigateToLoadingHome, viewModel.events.first())
        // The regression this design exists to prevent: a second createAccount
        // would fail as "email already in use" and strand the address.
        assertEquals(1, auth.createAccountCalls)
        assertEquals(2, profiles.finalizeSignUpCalls)
    }

    @Test
    fun `retrying skips credential validation, which the account no longer needs`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        submit()
        firstMessage()

        profiles.finalizeSignUpResult = Result.success(Unit)
        // Blank credentials would fail validation on a first submit.
        viewModel.onSubmitClicked(
            name = "Alex Rivera",
            location = "Cebu City",
            email = "",
            password = "",
            contactName = "Jordan Lee",
            contactPhone = "09171234567",
            contactRelationship = "Friend"
        )

        assertEquals(SignUpContactsEvent.NavigateToLoadingHome, viewModel.events.first())
    }

    @Test
    fun `a retry can fail again and still offer another retry`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        submit()
        firstMessage()
        submit()

        assertEquals(SignUpContactsViewModel.MSG_NO_CONNECTION, firstMessage())
        assertTrue(viewModel.uiState.value.canRetry)
        assertEquals(1, auth.createAccountCalls)
    }

    @Test
    fun `a refused write says so, and still offers a retry`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.PermissionDenied)
        submit()

        assertEquals(SignUpContactsViewModel.MSG_SAVE_REFUSED, firstMessage())
        assertTrue(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `a write failure never reports the sign-up itself as failed`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Unknown(null))
        submit()

        // The account exists, so "could not create your account" would be a lie.
        assertEquals(SignUpContactsViewModel.MSG_SAVE_FAILED, firstMessage())
    }

    // ── Complete-profile mode (first-time Google sign-in) ─────────────────────

    @Test
    fun `complete-profile mode writes the profile and never creates an account`() = runTest {
        val vm = completeProfileViewModel()

        submit(vm)
        vm.events.first()

        assertEquals(0, auth.createAccountCalls)
        assertEquals(1, profiles.finalizeSignUpCalls)
        assertEquals(
            Triple("Alex Rivera", FakeAuthRepository.DEFAULT_USER.email, "Cebu City"),
            profiles.saved[FakeAuthRepository.DEFAULT_USER.id]
        )
    }

    @Test
    fun `complete-profile mode uses the session email, not whatever was typed`() = runTest {
        val vm = completeProfileViewModel()

        vm.onSubmitClicked(
            name = "Alex Rivera",
            location = "Cebu City",
            email = "attacker@example.com",
            password = "",
            contactName = "",
            contactPhone = "",
            contactRelationship = ""
        )
        vm.events.first()

        assertEquals(
            FakeAuthRepository.DEFAULT_USER.email,
            profiles.saved[FakeAuthRepository.DEFAULT_USER.id]!!.second
        )
    }

    @Test
    fun `complete-profile mode skips credential validation entirely`() = runTest {
        val vm = completeProfileViewModel()

        // Blank email and password would fail validation in register mode.
        vm.onSubmitClicked(
            name = "Alex Rivera",
            location = "Cebu City",
            email = "",
            password = "",
            contactName = "",
            contactPhone = "",
            contactRelationship = ""
        )

        assertEquals(SignUpContactsEvent.NavigateToLoadingHome, vm.events.first())
    }

    @Test
    fun `complete-profile mode still saves the emergency contact`() = runTest {
        val vm = completeProfileViewModel()

        submit(vm)
        vm.events.first()

        assertEquals(
            "Jordan Lee",
            profiles.finalizedContacts[FakeAuthRepository.DEFAULT_USER.id]!!.single().name
        )
    }

    @Test
    fun `complete-profile mode reports a lost session instead of writing`() = runTest {
        val signedOut = FakeAuthRepository()
        val vm = SignUpContactsViewModel(signedOut, profiles)
        vm.setCompleteProfileMode(true)

        submit(vm)

        assertEquals(
            SignUpContactsViewModel.MSG_SESSION_EXPIRED,
            (vm.events.first() as SignUpContactsEvent.ShowMessage).text
        )
        assertEquals(0, profiles.finalizeSignUpCalls)
    }

    @Test
    fun `a failed write in complete-profile mode is reported and does not navigate`() = runTest {
        profiles.finalizeSignUpResult = Result.failure(AuthError.Network)
        val vm = completeProfileViewModel()

        submit(vm)

        assertEquals(
            SignUpContactsViewModel.MSG_NO_CONNECTION,
            (vm.events.first() as SignUpContactsEvent.ShowMessage).text
        )
        assertTrue(vm.uiState.value.canRetry)
    }

    @Test
    fun `the mode flag reaches the UiState so the Activity can hide the fields`() {
        val vm = completeProfileViewModel()

        assertTrue(vm.uiState.value.completeProfile)
        assertFalse(viewModel.uiState.value.completeProfile)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A signed-in session, as a first-time Google user would have. */
    private fun completeProfileViewModel(): SignUpContactsViewModel {
        val signedIn = FakeAuthRepository(signedIn = true)
        return SignUpContactsViewModel(signedIn, profiles)
            .also { it.setCompleteProfileMode(true) }
    }

    private fun submit(vm: SignUpContactsViewModel) = vm.onSubmitClicked(
        name = "Alex Rivera",
        location = "Cebu City",
        email = "alex@guardband.com",
        password = "password123",
        contactName = "Jordan Lee",
        contactPhone = "+63-917-000-0000",
        contactRelationship = "Friend"
    )

    private fun submit(
        email: String = "alex@guardband.com",
        password: String = "password123",
        contactName: String = "Jordan Lee",
        contactPhone: String = "+63-917-000-0000"
    ) = viewModel.onSubmitClicked(
        name = "Alex Rivera",
        location = "Cebu City",
        email = email,
        password = password,
        contactName = contactName,
        contactPhone = contactPhone,
        contactRelationship = "Friend"
    )

    private suspend fun firstMessage(): String =
        (viewModel.events.first() as SignUpContactsEvent.ShowMessage).text
}
