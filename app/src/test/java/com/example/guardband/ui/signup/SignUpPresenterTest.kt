package com.example.guardband.ui.signup

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.User
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.ContactRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bug condition exploration tests for SignUpPresenter (Task 1).
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4
 *
 * IMPORTANT: Tests B1–B4 are EXPECTED TO FAIL on unfixed code.
 * Failure confirms Bug 2 exists (blank-credential bypass and skip navigation).
 */
class SignUpPresenterTest {

    // ── Hand-rolled stub View ─────────────────────────────────────────────────

    private class FakeSignUpView : SignUpContract.View {
        data class FieldError(val field: String, val message: String?)

        val errors = mutableListOf<String>()
        val fieldErrors = mutableListOf<FieldError>()
        val shownSteps = mutableListOf<Int>()
        val stepIndicatorUpdates = mutableListOf<Int>()
        var renderedContacts: List<EmergencyContact> = emptyList()
        var contactFormCleared = false
        var navigatedToLoading = false

        override fun showError(message: String) { errors.add(message) }
        override fun showLoading() {}
        override fun hideLoading() {}
        override fun showStep(step: Int) { shownSteps.add(step) }
        override fun updateStepIndicator(step: Int) { stepIndicatorUpdates.add(step) }
        override fun renderContacts(contacts: List<EmergencyContact>) { renderedContacts = contacts }
        override fun clearContactForm() { contactFormCleared = true }
        override fun navigateToLoading() { navigatedToLoading = true }
        override fun showFieldError(field: String, message: String?) {
            fieldErrors.add(FieldError(field, message))
        }
        override fun updatePasswordCriteria(rules: ValidationResult.PasswordRules, hasTyped: Boolean) {}
    }

    // ── Hand-rolled stub AuthRepository ──────────────────────────────────────

    private class StubAuthRepository : AuthRepository() {
        var registerCallCount = 0
        var updateProfileCallCount = 0
        var loggedIn = false

        override fun registerWithEmail(
            email: String,
            password: String,
            firstName: String,
            lastName: String,
            phone: String,
            onSuccess: (User) -> Unit,
            onError: (String) -> Unit
        ) {
            registerCallCount++
            // Do not invoke callbacks — we only assert call counts
        }

        override fun updateProfile(
            updates: Map<String, Any?>,
            onSuccess: (User) -> Unit,
            onError: (String) -> Unit
        ) {
            updateProfileCallCount++
        }

        override fun isLoggedIn(): Boolean = loggedIn
    }

    // ── Hand-rolled stub ContactRepository ───────────────────────────────────

    private class StubContactRepository : ContactRepository() {
        var addCallCount = 0

        override fun addContact(
            contact: EmergencyContact,
            onSuccess: (EmergencyContact) -> Unit,
            onError: (String) -> Unit
        ) {
            addCallCount++
        }
    }

    private lateinit var view: FakeSignUpView
    private lateinit var authRepo: StubAuthRepository
    private lateinit var contactRepo: StubContactRepository
    private lateinit var presenter: SignUpPresenter

    @Before
    fun setUp() {
        view = FakeSignUpView()
        authRepo = StubAuthRepository()
        contactRepo = StubContactRepository()
        presenter = SignUpPresenter(authRepository = authRepo, contactRepository = contactRepo)
        presenter.attachView(view)
    }

    // ── Bug 2: Blank credentials should require email ─────────────────────────

    /**
     * Test B1 — blank email AND blank password with valid name should show email error
     * and must NOT advance the step.
     *
     * Validates: Requirements 1.4 (Bug 2 — blank credential bypass)
     *
     * EXPECTED TO FAIL on unfixed code:
     * The current `onNameContinue` logic contains `else -> goToStep(2)` which
     * silently advances to Step 2 when both email and password are blank.
     *
     * Failure proves bug exists.
     */
    @Test
    fun `B1 - blank email and blank password shows field error and does not advance step`() {
        presenter.onNameContinue("Alice", "Smith", "", "")

        // Assert showFieldError was called for email
        val emailError = view.fieldErrors.find { it.field == "email" }
        assertTrue(
            "Expected showFieldError('email', ...) to be called for blank email, " +
                "but fieldErrors were: ${view.fieldErrors}",
            emailError != null && emailError.message != null
        )

        // Assert step was NOT advanced to 2
        assertFalse(
            "showStep(2) should NOT have been called when email is blank, " +
                "but shown steps were: ${view.shownSteps}",
            view.shownSteps.contains(2)
        )

        // Assert registerWithEmail was NOT called
        assertEquals(
            "authRepository.registerWithEmail() should NOT have been called, " +
                "but was called ${authRepo.registerCallCount} time(s)",
            0, authRepo.registerCallCount
        )
    }

    /**
     * Test B2 — blank email with non-blank password should show email error.
     *
     * Validates: Requirements 1.4 (Bug 2 — blank email alone with non-blank password)
     *
     * EXPECTED TO FAIL on unfixed code: the current `if (trimmedEmail.isNotBlank() ||
     * trimmedPassword.isNotBlank())` condition enters the validation block only when at
     * least one field is filled — but with a blank email the code validates only the
     * password length, missing the email blank check.
     *
     * Failure proves bug exists.
     */
    @Test
    fun `B2 - blank email with non-blank password shows email field error`() {
        presenter.onNameContinue("Alice", "Smith", "", "Secret1!")

        val emailError = view.fieldErrors.find { it.field == "email" }
        assertTrue(
            "Expected showFieldError('email', ...) to be called when email is blank " +
                "but password is provided, but fieldErrors were: ${view.fieldErrors}",
            emailError != null && emailError.message != null
        )

        assertFalse(
            "showStep(2) should NOT be called when email is blank, " +
                "but shown steps were: ${view.shownSteps}",
            view.shownSteps.contains(2)
        )
    }

    /**
     * Test B3 — blank password with valid email should show password error.
     *
     * Validates: Requirements 1.4 (Bug 2 — blank password with valid email)
     *
     * EXPECTED TO FAIL on unfixed code: when email is provided but password is blank,
     * the current check `if (trimmedPassword.length < 8)` accepts an empty string as
     * "length 0 < 8" and shows the password error, but does not stop submission since
     * the outer `if` condition passes. This test verifies the current code does NOT
     * properly block advance.
     *
     * NOTE: This test may partially pass on current code since the length check fires,
     * but the exact message content is what's being verified.
     *
     * Failure proves bug exists in the specific error path or message.
     */
    @Test
    fun `B3 - blank password with valid email shows password field error`() {
        presenter.onNameContinue("Alice", "Smith", "alice@example.com", "")

        val passwordError = view.fieldErrors.find { it.field == "password" }
        assertTrue(
            "Expected showFieldError('password', ...) to be called when password is blank, " +
                "but fieldErrors were: ${view.fieldErrors}",
            passwordError != null && passwordError.message != null
        )

        assertFalse(
            "showStep(2) should NOT be called when password is blank, " +
                "but shown steps were: ${view.shownSteps}",
            view.shownSteps.contains(2)
        )
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
        // Step is 1 by default after construction
        assertEquals("Initial step should be 1", 1, presenter.getCurrentStep())

        presenter.onSkip()

        assertFalse(
            "showStep(2) should NOT be called when Skip is tapped on Step 1 (credential bypass), " +
                "but shown steps were: ${view.shownSteps}",
            view.shownSteps.contains(2)
        )

        assertEquals(
            "Step should remain 1 after Skip is tapped on Step 1 (no bypass), " +
                "but getCurrentStep() returned ${presenter.getCurrentStep()}",
            1, presenter.getCurrentStep()
        )
    }

    /**
     * Test C1 — onPasswordChanged does not exist on SignUpPresenter (Bug 3).
     *
     * Validates: Requirements 1.3 (Bug 3 — no live password rule feedback)
     *
     * This is a compilation-time verification: if SignUpContract.Presenter has no
     * `onPasswordChanged` method, the compiler would reject a call to it.
     * At runtime, we verify the presenter interface lacks the method by checking
     * the presenter does NOT implement it — confirmed by calling an introspection check.
     *
     * This test documents Bug 3 by asserting the method is absent in the contract.
     */
    @Test
    fun `C1 - SignUpPresenter does not implement onPasswordChanged - confirms Bug 3`() {
        val presenterMethods = presenter.javaClass.methods.map { it.name }
        assertFalse(
            "onPasswordChanged should NOT exist on SignUpPresenter (Bug 3 — method absent before fix). " +
                "If this test passes, Bug 3 may already be fixed. Methods found: $presenterMethods",
            presenterMethods.contains("onPasswordChanged")
        )
    }
}
