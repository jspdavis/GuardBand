package com.example.guardband.ui.forgotpass

import android.os.CountDownTimer
import android.util.Patterns
import com.example.guardband.base.BasePresenter
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.data.repository.AuthRepository

/**
 * Drives the 8-step Forgot Password state machine inside [ForgotPasswordActivity].
 *
 * Steps 4–7 share the credentials UI; visual state is driven by validation.
 */
class ForgotPasswordPresenter(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : BasePresenter<ForgotPasswordContract.View>(), ForgotPasswordContract.Presenter {

    private var email: String = ""
    private var otpVerified = false
    private var countDownTimer: CountDownTimer? = null
    private var hasTypedPassword = false

    override fun getEmail(): String = email

    override fun onRequestReset(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            view?.showError("Please enter your registered email.")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            view?.showError("Enter a valid email address.")
            return
        }

        view?.showLoading()
        authRepository.requestPasswordReset(
            email = trimmed,
            onSuccess = { debugOtp ->
                this.email = trimmed
                view?.hideLoading()
                if (!debugOtp.isNullOrBlank()) {
                    view?.showDebugOtp(debugOtp)
                }
                view?.showStep(2)
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onResendCode() {
        view?.showLoading()
        authRepository.resendOtp(
            email = email,
            onSuccess = { debugOtp ->
                view?.hideLoading()
                if (!debugOtp.isNullOrBlank()) view?.showDebugOtp(debugOtp)
                view?.showStep(3)
                startOtpTimer()
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onChangeEmail() {
        stopOtpTimer()
        otpVerified = false
        view?.showStep(1)
    }

    override fun onVerifyOtp(code: String) {
        if (code.length < 5) {
            view?.showError("Please enter the complete verification code.")
            return
        }
        view?.showLoading()
        authRepository.verifyOtp(
            email = email,
            code = code,
            onSuccess = {
                otpVerified = true
                stopOtpTimer()
                view?.hideLoading()
                view?.showStep(4)
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onResendOtpFromVerify() {
        view?.showLoading()
        authRepository.resendOtp(
            email = email,
            onSuccess = { debugOtp ->
                view?.hideLoading()
                if (!debugOtp.isNullOrBlank()) view?.showDebugOtp(debugOtp)
                startOtpTimer()
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onPasswordChanged(newPassword: String, confirmPassword: String) {
        hasTypedPassword = true
        val result = ValidationResult.evaluatePassword(newPassword, confirmPassword)
        view?.updatePasswordRules(result.passwordRules, hasTypedPassword)
        view?.showPasswordMismatchError(
            confirmPassword.isNotEmpty() && !result.passwordRules.passwordsMatch
        )
        when {
            result.isValid -> {
                view?.setCredentialBorders(valid = true, error = false)
                view?.showStep(7) // valid visual state
            }
            hasTypedPassword && (newPassword.isNotEmpty() || confirmPassword.isNotEmpty()) -> {
                view?.setCredentialBorders(valid = false, error = true)
                view?.showStep(6) // realtime rule check
            }
            else -> {
                view?.setCredentialBorders(valid = null, error = false)
                view?.showStep(4)
            }
        }
    }

    override fun onSubmitNewPassword(newPassword: String, confirmPassword: String) {
        if (newPassword.isBlank() || confirmPassword.isBlank()) {
            view?.showRequiredFieldErrors(true)
            view?.setCredentialBorders(valid = false, error = true)
            view?.showStep(5)
            return
        }

        val result = ValidationResult.evaluatePassword(newPassword, confirmPassword)
        view?.updatePasswordRules(result.passwordRules, true)
        if (!result.isValid) {
            view?.showPasswordMismatchError(!result.passwordRules.passwordsMatch)
            view?.setCredentialBorders(valid = false, error = true)
            view?.showStep(6)
            view?.showError("Password does not meet all requirements.")
            return
        }

        if (!otpVerified) {
            view?.showError("Verify your code first.")
            return
        }

        view?.showLoading()
        authRepository.updatePasswordWithOtp(
            email = email,
            newPassword = newPassword,
            onSuccess = {
                view?.hideLoading()
                view?.showStep(8)
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onCancelCredentials() {
        stopOtpTimer()
        view?.showStep(1)
    }

    override fun onLoginClicked() {
        view?.navigateToLoginCleared()
    }

    override fun startOtpTimer() {
        stopOtpTimer()
        countDownTimer = object : CountDownTimer(2 * 60 * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val total = (millisUntilFinished / 1000).toInt()
                view?.updateTimer(String.format("%d:%02d mins", total / 60, total % 60))
            }

            override fun onFinish() {
                view?.updateTimer("0:00 mins")
                view?.onTimerFinished()
            }
        }.start()
    }

    override fun stopOtpTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }
}
