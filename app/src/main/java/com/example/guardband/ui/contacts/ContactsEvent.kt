package com.example.guardband.ui.contacts

/** One-shot events emitted by [ContactsViewModel]. */
sealed interface ContactsEvent {
    data class ShowMessage(val text: String) : ContactsEvent
}
