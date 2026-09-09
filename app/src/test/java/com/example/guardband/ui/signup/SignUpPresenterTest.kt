package com.example.guardband.ui.signup

import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.ContactRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Bug condition exploration tests for SignUpPresenter (Task 1).
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4
 *
 * IMPORTANT: Tests B1, B2, B3, B4 are EXPECTED TO FAIL on unfixed code.
 * Failure confirms Bug 2 exists (blank-credential bypass and skip navigation).
 * C1 is expected to PASS on unfixed code (method absent = Bug 3 confirmed).
 */
class SignUpPresenterTest {

    // Track all showFieldError calls manually via a real view implementation
    private val fieldErrorsCalled = mutableListOf<Pair<String, String?>>()
    private lateinit var view: SignUpContract.View
    private lateinit var authRepo: AuthRepository
    private lateinit var contactRepo: ContactRepository
    private lateinit var presenter: SignUpPresenter

    @Before
    fun setUp() {
        fieldErrorsCalled.clear()
        // Use a hand-rolled view that records showFieldError calls
        // (avoids ArgumentCaptor<String?> nullable generic issue)
        view = object : SignUpContract.View {
            override fun showError(message: String) {}
            override fun showLoading() {}
            override fun hideLoading() {}
            override fun showStep(step: Int) {}
            override fun updateStepIndicator(step: Int) {}
            override fun renderContacts(contacts: List<com.example.guardband.data.model.EmergencyContact>) {}
            override fun clearContactForm() {}
            override fun navigateToLoading() {}
            override fun showFieldError(field: String, message: String?) {
                fieldErrorsCalled.add(Pair(field, message))
            }
        }
        authRepo = mock()
        contactRepo = mock()
        whenever(authRepo.isLoggedIn()).thenReturn(false)
        presenter = SignUpPresenter(authRepository = authRepo, contactRepository = contactRepo)
        presenter.attachView(view)
    }

    // ── Bug 2: Blank credentials should show error — NOT advance step ─────────

    /**
     * Test B1 — blank email AND blank password with valid name should show email error
     * and must NOT advance the step.
     *
     * Validates: Requirements 1.4 (Bug 2 — blank credential bypass)
     *
     * EXPECTED TO FAIL on unfixed code:
     * The current `onNameContinue` logic contains `else -> goToStep(2)` which
     * silently advances to Step 2 when both email and password are blank.
     * On unfixed code, showFieldError is only called to CLEAR firstName/lastName
     * errors (with null message); it is never called with a non-null email error.
     *
     * Failure proves bug exists.
     */
    @Test
    fun `B1 - blank email and blank password shows field error for email and does not advance step`() {
        presenter.onNameContinue("Alice", "Smith", "", "")

        // On unfixed code, only null-clearing calls happen: showFieldError("firstName", null)
        // and showFieldError("lastName", null). No showFieldError("email", <non-null>) is made.
        // The assertion below FAILS, proving Bug 2 exists.
        val emailErrorWithNonNullMsg = fieldErrorsCalled.any { (field, msg) ->
            field == "email" && msg != null
        }

        assertTrue(
            "Expected showFieldError('email', <non-null message>) for blank email+password.\n" +
                "Actual showFieldError calls: $fieldErrorsCalled\n" +
                "Bug 2: blank credentials silently advance to Step 2 without email error.",
            emailErrorWithNonNullMsg
        )

        // Assert step was NOT advanced to 2 (on unfixed code it IS advanced)
        assertEquals(
            "Step should remain 1 (not advance to 2) when email+password are blank.\n" +
                "Bug 2: presenter advanced to step ${presenter.getCurrentStep()} without credentials.",
            1, presenter.getCurrentStep()
        )

        // Assert registerWithEmail was NOT called
        verify(authRepo, never()).registerWithEmail(
            any(), any(), any(), any(), any(), any(), any()
        )
    }

    /**
     * Test B2 — blank email with non-blank password should show email error.
     *
     * Validates: Requirements 1.4 (Bug 2 — blank email alone with non-blank password)
     *
     * EXPECTED TO FAIL on unfixed code: when password is non-blank, the code enters
     * the validation block. With blank email, `trimmedEmail.isBlank()` is true
     * but the current code doesn't show a dedicated "email" field error — instead
     * it shows "Enter a valid email." via showFieldError("email", ...) OR crashes.
     *
     * If this test PASSES on unfixed code, it means the email blank check was
     * already handled partially. Document the result either way.
     */
    @Test
    fun `B2 - blank email with non-blank password shows email field error`() {
        presenter.onNameContinue("Alice", "Smith", "", "Secret1!")

        val emailErrorWithNonNullMsg = fieldErrorsCalled.any { (field, msg) ->
            field == "email" && msg != null
        }

        assertTrue(
            "Expected showFieldError('email', <non-null message>) when email is blank but " +
                "password is provided.\nActual calls: $fieldErrorsCalled",
            emailErrorWithNonNullMsg
        )

        assertEquals(
            "Step should remain 1 when email is blank. Current step: ${presenter.getCurrentStep()}",
            1, presenter.getCurrentStep()
        )
    }

    /**
     * Test B3 — blank password with valid email should show password error.
     *
     * Validates: Requirements 1.4 (Bug 2 — blank password with valid email)
     *
     * EXPECTED TO FAIL on unfixed code:
     * - Current code hits `android.util.Patterns.EMAIL_ADDRESS.matcher(email)` which
     *   throws NullPointerException in JVM unit tests (Patterns fields are null).
     * - If NPE is thrown, the test catches it and force-fails with a clear message.
     */
    @Test
    fun `B3 - blank password with valid email shows password field error and does not advance step`() {
        try {
            presenter.onNameContinue("Alice", "Smith", "alice@example.com", "")

            // If no exception, check password error was shown
            val passwordErrorWithNonNullMsg = fieldErrorsCalled.any { (field, msg) ->
                field == "password" && msg != null
            }

            assertTrue(
                "Expected showFieldError('password', <non-null message>) when password is blank.\n" +
                    "Actual calls: $fieldErrorsCalled",
                passwordErrorWithNonNullMsg
            )

            assertEquals(
                "Step should remain 1 when password is blank. Current step: ${presenter.getCurrentStep()}",
                1, presenter.getCurrentStep()
            )

        } catch (e: NullPointerException) {
            // NPE from android.util.Patterns.EMAIL_ADDRESS == null in JVM tests.
            // This confirms Bug 2: the code crashes at email validation.
            assertTrue(
                "B3 FAIL — NullPointerException from android.util.Patterns confirms unfixed code " +
                    "crashes at email validation instead of properly validating the password field. " +
                    "Exception: $e",
                false // force failure to document this as a detected bug
            )
        }
    }

    /**
     * Test B4 — Skip on Step 1 must NOT navigate to Step 2.
     *
     * Validates: Requirements 1.4 (Bug 2 — Skip button bypass)
     *
     * EXPECTED TO FAIL on unfixed code: `onSkip()` currently calls `goToStep(2)`
     * when step == 1, bypassing all credential validation.
     *
     * Failure proves bug exists.
     */
    @Test
    fun `B4 - skip on step 1 must not navigate to step 2`() {
        assertEquals("Initial step should be 1", 1, presenter.getCurrentStep())

        presenter.onSkip()

        assertEquals(
            "Step should remain 1 after Skip is tapped on Step 1 (credential bypass). " +
                "Bug 2: onSkip() calls goToStep(2) when step==1, current step is ${presenter.getCurrentStep()}",
            1, presenter.getCurrentStep()
        )
    }

    /**
     * Test C1 — onPasswordChanged does not exist on SignUpPresenter (Bug 3).
     *
     * Validates: Requirements 1.3 (Bug 3 — no live password rule feedback)
     *
     * EXPECTED TO PASS on unfixed code (method absent = bug confirmed).
     * After fix (task 5), this test will FAIL (method added = bug resolved).
     */
    @Test
    fun `C1 - SignUpPresenter does not implement onPasswordChanged - confirms Bug 3 absent method`() {
        val presenterMethods = presenter.javaClass.methods.map { it.name }
        assertFalse(
            "onPasswordChanged should NOT exist on SignUpPresenter (Bug 3 — method absent before fix). " +
                "If this test FAILS, Bug 3 may already be fixed.",
            presenterMethods.contains("onPasswordChanged")
        )
    }
}
