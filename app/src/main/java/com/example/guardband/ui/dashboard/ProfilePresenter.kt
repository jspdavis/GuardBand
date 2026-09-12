package com.example.guardband.ui.dashboard

import com.example.guardband.base.BasePresenter
import com.example.guardband.data.repository.AuthRepository

class ProfilePresenter(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : BasePresenter<ProfileContract.View>(), ProfileContract.Presenter {

    override fun loadProfile() {
        authRepository.fetchProfile(
            onSuccess = { user ->
                view?.displayProfile(user)
            },
            onError = {
                view?.displaySignedInMessage()
            }
        )
    }
}
