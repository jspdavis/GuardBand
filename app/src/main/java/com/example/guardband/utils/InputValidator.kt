package com.example.guardband.utils

import android.util.Patterns

/**
 * Input predicates shared by the screen ViewModels.
 *
 * Predicates only: error messages differ per screen and stay in each
 * ViewModel. Semantics mirror the MVP presenters (AuthPresenter,
 * ForgotPresenter) exactly — notably, nothing here trims its input except
 * [isBlank], which treats whitespace-only text as blank.
 */
object InputValidator {

    /** True for empty or whitespace-only text (same as `String.isBlank()`). */
    fun isBlank(text: String): Boolean = text.isBlank()

    /** Matches [Patterns.EMAIL_ADDRESS] against [email] as given (untrimmed). */
    fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /**
     * True when [password] has at least [min] characters (no trimming).
     *
     * The default is the app's rule, and it is stricter than Firebase's own
     * minimum of 6, so a password that passes here is never rejected as
     * [AuthError.WeakPassword][com.example.guardband.data.repository.AuthError.WeakPassword].
     */
    fun isPasswordLongEnough(password: String, min: Int = 8): Boolean =
        password.length >= min

    /** Exact, case-sensitive equality. */
    fun passwordsMatch(a: String, b: String): Boolean = a == b
}
