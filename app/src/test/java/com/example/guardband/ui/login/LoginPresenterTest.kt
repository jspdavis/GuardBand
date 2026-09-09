package com.example.guardband.ui.login

import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * Bug condition exploration tests for LoginPresenter (Task 1).
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 *
 * IMPORTANT: Tests A1–A3 are EXPECTED TO FAIL on unfixed code.
 * Failure confirms Bug 1 exists (no identifier format validation in LoginPresenter).
 */
class LoginPresenterTest {

    private lateinit var view: LoginContract.View
    private lateinit var authRepo: AuthRepository
    private lateinit var presenter: LoginPresenter

    @Before
    fun setUp() {
        view = mock()
        authRepo = mock()
        presenter = LoginPresenter(authRepository = authRepo)
        presenter.attachView(view)
    }

    // ── Bug 1: Malformed phone number (starts with +) ─────────────────────────

    /**
     * Test A1 — malformed phone identifier (+123 is too short to be valid).
     *
     * Validates: Requirements 1.1, 2.1
     *
     * EXPECTED TO FAIL on unfixed code (Bug 1 — LoginPresenter passes raw
     * phone-like identifier directly to authRepository.login, surfacing a
     * Firebase "Email is badly formatted." error instead of a user-friendly
     * validation message).
     *
     * Failure proves bug exists.
     */
    @Test
    fun `A1 - malformed phone plus123 shows error and does not call login`() {
        presenter.onLoginClicked("+123", "Secret1!")

        // Assert a user-friendly error was shown
        val captor = argumentCaptor<String>()
        verify(view).showError(captor.capture())
        val errorMsg = captor.firstValue.lowercase()
        assertTrue(
            "Expected error to mention 'phone', 'format', or 'invalid' but got: '${captor.firstValue}'",
            errorMsg.contains("phone") || errorMsg.contains("format") || errorMsg.contains("invalid")
        )

        // Assert authRepository.login was NOT called
        verify(authRepo, never()).login(any(), any(), any(), any())
    }

    // ── Bug 1: Unrecognized identifier (neither phone nor email) ──────────────

    /**
     * Test A2 — identifier "abc" is neither a valid phone nor email.
     *
     * Validates: Requirements 1.3, 2.5
     *
     * EXPECTED TO FAIL on unfixed code (Bug 1 — presenter passes "abc" to
     * Firebase which returns an opaque error instead of the user-friendly
     * "Please enter a valid phone number or email.").
     *
     * Failure proves bug exists.
     */
    @Test
    fun `A2 - non-phone non-email identifier abc shows error and does not call login`() {
        presenter.onLoginClicked("abc", "Secret1!")

        val captor = argumentCaptor<String>()
        verify(view).showError(captor.capture())
        val errorMsg = captor.firstValue.lowercase()
        assertTrue(
            "Expected error to mention 'valid', 'phone', or 'email' but got: '${captor.firstValue}'",
            errorMsg.contains("valid") || errorMsg.contains("phone") || errorMsg.contains("email")
        )

        verify(authRepo, never()).login(any(), any(), any(), any())
    }

    // ── Bug 1: Malformed email (contains @ but invalid format) ────────────────

    /**
     * Test A3 — identifier "notanemail@" has an @ but is not a valid email.
     *
     * Validates: Requirements 1.2, 2.3
     *
     * EXPECTED TO FAIL on unfixed code (Bug 1 — presenter passes the malformed
     * email to Firebase which surfaces an opaque error instead of the
     * user-friendly "Invalid email format.").
     *
     * Failure proves bug exists.
     */
    @Test
    fun `A3 - malformed email notanemail@ shows error and does not call login`() {
        presenter.onLoginClicked("notanemail@", "Secret1!")

        val captor = argumentCaptor<String>()
        verify(view).showError(captor.capture())
        val errorMsg = captor.firstValue.lowercase()
        assertTrue(
            "Expected error to mention 'email', 'format', or 'invalid' but got: '${captor.firstValue}'",
            errorMsg.contains("email") || errorMsg.contains("format") || errorMsg.contains("invalid")
        )

        verify(authRepo, never()).login(any(), any(), any(), any())
    }
}
