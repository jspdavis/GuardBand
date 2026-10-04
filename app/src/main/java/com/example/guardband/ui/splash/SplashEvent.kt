package com.example.guardband.ui.splash

/** One-shot events emitted by [SplashViewModel]. */
sealed interface SplashEvent {
    object NavigateToLogin : SplashEvent
    object NavigateToHome : SplashEvent
}
