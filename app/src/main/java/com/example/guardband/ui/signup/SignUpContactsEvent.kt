package com.example.guardband.ui.signup

import com.example.guardband.data.model.EmergencyContact

/** One-shot events emitted by [SignUpContactsViewModel]. */
sealed interface SignUpContactsEvent {
    data class ShowMessage(val text: String) : SignUpContactsEvent

    /** Open the add/edit dialog. A null [contact] means add. */
    data class ShowContactEditor(val contact: EmergencyContact?) : SignUpContactsEvent

    /**
     * Everything the wizard has collected, on its way to the final screen.
     *
     * Nothing has been written at this point: Consent is where the account is
     * created and the whole record committed.
     */
    data class NavigateToConsent(
        val name: String,
        val email: String,
        val password: String,
        val contacts: List<EmergencyContact>,
        val completeProfile: Boolean
    ) : SignUpContactsEvent
}
