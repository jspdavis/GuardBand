package com.example.guardband.ui.signup

/** One-shot events emitted by [SignUpLocationViewModel]. */
sealed interface SignUpLocationEvent {
    data class ShowMessage(val text: String) : SignUpLocationEvent
    data class NavigateToContacts(val name: String, val location: String) : SignUpLocationEvent
}
