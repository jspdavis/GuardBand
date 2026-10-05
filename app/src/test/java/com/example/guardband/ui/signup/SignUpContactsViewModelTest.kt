package com.example.guardband.ui.signup

import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeContactRepository
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

/** Robolectric: validation uses `android.util.Patterns`. */
@RunWith(RobolectricTestRunner::class)
class SignUpContactsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val contacts = FakeContactRepository()
    private val profiles = FakeUserProfileRepository()
    private val viewModel = SignUpContactsViewModel(auth, contacts, profiles)

    @Test
    fun `a password under 8 characters is rejected before the repository is called`() = runTest {
        submit(password = "pass123")

        assertEquals(SignUpContactsViewModel.MSG_PASSWORD_TOO_SHORT, firstMessage())
        assertEquals(0, auth.registerCalls)
    }

    @Test
    fun `exactly 8 characters is accepted`() = runTest {
        submit(password = "pass1234")

        assertEquals(SignUpContactsEvent.NavigateToLoadingHome, viewModel.events.first())
        assertEquals(1, auth.registerCalls)
    }

    @Test
    fun `a malformed email is rejected before the repository is called`() = runTest {
        submit(email = "not-an-email")

        assertEquals(SignUpContactsViewModel.MSG_EMAIL_INVALID, firstMessage())
        assertEquals(0, auth.registerCalls)
    }

    @Test
    fun `name and location from the earlier steps are forwarded to register`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals(
            listOf("Alex Rivera", "Cebu City", "alex@guardband.com", "password123"),
            auth.lastRegisterArgs
        )
    }

    @Test
    fun `the emergency contact is saved once the account exists`() = runTest {
        submit()
        viewModel.events.first()

        assertEquals(1, contacts.added.size)
        assertEquals("Jordan Lee", contacts.added.single().name)
    }

    @Test
    fun `a taken email gets its own message`() = runTest {
        auth.registerResult = Result.failure(AuthError.EmailAlreadyInUse)
        submit()

        assertEquals(SignUpContactsViewModel.MSG_EMAIL_IN_USE, firstMessage())
    }

    @Test
    fun `an unmapped failure falls back to the generic message, never the code`() = runTest {
        auth.registerResult = Result.failure(AuthError.Unknown("ERROR_SOMETHING_NEW"))
        submit()

        assertEquals(SignUpContactsViewModel.MSG_SIGN_UP_FAILED, firstMessage())
    }

    @Test
    fun `a second tap while the request is in flight is ignored`() = runTest {
        submit()
        submit()

        assertEquals(SignUpContactsEvent.NavigateToLoadingHome, viewModel.events.first())
        assertEquals(1, auth.registerCalls)
    }

    // -- Complete-profile mode (first-time Google sign-in) --------------------

    @Test
    fun `complete-profile mode saves the profile and never registers`() = runTest {
        val vm = completeProfileViewModel()

        submit(vm)
        vm.events.first()

        assertEquals(0, auth.registerCalls)
        assertEquals(1, profiles.saveProfileCalls)
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

        assertEquals(1, contacts.added.size)
        assertEquals("Jordan Lee", contacts.added.single().name)
    }

    @Test
    fun `complete-profile mode reports a lost session instead of writing`() = runTest {
        val signedOut = FakeAuthRepository()
        val vm = SignUpContactsViewModel(signedOut, contacts, profiles)
        vm.setCompleteProfileMode(true)

        submit(vm)

        assertEquals(
            SignUpContactsViewModel.MSG_SESSION_EXPIRED,
            (vm.events.first() as SignUpContactsEvent.ShowMessage).text
        )
        assertEquals(0, profiles.saveProfileCalls)
    }

    @Test
    fun `a failed profile write is reported and does not navigate`() = runTest {
        profiles.saveProfileResult = Result.failure(AuthError.Network)
        val vm = completeProfileViewModel()

        submit(vm)

        assertEquals(
            SignUpContactsViewModel.MSG_NO_CONNECTION,
            (vm.events.first() as SignUpContactsEvent.ShowMessage).text
        )
    }

    @Test
    fun `the mode flag reaches the UiState so the Activity can hide the fields`() {
        val vm = completeProfileViewModel()

        assertTrue(vm.uiState.value.completeProfile)
        assertFalse(viewModel.uiState.value.completeProfile)
    }

    /** A signed-in session, as a first-time Google user would have. */
    private fun completeProfileViewModel(): SignUpContactsViewModel {
        val signedIn = FakeAuthRepository(signedIn = true)
        return SignUpContactsViewModel(signedIn, contacts, profiles)
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
        password: String = "password123"
    ) = viewModel.onSubmitClicked(
        name = "Alex Rivera",
        location = "Cebu City",
        email = email,
        password = password,
        contactName = "Jordan Lee",
        contactPhone = "+63-917-000-0000",
        contactRelationship = "Friend"
    )

    private suspend fun firstMessage(): String =
        (viewModel.events.first() as SignUpContactsEvent.ShowMessage).text
}
