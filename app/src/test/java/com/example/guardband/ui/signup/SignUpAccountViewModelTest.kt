package com.example.guardband.ui.signup

import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Sign-Up Step 1: the credential rules.
 *
 * Robolectric, because [com.example.guardband.utils.InputValidator.isValidEmail]
 * reaches `android.util.Patterns`.
 */
@RunWith(RobolectricTestRunner::class)
class SignUpAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = SignUpAccountViewModel()

    private suspend fun messageFor(
        email: String = "alex@guardband.com",
        password: String = "password123",
        confirm: String = "password123"
    ): String {
        viewModel.onNextClicked(email, password, confirm)
        return (viewModel.events.first() as SignUpAccountEvent.ShowMessage).text
    }

    // ── The happy path ────────────────────────────────────────────────────────

    @Test
    fun `valid credentials move to the name step`() = runTest {
        viewModel.onNextClicked("alex@guardband.com", "password123", "password123")

        val event = viewModel.events.first()

        assertEquals(
            SignUpAccountEvent.NavigateToName("alex@guardband.com", "password123"),
            event
        )
    }

    @Test
    fun `the email is trimmed before it travels`() = runTest {
        viewModel.onNextClicked("  alex@guardband.com  ", "password123", "password123")

        val event = viewModel.events.first() as SignUpAccountEvent.NavigateToName

        assertEquals("alex@guardband.com", event.email)
    }

    @Test
    fun `the password travels untrimmed, because spaces are part of it`() = runTest {
        viewModel.onNextClicked("alex@guardband.com", " pass word ", " pass word ")

        val event = viewModel.events.first() as SignUpAccountEvent.NavigateToName

        // Trimming here would create the account with a different password
        // than the one that was typed.
        assertEquals(" pass word ", event.password)
    }

    @Test
    fun `exactly the minimum length is accepted`() = runTest {
        viewModel.onNextClicked("alex@guardband.com", "12345678", "12345678")

        val event = viewModel.events.first()

        assertEquals(SignUpAccountEvent.NavigateToName("alex@guardband.com", "12345678"), event)
    }

    // ── Rejections ────────────────────────────────────────────────────────────

    @Test
    fun `a blank email is refused`() = runTest {
        assertEquals(SignUpAccountViewModel.MSG_EMAIL_REQUIRED, messageFor(email = "   "))
    }

    @Test
    fun `a malformed email is refused`() = runTest {
        assertEquals(SignUpAccountViewModel.MSG_EMAIL_INVALID, messageFor(email = "alex@"))
    }

    @Test
    fun `a blank password is refused`() = runTest {
        assertEquals(
            SignUpAccountViewModel.MSG_PASSWORD_REQUIRED,
            messageFor(password = "", confirm = "")
        )
    }

    @Test
    fun `a short password is refused`() = runTest {
        assertEquals(
            SignUpAccountViewModel.MSG_PASSWORD_TOO_SHORT,
            messageFor(password = "short12", confirm = "short12")
        )
    }

    @Test
    fun `a mismatched confirmation is refused`() = runTest {
        assertEquals(
            SignUpAccountViewModel.MSG_PASSWORDS_DIFFER,
            messageFor(confirm = "password124")
        )
    }

    @Test
    fun `the confirmation is compared case-sensitively`() = runTest {
        assertEquals(
            SignUpAccountViewModel.MSG_PASSWORDS_DIFFER,
            messageFor(password = "Password123", confirm = "password123")
        )
    }

    @Test
    fun `the email is checked before the password`() = runTest {
        // Both are wrong; the message names the first field the user would fix.
        assertEquals(
            SignUpAccountViewModel.MSG_EMAIL_INVALID,
            messageFor(email = "nope", password = "x", confirm = "y")
        )
    }
}
