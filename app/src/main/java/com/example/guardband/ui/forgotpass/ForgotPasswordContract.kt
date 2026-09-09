package com.example.guardband.ui.forgotpass

import com.example.guardband.base.BaseView
import com.example.guardband.data.model.ValidationResult

interface ForgotPasswordContract {

    interface View : BaseView {
        fun showStep(step: Int)
        fun updateTimer(text: String)
        fun onTimerFinished()
        fun updatePasswordRules(rules: ValidationResult.PasswordRules, hasTyped: Boolean)
        fun showRequiredFieldErrors(show: Boolean)
        fun showPasswordMismatchError(show: Boolean)
        fun setCredentialBorders(valid: Boolean?, error: Boolean)
        fun showDebugOtp(otp: String)
        fun navigateToLoginCleared()
    }

    interface Presenter {
        fun onRequestReset(email: String)
        fun onResendCode()
        fun onChangeEmail()
        fun onVerifyOtp(code: String)
        fun onResendOtpFromVerify()
        fun onPasswordChanged(newPassword: String, confirmPassword: String)
        fun onSubmitNewPassword(newPassword: String, confirmPassword: String)
        fun onCancelCredentials()
        fun onLoginClicked()
        fun startOtpTimer()
        fun stopOtpTimer()
        fun getEmail(): String
    }
}
