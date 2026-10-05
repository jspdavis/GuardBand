package com.example.guardband.ui.contacts

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.utils.InputValidator

/** Render state for [ContactsFragment]. */
data class ContactsUiState(
    /** True until the first read of the contact list completes. */
    val isLoading: Boolean = false,

    /**
     * True while an add, edit or delete is in flight.
     *
     * Separate from [isLoading] so a mutation does not make the list look like
     * it is reloading, and so the double-submit guard does not also block on a
     * slow first read.
     */
    val isMutating: Boolean = false,

    val contacts: List<EmergencyContact> = emptyList(),

    /**
     * True when the last read failed. [contacts] then still holds the last good
     * list, which is deliberate.
     */
    val loadFailed: Boolean = false
) {
    /** Drives the "n of 3 minimum" notice; hidden once the user is at or above it. */
    val showMinimumNotice: Boolean
        get() = !isLoading && contacts.size < InputValidator.MIN_CONTACTS

    /** True when the empty-state text should show instead of the list. */
    val showEmpty: Boolean
        get() = !isLoading && !loadFailed && contacts.isEmpty()
}
