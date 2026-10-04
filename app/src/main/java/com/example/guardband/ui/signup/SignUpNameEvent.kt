package com.example.guardband.ui.signup

/** One-shot events emitted by [SignUpNameViewModel]. */
sealed interface SignUpNameEvent {
    data class ShowMessage(val text: String) : SignUpNameEvent
    data class NavigateToLocation(val name: String) : SignUpNameEvent
}
