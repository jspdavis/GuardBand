package com.example.guardband.ui.login

import com.example.guardband.data.model.User

/** One-time navigation / action events emitted via SharedFlow. */
sealed interface LoginEvent {
    data object NavigateToMain : LoginEvent
    data class NavigateToSignUpLocation(val user: User) : LoginEvent
    data object NavigateToSignUp : LoginEvent
    data object NavigateToForgotPassword : LoginEvent
    data object LaunchGoogleSignIn : LoginEvent
}
