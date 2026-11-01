package com.example.guardband.ui.signup

import androidx.lifecycle.ViewModel
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.repository.ContactError
import com.example.guardband.data.repository.ContactFields
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
 * Flow: SignUpContactsActivity → SignUpConsentActivity
 */
class SignUpContactsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpContactsUiState())
    val uiState: StateFlow<SignUpContactsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignUpContactsEvent>(Channel.BUFFERED)
    val events: Flow<SignUpContactsEvent> = _events.receiveAsFlow()

    var ownPhone: String? = null

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
     */
    fun onEditorSubmitted(
        contactId: String,
        name: String,
        phone: String,
        relationship: String
    ) {
        val draft = EmergencyContact(
            id = if (contactId.isBlank()) "draft" else contactId,
            name = name,
            phone = phone,
            relationship = relationship
        )
        try {
            ContactFields.validated(draft, ownPhone)
        } catch (e: ContactError.SelfPhone) {
            _events.trySend(SignUpContactsEvent.ShowMessage(MSG_SELF_PHONE))
            return
        } catch (e: Exception) {
            val error = when {
                !InputValidator.isValidContactName(name) -> MSG_CONTACT_NAME_INVALID
                !InputValidator.isValidPhone(phone) -> MSG_CONTACT_PHONE_INVALID
                !InputValidator.isValidContactRelationship(relationship) ->
                    MSG_CONTACT_RELATIONSHIP_INVALID
                else -> MSG_CONTACT_PHONE_INVALID
            }
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
        const val MIN_AT_SIGNUP = 1

        const val MSG_SELF_PHONE = "Guardian phone number cannot be the same as the dependent's phone number."
        const val MSG_MAXIMUM_REACHED = "You can add up to 5 emergency contacts."
        const val MSG_CONTACT_NAME_INVALID = "Please enter the contact's name."
        const val MSG_CONTACT_PHONE_INVALID = "Please enter a valid phone number."
        const val MSG_CONTACT_RELATIONSHIP_INVALID = "Please choose a relationship."
        const val MSG_ONE_CONTACT_REQUIRED = "Add at least one emergency contact to continue."
    }
}
