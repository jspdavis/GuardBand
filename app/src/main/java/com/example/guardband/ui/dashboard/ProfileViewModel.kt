package com.example.guardband.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.fetchProfileSuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val user = authRepository.fetchProfileSuspend()
                _uiState.value = ProfileUiState.Loaded(user)
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.SignedIn
            }
        }
    }

    class Factory(
        private val authRepository: AuthRepository = AuthRepository.getInstance()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(authRepository) as T
    }
}
