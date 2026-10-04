package com.example.guardband.ui.signup

import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeContactRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val viewModel = SignUpContactsViewModel(auth, contacts)

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
