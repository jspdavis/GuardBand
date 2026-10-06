package com.example.guardband.ui.signup

/** One-shot events emitted by [SignUpConsentViewModel]. */
sealed interface SignUpConsentEvent {
    data class ShowMessage(val text: String) : SignUpConsentEvent
    object NavigateToLoadingHome : SignUpConsentEvent
}
