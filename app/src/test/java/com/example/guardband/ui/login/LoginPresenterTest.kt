package com.example.guardband.ui.login

import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bug condition exploration tests for LoginPresenter (Task 1 — EXPECTED TO FAIL on unfixed code).
 *
 * These tests document the BUG conditions. They MUST FAIL before the fix is applied
 * (failure confirms the bugs exist). They WILL PASS after Bug 1 is fixed in task 3.
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 */
class LoginPresenterTest {

    // ── Stub view ────────────────────────────────────────────────────────────

    private class FakeLoginView : LoginContract.View {
        val errors = mutableListOf<String>()
        var loadingShown = false
        var loadingHidden = false
        var navigatedToMain = false
        var navigatedToSignUp = false
        var googleSignInLaunched = false

        override fun showError(message: String) {
            errors += message
        }
        override fun showLoading() { loadingShown = true }
        override fun hideLoading() { loadingHidden = true }
        override fun navigateToMain() { navigatedToMain = true }
        override fun navigateToSignUp() { navigatedToSignUp = true }
        override fun navigateToSignUpLocation(user: User) {}
        override fun navigateToForgotPassword() {}
        override fun launchGoogleSignIn() { googleSignInLaunched = true }
        override fun showIdentifierError(message: String?) {
            if (message != null) errors += message
        }
    }

    // ── Stub repository ───────────────────────────────────────────────────────

    /**
     * A Firebase-free stub of AuthRepository.
     * Constructed with nulls to avoid any Firebase initialisation in unit tests.
     * Tracks whether login() was called and with what arguments.
     */
    private class StubAuthRepository : AuthRepository(
        auth = null as com.google.firebase.auth.FirebaseAuth?,
        db = null as com.google.firebase.firestore.FirebaseFirestore?,
        functions = null as com.google.firebase.functions.FirebaseFunctions?
    ) {
        var loginCalled = false
        var loginIdentifier: String? = null

        override fun login(
            identifier: String,
            password: String,
            onSuccess: (User) -> Unit,
            onError: (String) -> Unit
        ) {
            loginCalled = true
            loginIdentifier = identifier
            // Do nothing else — no real Firebase call
        }

        override fun isLoggedIn(): Boolean = false
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private lateinit var fakeView: FakeLoginView
    private lateinit var stubRepo: StubAuthRepository
    private lateinit var presenter: LoginPresenter

    @Before
    fun setUp() {
        fakeView = FakeLoginView()
        stubRepo = StubAuthRepository()
        presenter = LoginPresenter(authRepository = stubRepo)
        presenter.attachView(fakeView)
    }

    // ── Bug 1 exploration tests (EXPECTED TO FAIL on unfixed code) ────────────

    /**
     * Test A1 — Malformed phone number (+123 is too short for the valid +?[0-9]{7,15} pattern).
     *
     * Expected after fix:
     *   - showError called with "Invalid phone number format."
     *   - authRepository.login() NOT called
     *
     * On UNFIXED code this test FAILS because LoginPresenter passes the identifier
     * straight to Firebase, which surfaces a raw "Email is badly formatted." error instead,
     * AND still calls authRepository.login().
     *
     * Validates: Requirements 1.1, 2.1
     */
    @Test
    fun `A1 - malformed phone number shows specific error and does not call login`() {
        presenter.onLoginClicked("+123", "Secret1!")

        // Assert: a user-friendly error was shown
        assertTrue(
            "Expected showError to be called with a phone-format message, but errors were: $fakeView.errors",
            fakeView.errors.isNotEmpty()
        )
        assertTrue(
            "Expected error to mention phone format, got: ${fakeView.errors}",
            fakeView.errors.any { it.contains("phone", ignoreCase = true) }
        )

        // Assert: login() must NOT have been called with an invalid identifier
        assertFalse(
            "authRepository.login() should NOT be called for malformed phone '+123', but it was called",
            stubRepo.loginCalled
        )
    }

    /**
     * Test A2 — Identifier is neither a phone-like nor email-like string ("abc").
     *
     * Expected after fix:
     *   - showError called with "Please enter a valid phone number or email."
     *   - authRepository.login() NOT called
     *
     * On UNFIXED code this test FAILS because the presenter passes "abc" to Firebase,
     * which errors opaquely, AND still calls authRepository.login().
     *
     * Validates: Requirements 1.3, 2.5
     */
    @Test
    fun `A2 - unrecognized identifier shows clear error and does not call login`() {
        presenter.onLoginClicked("abc", "Secret1!")

        assertTrue(
            "Expected showError to be called, but errors list was empty",
            fakeView.errors.isNotEmpty()
        )
        assertTrue(
            "Expected error to guide the user toward phone or email format, got: ${fakeView.errors}",
            fakeView.errors.any {
                it.contains("phone", ignoreCase = true) || it.contains("email", ignoreCase = true)
            }
        )

        assertFalse(
            "authRepository.login() should NOT be called for unrecognized identifier 'abc', but it was",
            stubRepo.loginCalled
        )
    }

    /**
     * Test A3 — Malformed email (contains @ but is syntactically invalid).
     *
     * Expected after fix:
     *   - showError called with "Invalid email format."
     *   - authRepository.login() NOT called
     *
     * On UNFIXED code this test FAILS because the presenter passes "notanemail@" to Firebase
     * and surfaces an opaque Firebase error instead of a user-friendly message.
     *
     * Validates: Requirements 1.2, 2.3
     */
    @Test
    fun `A3 - malformed email shows specific error and does not call login`() {
        presenter.onLoginClicked("notanemail@", "Secret1!")

        assertTrue(
            "Expected showError to be called, but errors list was empty",
            fakeView.errors.isNotEmpty()
        )
        assertTrue(
            "Expected error to mention email format, got: ${fakeView.errors}",
            fakeView.errors.any { it.contains("email", ignoreCase = true) }
        )

        assertFalse(
            "authRepository.login() should NOT be called for malformed email 'notanemail@', but it was",
            stubRepo.loginCalled
        )
    }

    // ── Preservation sanity checks (EXPECTED TO PASS on both unfixed and fixed code) ──

    /**
     * Preservation: blank identifier still shows "Phone number or email is required."
     * This behavior already exists and must not regress.
     */
    @Test
    fun `blank identifier shows required error`() {
        presenter.onLoginClicked("", "Secret1!")

        assertTrue("Expected error for blank identifier", fakeView.errors.isNotEmpty())
        assertFalse("login() must not be called for blank identifier", stubRepo.loginCalled)
    }

    /**
     * Preservation: blank password still shows "Password is required."
     */
    @Test
    fun `blank password shows required error`() {
        presenter.onLoginClicked("user@example.com", "")

        assertTrue("Expected error for blank password", fakeView.errors.isNotEmpty())
        assertFalse("login() must not be called for blank password", stubRepo.loginCalled)
    }
}
