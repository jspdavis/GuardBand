package com.example.guardband.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.data.repository.deleteContactSuspend
import com.example.guardband.data.repository.getContactsSuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val contactRepository: ContactRepository = ContactRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = ContactsUiState.Loading
            try {
                val list = contactRepository.getContactsSuspend()
                _uiState.value = if (list.isEmpty()) {
                    ContactsUiState.Empty
                } else {
                    ContactsUiState.Contacts(list)
                }
            } catch (e: Exception) {
                _uiState.value = ContactsUiState.Error(e.message ?: "Failed to load contacts.")
            }
        }
    }

    fun onDeleteContact(contactId: String) {
        viewModelScope.launch {
            _uiState.value = ContactsUiState.Loading
            try {
                contactRepository.deleteContactSuspend(contactId)
                loadContacts()
            } catch (e: Exception) {
                _uiState.value = ContactsUiState.Error(e.message ?: "Failed to delete contact.")
            }
        }
    }

    class Factory(
        private val contactRepository: ContactRepository = ContactRepository.getInstance()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ContactsViewModel(contactRepository) as T
    }
}
