package com.example.guardband.ui.dashboard

import com.example.guardband.base.BasePresenter
import com.example.guardband.data.repository.AuthRepository

class HomePresenter(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : BasePresenter<HomeContract.View>(), HomeContract.Presenter {

    override fun loadProfile() {
        authRepository.fetchProfile(
            onSuccess = { user ->
                val name = user.fullName.takeIf { it.isNotBlank() }
                if (name != null) {
                    view?.displayWelcomeMessage(name)
                } else {
                    view?.displayDefaultMessage()
                }
            },
            onError = {
                view?.displayDefaultMessage()
            }
        )
    }
}
