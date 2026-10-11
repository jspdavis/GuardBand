package com.example.guardband.utils

/**
 * Which of the password rules the typed pair currently satisfies.
 *
 * A value, not a view and not a string: the wording lives in `strings.xml` and
 * the tinting in
 * [PasswordCriteriaList][com.example.guardband.ui.auth.PasswordCriteriaList],
 * so this stays pure and testable.
 *
 * **It derives every rule from [InputValidator] rather than restating one.**
 * The criteria list a user reads and the check that actually gates Submit have
 * to be the same rules, or the screen will refuse a password it just ticked
 * green. Adding a rule means adding a predicate to [InputValidator] and a
 * field here — never a length comparison written out again.
 */
data class PasswordCriteria(

    /** At least [InputValidator.isPasswordLongEnough]'s minimum. */
    val longEnough: Boolean,

    /** New and Confirm are identical, and not both empty. */
    val matches: Boolean,

    /**
     * True while both fields are still empty.
     *
     * Only the rendering cares: an unmet rule reads as neutral before the user
     * has typed anything and as a failure afterwards, so the list does not open
     * accusing them of a password they have not written yet. It is deliberately
     * not part of [allMet] - nothing typed is nothing valid either way.
     */
    val untouched: Boolean
) {

    /** True when every rule passes, which is what Submit is gated on. */
    val allMet: Boolean
        get() = longEnough && matches

    companion object {

        /** Nothing typed yet: every rule unmet, none of them an error. */
        val NONE = PasswordCriteria(longEnough = false, matches = false, untouched = true)

        /**
         * Evaluates the rules against what is typed.
         *
         * [matches] requires a non-empty password, so two empty fields do not
         * read as "they match" before the user has typed anything.
         */
        fun of(password: String, confirmPassword: String): PasswordCriteria =
            PasswordCriteria(
                longEnough = InputValidator.isPasswordLongEnough(password),
                matches = password.isNotEmpty() &&
                    InputValidator.passwordsMatch(password, confirmPassword),
                untouched = password.isEmpty() && confirmPassword.isEmpty()
            )
    }
}
