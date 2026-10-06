package com.example.guardband.ui.signup

import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Sign-Up Step 2: the name rules and the credential pass-through.
 *
 * No Robolectric: nothing here reaches `android.util.Patterns` — the name
 * rules are pure Kotlin, and the email is never validated on this screen.
 */
class SignUpNameViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = SignUpNameViewModel()

    private fun next(
        firstName: String = "Alex",
        lastName: String = "Rivera",
        email: String = "alex@guardband.com",
        password: String = "password123"
    ) = viewModel.onNextClicked(firstName, lastName, email, password)

    @Test
    fun `both names are joined into the stored name`() = runTest {
        next()

        val event = viewModel.events.first() as SignUpNameEvent.NavigateToContacts

        assertEquals("Alex Rivera", event.name)
    }

    @Test
    fun `each part is trimmed before joining`() = runTest {
        next(firstName = "  Alex  ", lastName = "  Rivera  ")

        val event = viewModel.events.first() as SignUpNameEvent.NavigateToContacts

        assertEquals("Alex Rivera", event.name)
    }

    @Test
    fun `the credentials pass through untouched`() = runTest {
        next(email = "  spaced@guardband.com  ", password = " pass word ")

        val event = viewModel.events.first() as SignUpNameEvent.NavigateToContacts

        // This screen neither trims nor validates them; step 1 already did.
        assertEquals("  spaced@guardband.com  ", event.email)
        assertEquals(" pass word ", event.password)
    }

    @Test
    fun `a blank first name is refused`() = runTest {
        next(firstName = "   ")

        assertEquals(
            SignUpNameViewModel.MSG_FIRST_NAME_REQUIRED,
            (viewModel.events.first() as SignUpNameEvent.ShowMessage).text
        )
    }

    @Test
    fun `a blank last name is refused`() = runTest {
        next(lastName = "")

        assertEquals(
            SignUpNameViewModel.MSG_LAST_NAME_REQUIRED,
            (viewModel.events.first() as SignUpNameEvent.ShowMessage).text
        )
    }

    @Test
    fun `the length limit applies to the joined name, not to either half`() = runTest {
        // Each half fits on its own; together they do not.
        val half = "a".repeat(InputValidator.MAX_CONTACT_NAME_LENGTH - 10)
        next(firstName = half, lastName = half)

        assertEquals(
            SignUpNameViewModel.MSG_NAME_TOO_LONG,
            (viewModel.events.first() as SignUpNameEvent.ShowMessage).text
        )
    }

    @Test
    fun `fullName is the single place the two parts combine`() {
        assertEquals("Alex Rivera", SignUpNameViewModel.fullName(" Alex ", " Rivera "))
    }

    @Test
    fun `the Google shortcut hands over to Login`() = runTest {
        viewModel.onGoogleSignInClicked()

        assertEquals(SignUpNameEvent.NavigateToGoogleSignIn, viewModel.events.first())
    }
}
