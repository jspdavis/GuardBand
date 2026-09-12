package com.example.guardband.ui.changepass

import android.util.Log
import com.example.guardband.base.BasePresenter
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.data.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider

class ChangePasswordPresenter(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : BasePresenter<ChangePasswordContract.View>(), ChangePasswordContract.Presenter {

    // ── Presenter contract ────────────────────────────────────────────────────

    override fun onNewPasswordTyped(newPassword: String, confirmPassword: String) {
        val result = ValidationResult.evaluatePassword(newPassword, confirmPassword)
        view?.updatePasswordCriteria(result.passwordRules, hasTyped = newPassword.isNotEmpty())
    }

    override fun onChangePasswordClicked(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        var hasError = false

        // 1) Current password is required.
        if (currentPassword.isBlank()) {
            view?.showCurrentPasswordError("This field is required.")
            hasError = true
        } else {
            view?.showCurrentPasswordError(null)
        }

        // 2) New password is required.
        if (newPassword.isBlank()) {
            view?.showNewPasswordError("This field is required.")
            hasError = true
        } else {
            view?.showNewPasswordError(null)
        }

        // 3) Confirm password is required.
        if (confirmPassword.isBlank()) {
            view?.showConfirmPasswordError("This field is required.")
            hasError = true
        } else {
            view?.showConfirmPasswordError(null)
        }

        if (hasError) return

        // 4) Validate new password meets all security criteria.
        val passwordValidation = ValidationResult.evaluatePassword(newPassword, confirmPassword)
        when {
            !passwordValidation.passwordRules.minLength -> {
                view?.showNewPasswordError("Password must be at least 8 characters.")
                return
            }
            !passwordValidation.passwordRules.hasUppercase -> {
                view?.showNewPasswordError("Password must contain at least 1 uppercase letter.")
                return
            }
            !passwordValidation.passwordRules.hasLowercase -> {
                view?.showNewPasswordError("Password must contain at least 1 lowercase letter.")
                return
            }
            !passwordValidation.passwordRules.hasNumberOrSpecial -> {
                view?.showNewPasswordError("Password must contain at least 1 number or special character.")
                return
            }
            !passwordValidation.passwordRules.passwordsMatch -> {
                view?.showConfirmPasswordError("Passwords do not match.")
                return
            }
        }

        // 5) Prevent user from setting the same password.
        if (currentPassword == newPassword) {
            view?.showNewPasswordError("New password must be different from current password.")
            return
        }

        // 6) Re-authenticate with current password, then update to new password.
        val currentUser = authRepository.currentUser
        if (currentUser == null) {
            view?.showError("Not signed in. Please log in again.")
            return
        }

        val email = currentUser.email
        if (email.isNullOrBlank()) {
            view?.showError("Unable to verify your account. Please contact support.")
            return
        }

        view?.showLoading()

        // 🔍 DIAGNOSTIC: Log user info before reauthenticate
        Log.d(TAG, "═══ BEFORE REAUTHENTICATE ═══")
        Log.d(TAG, "User UID: ${currentUser.uid}")
        Log.d(TAG, "User Email: ${currentUser.email}")
        Log.d(TAG, "Provider Data: ${currentUser.providerData.map { 
            "providerId=${it.providerId}, uid=${it.uid}, email=${it.email}" 
        }}")
        Log.d(TAG, "Email for credential: $email")
        Log.d(TAG, "═══════════════════════════════")

        // Re-authenticate to verify current password is correct.
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        Log.d(TAG, "🔐 Calling reauthenticate() with EmailAuthProvider credential...")
        currentUser.reauthenticate(credential)
            .addOnSuccessListener {
                // 🔍 DIAGNOSTIC: Reauthenticate succeeded
                Log.d(TAG, "✅ REAUTHENTICATE SUCCESS")
                Log.d(TAG, "User still authenticated: ${currentUser.uid}")
                
                // Current password verified — now update to new password.
                Log.d(TAG, "🔄 Calling updatePassword() with new password...")
                currentUser.updatePassword(newPassword)
                    .addOnSuccessListener {
                        // 🔍 DIAGNOSTIC: updatePassword succeeded
                        Log.d(TAG, "✅ UPDATE PASSWORD SUCCESS")
                        Log.d(TAG, "Password should now be changed in Firebase Auth")
                        Log.d(TAG, "User UID: ${currentUser.uid}")
                        
                        view?.hideLoading()
                        view?.showSuccess("Password changed successfully.")
                        // Navigate back after a short delay to let user see success message.
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            view?.navigateToDashboard()
                        }, 1500L)
                    }
                    .addOnFailureListener { e ->
                        // 🔍 DIAGNOSTIC: updatePassword failed
                        Log.e(TAG, "❌ UPDATE PASSWORD FAILURE")
                        Log.e(TAG, "Error: ${e.message}")
                        Log.e(TAG, "Error class: ${e.javaClass.name}")
                        Log.e(TAG, "Localized: ${e.localizedMessage}")
                        
                        view?.hideLoading()
                        view?.showError(
                            e.localizedMessage ?: "Failed to update password. Please try again."
                        )
                    }
            }
            .addOnFailureListener { e ->
                // 🔍 DIAGNOSTIC: Reauthenticate failed
                Log.e(TAG, "❌ REAUTHENTICATE FAILURE")
                Log.e(TAG, "Error: ${e.message}")
                Log.e(TAG, "Error class: ${e.javaClass.name}")
                Log.e(TAG, "Localized: ${e.localizedMessage}")
                Log.e(TAG, "This means current password verification failed OR wrong credential type")
                
                view?.hideLoading()
                val msg = e.localizedMessage.orEmpty()
                when {
                    msg.contains("password", ignoreCase = true) ||
                        msg.contains("credential", ignoreCase = true) ||
                        msg.contains("wrong", ignoreCase = true) ||
                        msg.contains("invalid", ignoreCase = true) ->
                        view?.showCurrentPasswordError("Current password is incorrect.")
                    else -> view?.showError(msg.ifBlank { "Authentication failed. Please try again." })
                }
            }
    }

    override fun onCancelClicked() {
        view?.navigateToDashboard()
    }

    companion object {
        private const val TAG = "ChangePasswordPresenter"
    }
}
