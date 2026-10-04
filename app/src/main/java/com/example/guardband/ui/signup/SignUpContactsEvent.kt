package com.example.guardband.ui.signup

/** One-shot events emitted by [SignUpContactsViewModel]. */
sealed interface SignUpContactsEvent {
    data class ShowMessage(val text: String) : SignUpContactsEvent
    object NavigateToLoadingHome : SignUpContactsEvent
}
