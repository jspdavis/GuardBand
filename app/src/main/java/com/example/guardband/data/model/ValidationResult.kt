package com.example.guardband.data.model

/**
 * Result of password / form validation used by presenters and UI state.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val passwordRules: PasswordRules = PasswordRules()
) {
    data class PasswordRules(
        val minLength: Boolean = false,
        val hasUppercase: Boolean = false,
        val hasLowercase: Boolean = false,
        val hasNumberOrSpecial: Boolean = false,
        val passwordsMatch: Boolean = false
    ) {
        val allMet: Boolean
            get() = minLength && hasUppercase && hasLowercase &&
                hasNumberOrSpecial && passwordsMatch
    }

    companion object {
        fun evaluatePassword(password: String, confirm: String): ValidationResult {
            val rules = PasswordRules(
                minLength = password.length >= 8,
                hasUppercase = password.any { it.isUpperCase() },
                hasLowercase = password.any { it.isLowerCase() },
                hasNumberOrSpecial = password.any { it.isDigit() || !it.isLetterOrDigit() },
                passwordsMatch = confirm.isNotEmpty() && password == confirm
            )
            val errors = mutableListOf<String>()
            if (password.isBlank()) errors += "This is a required field."
            if (confirm.isBlank()) errors += "This is a required field."
            if (confirm.isNotBlank() && password != confirm) errors += "Passwords do not match."
            return ValidationResult(
                isValid = password.isNotBlank() && confirm.isNotBlank() && rules.allMet,
                errors = errors,
                passwordRules = rules
            )
        }
    }
}
