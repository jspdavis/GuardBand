package com.example.guardband.ui.forgotpass

import android.os.CountDownTimer
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.requestPasswordResetSuspend
import com.example.guardband.data.repository.resendOtpSuspend
import com.example.guardband.data.repository.updatePasswordWithOtpSuspend
import com.example.guardband.data.repository.verifyOtpSuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private var email: String = ""
    private var otpVerified = false
    private var countDownTimer: CountDownTimer? = null
    private var hasTypedPassword = false

    fun getEmail(): String = email

    fun onRequestReset(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            _uiState.value = ForgotPasswordUiState.Error("Please enter your registered email.")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            _uiState.value = ForgotPasswordUiState.Error("Enter a valid email address.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            try {
                val debugOtp = authRepository.requestPasswordResetSuspend(trimmed)
                this@ForgotPasswordViewModel.email = trimmed
                if (!debugOtp.isNullOrBlank()) {
                    _uiState.value = ForgotPasswordUiState.DebugOtp(debugOtp)
                }
                _uiState.value = ForgotPasswordUiState.ShowStep(2, trimmed)
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Reset request failed.")
            }
        }
    }

    fun onResendCode() {
        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            try {
                val debugOtp = authRepository.resendOtpSuspend(email)
                if (!debugOtp.isNullOrBlank()) {
                    _uiState.value = ForgotPasswordUiState.DebugOtp(debugOtp)
                }
                _uiState.value = ForgotPasswordUiState.ShowStep(3, email)
                startOtpTimer()
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Resend failed.")
            }
        }
    }

    fun onChangeEmail() {
        stopOtpTimer()
        otpVerified = false
        _uiState.value = ForgotPasswordUiState.ShowStep(1)
    }

    fun onVerifyOtp(code: String) {
        if (code.length < 5) {
            _uiState.value = ForgotPasswordUiState.Error("Please enter the complete verification code.")
            return
        }
        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            try {
                authRepository.verifyOtpSuspend(email, code)
                otpVerified = true
                stopOtpTimer()
                _uiState.value = ForgotPasswordUiState.ShowStep(4)
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Verification failed.")
            }
        }
    }

    fun onResendOtpFromVerify() {
        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            try {
                val debugOtp = authRepository.resendOtpSuspend(email)
                if (!debugOtp.isNullOrBlank()) {
                    _uiState.value = ForgotPasswordUiState.DebugOtp(debugOtp)
                }
                _uiState.value = ForgotPasswordUiState.Idle
                startOtpTimer()
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Resend failed.")
            }
        }
    }

    fun onPasswordChanged(newPassword: String, confirmPassword: String) {
        hasTypedPassword = true
        val result = ValidationResult.evaluatePassword(newPassword, confirmPassword)
        _uiState.value = ForgotPasswordUiState.PasswordRulesUpdate(result.passwordRules, hasTypedPassword)
        _uiState.value = ForgotPasswordUiState.PasswordMismatchError(
            confirmPassword.isNotEmpty() && !result.passwordRules.passwordsMatch
        )
        when {
            result.isValid -> {
                _uiState.value = ForgotPasswordUiState.CredentialBorders(valid = true, error = false)
                _uiState.value = ForgotPasswordUiState.ShowStep(7)
            }
            hasTypedPassword && (newPassword.isNotEmpty() || confirmPassword.isNotEmpty()) -> {
                _uiState.value = ForgotPasswordUiState.CredentialBorders(valid = false, error = true)
                _uiState.value = ForgotPasswordUiState.ShowStep(6)
            }
            else -> {
                _uiState.value = ForgotPasswordUiState.CredentialBorders(valid = null, error = false)
                _uiState.value = ForgotPasswordUiState.ShowStep(4)
            }
        }
    }

    fun onSubmitNewPassword(newPassword: String, confirmPassword: String) {
        if (newPassword.isBlank() || confirmPassword.isBlank()) {
            _uiState.value = ForgotPasswordUiState.RequiredFieldErrors(true)
            _uiState.value = ForgotPasswordUiState.CredentialBorders(valid = false, error = true)
            _uiState.value = ForgotPasswordUiState.ShowStep(5)
            return
        }

        val result = ValidationResult.evaluatePassword(newPassword, confirmPassword)
        _uiState.value = ForgotPasswordUiState.PasswordRulesUpdate(result.passwordRules, true)
        if (!result.isValid) {
            _uiState.value = ForgotPasswordUiState.PasswordMismatchError(!result.passwordRules.passwordsMatch)
            _uiState.value = ForgotPasswordUiState.CredentialBorders(valid = false, error = true)
            _uiState.value = ForgotPasswordUiState.ShowStep(6)
            _uiState.value = ForgotPasswordUiState.Error("Password does not meet all requirements.")
            return
        }

        if (!otpVerified) {
            _uiState.value = ForgotPasswordUiState.Error("Verify your code first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            try {
                authRepository.updatePasswordWithOtpSuspend(email, newPassword)
                _uiState.value = ForgotPasswordUiState.ShowStep(8)
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Password update failed.")
            }
        }
    }

    fun onCancelCredentials() {
        stopOtpTimer()
        _uiState.value = ForgotPasswordUiState.ShowStep(1)
    }

    fun onLoginClicked() {
        _uiState.value = ForgotPasswordUiState.NavigateToLoginCleared
    }

    fun startOtpTimer() {
        stopOtpTimer()
        countDownTimer = object : CountDownTimer(2 * 60 * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val total = (millisUntilFinished / 1000).toInt()
                _uiState.value = ForgotPasswordUiState.TimerTick(
                    String.format("%d:%02d mins", total / 60, total % 60)
                )
            }
            override fun onFinish() {
                _uiState.value = ForgotPasswordUiState.TimerTick("0:00 mins")
                _uiState.value = ForgotPasswordUiState.TimerFinished
            }
        }.start()
    }

    fun stopOtpTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    override fun onCleared() {
        stopOtpTimer()
        super.onCleared()
    }

    class Factory(private val authRepository: AuthRepository = AuthRepository.getInstance()) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ForgotPasswordViewModel(authRepository) as T
    }
}
