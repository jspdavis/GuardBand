package com.example.guardband.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.guardband.data.repository.AuthRepository

class SplashViewModel(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : ViewModel() {

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    class Factory(
        private val authRepository: AuthRepository = AuthRepository.getInstance()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SplashViewModel(authRepository) as T
    }
}
