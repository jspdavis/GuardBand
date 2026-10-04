package com.example.guardband.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.ContactRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Contacts tab: lists the emergency contacts and deletes one after the
 * Fragment's confirmation dialog. Add and edit (rest of FR-06) come later.
 *
 * Loads once when created; the tab Fragment is kept alive by the host, so
 * this survives tab switches.
 */
class ContactsViewModel(
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState(isLoading = true))
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _events = Channel<ContactsEvent>(Channel.BUFFERED)
    val events: Flow<ContactsEvent> = _events.receiveAsFlow()

    init {
        loadContacts()
    }

    /** Called after the user confirms the delete dialog. */
    fun onDeleteConfirmed(contactId: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            contactRepository.deleteContact(contactId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            contacts = state.contacts.filterNot { it.id == contactId }
                        )
                    }
                    _events.send(ContactsEvent.ShowMessage(MSG_CONTACT_DELETED))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ContactsEvent.ShowMessage(error.message ?: MSG_DELETE_FAILED))
                }
        }
    }

    private fun loadContacts() {
        viewModelScope.launch {
            contactRepository.getContacts()
                .onSuccess { contacts ->
                    _uiState.update { it.copy(isLoading = false, contacts = contacts) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ContactsEvent.ShowMessage(error.message ?: MSG_LOAD_FAILED))
                }
        }
    }

    companion object {
        const val MSG_CONTACT_DELETED = "Contact deleted."
        const val MSG_DELETE_FAILED = "Couldn't delete the contact. Try again."
        const val MSG_LOAD_FAILED = "Couldn't load your contacts."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ContactsViewModel(RepositoryProvider.contactRepository) }
        }
    }
}
