package com.example.guardband.ui.dashboard

import com.example.guardband.base.BasePresenter
import com.example.guardband.data.repository.AuthRepository

class MainPresenter(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : BasePresenter<MainContract.View>(), MainContract.Presenter {

    override fun checkLoginStatus() {
        // Check if user is logged in - currently allowing partial signup flow
        // This is a no-op check but maintains the pattern for future enhancements
        val isLoggedIn = authRepository.isLoggedIn()
        // Currently not enforcing login, allowing wizard completion without auth
    }

    override fun onLogoutClicked() {
        authRepository.signOut()
        view?.navigateToLogin()
    }
}
