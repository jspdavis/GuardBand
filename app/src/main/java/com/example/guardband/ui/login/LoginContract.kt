package com.example.guardband.ui.login

import com.example.guardband.base.BaseView
import com.example.guardband.data.model.User

interface LoginContract {

    interface View : BaseView {
        fun navigateToMain()
        fun navigateToSignUp()
        fun navigateToSignUpLocation(user: User)
        fun navigateToForgotPassword()
        fun launchGoogleSignIn()
        /** Show an inline validation error on the identifier (phone/email) field. */
        fun showIdentifierError(message: String?)
    }

    interface Presenter {
        fun onLoginClicked(identifier: String, password: String)
        fun onGoogleSignInClicked()
        fun onGoogleIdTokenReceived(idToken: String)
        fun onGoogleSignInFailed(message: String)
        fun onCreateAccountClicked()
        fun onForgotPasswordClicked()
    }
}
