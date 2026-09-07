package com.example.guardband.ui.auth

import com.example.guardband.base.BaseView

/**
 * MVP contract for the entire auth flow (Login + multi-step Sign-Up).
 * Each inner interface pairs a View with its Presenter counterpart.
 */
interface AuthContract {

    // ── Login ────────────────────────────────────────────────────────────────

    interface LoginView : BaseView {
        fun navigateToDashboard()
        fun navigateToSignUp()
        fun navigateToForgotPassword()
    }

    interface LoginPresenter {
        fun onLoginClicked(email: String, password: String)
        fun onSignUpClicked()
        fun onForgotPasswordClicked()
    }

    // ── Sign-Up: Step 1 – Name ────────────────────────────────────────────────

    interface SignUpNameView : BaseView {
        fun navigateToSignUpLocation(name: String)
    }

    interface SignUpNamePresenter {
        fun onNextClicked(name: String)
    }

    // ── Sign-Up: Step 2 – Location ────────────────────────────────────────────

    interface SignUpLocationView : BaseView {
        fun navigateToSignUpContacts(name: String, location: String)
    }

    interface SignUpLocationPresenter {
        fun onNextClicked(name: String, location: String)
    }

    // ── Sign-Up: Step 3 – Contacts ────────────────────────────────────────────

    interface SignUpContactsView : BaseView {
        fun navigateToDashboard()
    }

    interface SignUpContactsPresenter {
        fun onSubmitClicked(
            name: String,
            location: String,
            email: String,
            password: String
        )
    }
}
