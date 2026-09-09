package com.example.guardband.ui.forgot

import com.example.guardband.base.BasePresenter
import com.example.guardband.data.MockRepository

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — Request reset link
// ─────────────────────────────────────────────────────────────────────────────

class ForgotRequestPresenter : BasePresenter<ForgotContract.RequestView>(),
    ForgotContract.RequestPresenter {

    override fun onSendLinkClicked(email: String) {
        if (email.isBlank()) {
            view?.showError("Please enter your email address.")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            view?.showError("Enter a valid email address.")
            return
        }

        view?.showLoading()
        MockRepository.requestPasswordReset(
            email = email,
            onSuccess = {
                view?.hideLoading()
                view?.navigateToVerify(email.trim())
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — Verify code
// ─────────────────────────────────────────────────────────────────────────────

class ForgotVerifyPresenter : BasePresenter<ForgotContract.VerifyView>(),
    ForgotContract.VerifyPresenter {

    override fun onVerifyClicked(email: String, code: String) {
        if (code.isBlank()) {
            view?.showError("Please enter the verification code.")
            return
        }

        view?.showLoading()
        MockRepository.verifyResetCode(
            code = code,
            onSuccess = {
                view?.hideLoading()
                view?.navigateToNewPassword(email)
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onResendClicked(email: String) {
        view?.showLoading()
        MockRepository.requestPasswordReset(
            email = email,
            onSuccess = {
                view?.hideLoading()
                view?.showResendConfirmation()
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — Set new password
// ─────────────────────────────────────────────────────────────────────────────

class ForgotNewPasswordPresenter : BasePresenter<ForgotContract.NewPasswordView>(),
    ForgotContract.NewPasswordPresenter {

    override fun onSavePasswordClicked(
        email: String,
        newPassword: String,
        confirmPassword: String
    ) {
        if (newPassword.isBlank()) {
            view?.showError("Please enter a new password.")
            return
        }
        if (newPassword.length < 6) {
            view?.showError("Password must be at least 6 characters.")
            return
        }
        if (newPassword != confirmPassword) {
            view?.showError("Passwords do not match.")
            return
        }

        view?.showLoading()
        MockRepository.resetPassword(
            email = email,
            newPassword = newPassword,
            onSuccess = {
                view?.hideLoading()
                view?.navigateToSuccess()
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4 — Success
// ─────────────────────────────────────────────────────────────────────────────

class ForgotSuccessPresenter : BasePresenter<ForgotContract.SuccessView>(),
    ForgotContract.SuccessPresenter {

    override fun onBackToLoginClicked() {
        view?.navigateToLogin()
    }
}
