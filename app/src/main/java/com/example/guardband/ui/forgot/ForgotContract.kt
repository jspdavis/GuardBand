package com.example.guardband.ui.forgot

import com.example.guardband.base.BaseView

/**
 * MVP contract for the full Forgot Password flow.
 *
 * Steps:
 *   ForgotRequestActivity  →  ForgotVerifyActivity
 *   ForgotVerifyActivity   →  ForgotNewPassActivity
 *   ForgotNewPassActivity  →  ForgotSuccessActivity
 *   ForgotSuccessActivity  →  LoginActivity
 */
interface ForgotContract {

    // ── Step 1: Request reset link ────────────────────────────────────────────

    interface RequestView : BaseView {
        fun navigateToVerify(email: String)
    }

    interface RequestPresenter {
        fun onSendLinkClicked(email: String)
    }

    // ── Step 2: Verify code ───────────────────────────────────────────────────

    interface VerifyView : BaseView {
        fun navigateToNewPassword(email: String)
        fun showResendConfirmation()
    }

    interface VerifyPresenter {
        fun onVerifyClicked(email: String, code: String)
        fun onResendClicked(email: String)
    }

    // ── Step 3: Set new password ──────────────────────────────────────────────

    interface NewPasswordView : BaseView {
        fun navigateToSuccess()
    }

    interface NewPasswordPresenter {
        fun onSavePasswordClicked(email: String, newPassword: String, confirmPassword: String)
    }

    // ── Step 4: Success confirmation ──────────────────────────────────────────

    interface SuccessView : BaseView {
        fun navigateToLogin()
    }

    interface SuccessPresenter {
        fun onBackToLoginClicked()
    }
}
