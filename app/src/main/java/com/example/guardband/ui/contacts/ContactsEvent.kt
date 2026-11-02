package com.example.guardband.ui.contacts

import com.example.guardband.data.model.EmergencyContact

/** One-shot events emitted by [ContactsViewModel]. */
sealed interface ContactsEvent {
    data class ShowMessage(val text: String) : ContactsEvent

    /**
     * Open the add/edit dialog.
     *
     * [contact] is null for a new contact and the existing record for an edit,
     * which is the only difference between the two uses of one dialog (D6).
     */
    data class ShowContactEditor(val contact: EmergencyContact?) : ContactsEvent

    /**
     * Open the phone's dialer on [phone], the guardian card's Call button.
     *
     * The dialer only fills the number in; the user still places the call,
     * so the app needs no phone permission.
     */
    data class Dial(val phone: String) : ContactsEvent
}
