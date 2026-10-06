package com.example.guardband.ui.signup

import androidx.lifecycle.ViewModel
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Sign-Up Step 3 — staging the emergency contacts.
 *
 * **It writes nothing.** Contacts are collected in memory and handed to the
 * Consent screen, which creates the account and commits the whole record in
 * one atomic write. That is what keeps an abandoned sign-up from leaving
 * anything behind, and it is why this ViewModel takes no repositories at all.
 *
 * **One contact is required; three are recommended.** The band texts these
 * people, so finishing with none would be a safety app with nobody to call.
 * Three is the figure the Contacts tab asks for
 * ([MIN_CONTACTS][InputValidator.MIN_CONTACTS]), and the banner keeps asking
 * until it is reached — but it stays a nudge, because a band in use with one
 * contact is better than a sign-up the user abandoned at the third.
 *
 * Staged contacts carry a local `draft-n` id so the list can diff and the
 * editor can tell add from edit. The repository assigns the real keys at the
 * final write, so these ids never reach the database.
 *
 * Flow: SignUpContactsActivity → SignUpConsentActivity
 */
class SignUpContactsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpContactsUiState())
    val uiState: StateFlow<SignUpContactsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignUpContactsEvent>(Channel.BUFFERED)
    val events: Flow<SignUpContactsEvent> = _events.receiveAsFlow()

    private var nextDraftId = 1

    /** Switches the screen into complete-profile mode. Called once, from the extras. */
    fun setCompleteProfileMode(completeProfile: Boolean) {
        _uiState.update { it.copy(completeProfile = completeProfile) }
    }

    fun onAddClicked() {
        if (!InputValidator.canAddContact(_uiState.value.contacts.size)) {
            _events.trySend(SignUpContactsEvent.ShowMessage(MSG_MAXIMUM_REACHED))
            return
        }
        _events.trySend(SignUpContactsEvent.ShowContactEditor(null))
    }

    fun onEditClicked(contact: EmergencyContact) {
        _events.trySend(SignUpContactsEvent.ShowContactEditor(contact))
    }

    /**
     * Adds or replaces a staged contact.
     *
     * A blank [contactId] means add. The phone is stored **as typed**: the
     * repository normalises to E.164 at the write, and it is the only layer
     * that decides the stored form.
     */
    fun onEditorSubmitted(
        contactId: String,
        name: String,
        phone: String,
        relationship: String
    ) {
        val error = when {
            !InputValidator.isValidContactName(name) -> MSG_CONTACT_NAME_INVALID
            !InputValidator.isValidPhone(phone) -> MSG_CONTACT_PHONE_INVALID
            !InputValidator.isValidContactRelationship(relationship) ->
                MSG_CONTACT_RELATIONSHIP_INVALID
            else -> null
        }
        if (error != null) {
            _events.trySend(SignUpContactsEvent.ShowMessage(error))
            return
        }

        val current = _uiState.value.contacts
        val isAdd = contactId.isBlank()

        if (isAdd && !InputValidator.canAddContact(current.size)) {
            _events.trySend(SignUpContactsEvent.ShowMessage(MSG_MAXIMUM_REACHED))
            return
        }

        val edited = EmergencyContact(
            id = if (isAdd) "draft-${nextDraftId++}" else contactId,
            name = name.trim(),
            phone = phone.trim(),
            relationship = relationship.trim()
        )

        publish(
            if (isAdd) current + edited
            else current.map { if (it.id == edited.id) edited else it }
        )
    }

    /**
     * Removes a staged contact.
     *
     * No minimum is enforced here, unlike the repository's delete: nothing has
     * been written, so there is no stored record to protect. Continue is what
     * requires one.
     */
    fun onDeleteClicked(contactId: String) {
        publish(_uiState.value.contacts.filterNot { it.id == contactId })
    }

    /** Hands the whole wizard to the Consent screen. */
    fun onContinueClicked(name: String, email: String, password: String) {
        val state = _uiState.value
        if (state.contacts.size < MIN_AT_SIGNUP) {
            _events.trySend(SignUpContactsEvent.ShowMessage(MSG_ONE_CONTACT_REQUIRED))
            return
        }

        _events.trySend(
            SignUpContactsEvent.NavigateToConsent(
                name = name,
                email = email,
                password = password,
                contacts = state.contacts,
                completeProfile = state.completeProfile
            )
        )
    }

    /** The one place the derived flags are recomputed, so they cannot drift. */
    private fun publish(contacts: List<EmergencyContact>) {
        _uiState.update {
            it.copy(
                contacts = contacts,
                canContinue = contacts.size >= MIN_AT_SIGNUP,
                showMinimumNotice = contacts.size < InputValidator.MIN_CONTACTS
            )
        }
    }

    companion object {
        /** What sign-up actually requires. The banner asks for three. */
        const val MIN_AT_SIGNUP = 1

        const val MSG_ONE_CONTACT_REQUIRED =
            "Add at least one emergency contact so your band has someone to text."
        const val MSG_MAXIMUM_REACHED = "You can add up to ${InputValidator.MAX_CONTACTS} contacts."
        const val MSG_CONTACT_NAME_INVALID = "Enter the contact's name."
        const val MSG_CONTACT_PHONE_INVALID = "Enter a valid mobile number, like 09171234567."
        const val MSG_CONTACT_RELATIONSHIP_INVALID = "That relationship is too long."
    }
}
