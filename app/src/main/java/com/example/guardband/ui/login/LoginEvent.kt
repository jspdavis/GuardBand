package com.example.guardband.ui.login

/** One-shot events emitted by [LoginViewModel]. */
sealed interface LoginEvent {
    data class ShowMessage(val text: String) : LoginEvent
    object NavigateToLoadingDashboard : LoginEvent
    object NavigateToSignUp : LoginEvent
    object NavigateToForgotPassword : LoginEvent
}
