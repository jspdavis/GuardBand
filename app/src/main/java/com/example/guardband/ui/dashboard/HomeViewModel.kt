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

class HomeViewModel(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val user = authRepository.fetchProfileSuspend()
                val name = user.fullName.takeIf { it.isNotBlank() }
                _uiState.value = if (name != null) {
                    HomeUiState.WelcomeMessage(name)
                } else {
                    HomeUiState.DefaultMessage
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.DefaultMessage
            }
        }
    }

    class Factory(
        private val authRepository: AuthRepository = AuthRepository.getInstance()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(authRepository) as T
    }
}
