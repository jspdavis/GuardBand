package com.example.guardband.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.repository.ContactError
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Contacts tab: the emergency-contact list with add, edit and delete (FR-06).
 *
 * The list comes from [ContactRepository.observeContacts] and is never edited
 * locally. A mutation only sends the call; the new list arrives through the
 * flow, so what is on screen is always what is stored.
 *
 * The D2 bounds are checked here too, but only so the screen can refuse early
 * with a useful message. The repository is what actually enforces them — see
 * [ContactRepository] — and its verdict wins, because two devices can disagree
 * about the count and only the transaction is authoritative.
 */
class ContactsViewModel(
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState(isLoading = true))
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _events = Channel<ContactsEvent>(Channel.BUFFERED)
    val events: Flow<ContactsEvent> = _events.receiveAsFlow()

    init {
        observeContacts()
    }

    // ── Search and filter ─────────────────────────────────────────────────────

    /**
     * Updates the search query. Filtering is applied immediately in-memory via
     * [ContactsUiState.filteredContacts]; no database call is made.
     */
    fun onSearchChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /**
     * Selects a relationship chip filter. Combining with the active query is
     * handled by [ContactsUiState.filteredContacts].
     */
    fun onRelationshipFilterSelected(filter: ContactRelationshipFilter) {
        _uiState.update { it.copy(selectedRelationship = filter) }
    }

    // ── Add and edit ──────────────────────────────────────────────────────────

    /** Opens the editor for a new contact. */
    fun onAddClicked() {
        _events.trySend(ContactsEvent.ShowContactEditor(null))
    }

    /** Opens the editor on an existing contact. Always allowed. */
    fun onEditClicked(contact: EmergencyContact) {
        _events.trySend(ContactsEvent.ShowContactEditor(contact))
    }

    /**
     * Saves what the editor dialog collected.
     *
     * [contactId] is blank for a new contact and the existing id for an edit.
     * The phone is passed through as typed: the repository normalises it to
     * E.164, and it is the only layer that decides the stored form.
     */
    fun onEditorSubmitted(
        contactId: String,
        name: String,
        phone: String,
        relationship: String
    ) {
        if (_uiState.value.isMutating) return

        val message = when {
            !InputValidator.isValidContactName(name) -> MSG_NAME_INVALID
            !InputValidator.isValidPhone(phone) -> MSG_PHONE_INVALID
            !InputValidator.isValidContactRelationship(relationship) -> MSG_RELATIONSHIP_INVALID
            else -> null
        }
        if (message != null) {
            _events.trySend(ContactsEvent.ShowMessage(message))
            return
        }

        val contact = EmergencyContact(
            id = contactId,
            name = name,
            phone = phone,
            relationship = relationship
        )
        val isNew = contactId.isBlank()

        _uiState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            val result = if (isNew) {
                contactRepository.addContact(contact).map { }
            } else {
                contactRepository.updateContact(contact)
            }

            _uiState.update { it.copy(isMutating = false) }
            result
                .onSuccess {
                    _events.send(
                        ContactsEvent.ShowMessage(if (isNew) MSG_CONTACT_ADDED else MSG_CONTACT_SAVED)
                    )
                }
                .onFailure { _events.send(ContactsEvent.ShowMessage(messageFor(it))) }
        }
    }

    // ── Emergency designation ─────────────────────────────────────────────────

    /**
     * Toggles the emergency designation on [contact]. Contacts marked as
     * emergency sort to the top of the list and show an "Emergency" badge.
     * The action is immediately reversible by tapping the star again.
     */
    fun onEmergencyToggled(contact: EmergencyContact) {
        if (_uiState.value.isMutating) return
        val newValue = !contact.isEmergency

        _uiState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            contactRepository.toggleEmergency(contact.id, newValue)
                .also { _uiState.update { state -> state.copy(isMutating = false) } }
                .onFailure { _events.send(ContactsEvent.ShowMessage(messageFor(it))) }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /** Called after the user confirms the delete dialog. */
    fun onDeleteConfirmed(contactId: String) {
        if (_uiState.value.isMutating) return

        _uiState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            contactRepository.deleteContact(contactId)
                .also { _uiState.update { state -> state.copy(isMutating = false) } }
                .onSuccess { _events.send(ContactsEvent.ShowMessage(MSG_CONTACT_DELETED)) }
                .onFailure { _events.send(ContactsEvent.ShowMessage(messageFor(it))) }
        }
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    /**
     * Collects for the ViewModel's whole life, so an edit made on another
     * device shows up without the user leaving the tab.
     *
     * A failure leaves the last good list on screen rather than blanking it:
     * stale contacts are more use in an emergency than none.
     */
    private fun observeContacts() {
        viewModelScope.launch {
            contactRepository.observeContacts().collect { result ->
                result
                    .onSuccess { contacts ->
                        _uiState.update {
                            it.copy(isLoading = false, contacts = contacts, loadFailed = false)
                        }
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                        _events.send(ContactsEvent.ShowMessage(messageFor(error)))
                    }
            }
        }
    }

    // ── Errors ────────────────────────────────────────────────────────────────

    /** Wording for a [ContactError]. Never shows the exception text. */
    private fun messageFor(error: Throwable): String = when (error) {
        ContactError.MinimumContacts -> MSG_AT_MINIMUM
        ContactError.MaximumContacts -> MSG_GENERIC_FAILURE
        ContactError.Validation -> MSG_PHONE_INVALID
        ContactError.NotFound -> MSG_CONTACT_GONE
        ContactError.Network -> MSG_NO_CONNECTION
        ContactError.PermissionDenied -> MSG_REFUSED
        ContactError.NotSignedIn -> MSG_SESSION_EXPIRED
        else -> MSG_GENERIC_FAILURE
    }

    companion object {
        const val MSG_CONTACT_ADDED = "Contact added."
        const val MSG_CONTACT_SAVED = "Contact updated."
        const val MSG_CONTACT_DELETED = "Contact deleted."

        const val MSG_NAME_INVALID = "Enter the contact's name."
        const val MSG_PHONE_INVALID = "Enter a valid mobile number, like 09171234567."
        const val MSG_RELATIONSHIP_INVALID = "That relationship is too long."

        const val MSG_AT_MINIMUM =
            "The list can't go below ${InputValidator.MIN_CONTACTS}, because the band " +
                "needs someone to text. Add another contact first."

        const val MSG_CONTACT_GONE = "That contact no longer exists."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_REFUSED = "You don't have permission to change these contacts."
        const val MSG_SESSION_EXPIRED = "You are no longer signed in. Please sign in again."
        const val MSG_GENERIC_FAILURE = "Something went wrong. Try again."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ContactsViewModel(RepositoryProvider.contactRepository) }
        }
    }
}
