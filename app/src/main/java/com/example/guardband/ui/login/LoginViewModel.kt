package com.example.guardband.ui.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.loginSuspend
import com.example.guardband.data.repository.signInWithGoogleIdTokenSuspend
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun login(identifier: String, password: String) {
        val trimmedId = identifier.trim()

        if (trimmedId.isBlank()) {
            _uiState.value = LoginUiState.IdentifierError("This field is required.")
            return
        }
        _uiState.value = LoginUiState.IdentifierError(null)

        if (password.isBlank()) {
            _uiState.value = LoginUiState.Error("Password is required.")
            return
        }

        val isPhone = Regex("^\\+?\\d{10,15}$").matches(trimmedId)
        val isEmail = Patterns.EMAIL_ADDRESS.matcher(trimmedId).matches()
        if (!isPhone && !isEmail) {
            _uiState.value = LoginUiState.IdentifierError("Invalid phone number or email format.")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val user = authRepository.loginSuspend(trimmedId, password)
                _uiState.value = LoginUiState.Success(user)
                _events.emit(LoginEvent.NavigateToMain)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Login failed.")
            }
        }
    }

    fun onGoogleSignInClicked() {
        viewModelScope.launch { _events.emit(LoginEvent.LaunchGoogleSignIn) }
    }

    fun onGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val (user, isNewUser) = authRepository.signInWithGoogleIdTokenSuspend(idToken)
                _uiState.value = LoginUiState.Success(user)
                if (isNewUser || !user.profileComplete) {
                    _events.emit(LoginEvent.NavigateToSignUpLocation(user))
                } else {
                    _events.emit(LoginEvent.NavigateToMain)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Google Sign-In failed.")
            }
        }
    }

    fun onGoogleSignInFailed(message: String) {
        _uiState.value = LoginUiState.Error(message)
    }

    fun onCreateAccountClicked() {
        viewModelScope.launch { _events.emit(LoginEvent.NavigateToSignUp) }
    }

    fun onForgotPasswordClicked() {
        viewModelScope.launch { _events.emit(LoginEvent.NavigateToForgotPassword) }
    }

    class Factory(private val authRepository: AuthRepository = AuthRepository.getInstance()) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoginViewModel(authRepository) as T
    }
}
