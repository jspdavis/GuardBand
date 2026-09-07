package com.example.guardband.ui.auth

import com.example.guardband.base.BasePresenter
import com.example.guardband.data.MockRepository
import com.example.guardband.data.UserModel

// ─────────────────────────────────────────────────────────────────────────────
// Login
// ─────────────────────────────────────────────────────────────────────────────

class LoginPresenter : BasePresenter<AuthContract.LoginView>(),
    AuthContract.LoginPresenter {

    override fun onLoginClicked(email: String, password: String) {
        if (!validateCredentials(email, password)) return

        view?.showLoading()
        MockRepository.login(
            email = email,
            password = password,
            onSuccess = {
                view?.hideLoading()
                view?.navigateToDashboard()
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onSignUpClicked() {
        view?.navigateToSignUp()
    }

    override fun onForgotPasswordClicked() {
        view?.navigateToForgotPassword()
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateCredentials(email: String, password: String): Boolean {
        if (email.isBlank()) {
            view?.showError("Email is required.")
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            view?.showError("Enter a valid email address.")
            return false
        }
        if (password.isBlank()) {
            view?.showError("Password is required.")
            return false
        }
        return true
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sign-Up: Step 1 – Name
// ─────────────────────────────────────────────────────────────────────────────

class SignUpNamePresenter : BasePresenter<AuthContract.SignUpNameView>(),
    AuthContract.SignUpNamePresenter {

    override fun onNextClicked(name: String) {
        if (name.isBlank()) {
            view?.showError("Please enter your full name.")
            return
        }
        view?.navigateToSignUpLocation(name.trim())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sign-Up: Step 2 – Location
// ─────────────────────────────────────────────────────────────────────────────

class SignUpLocationPresenter : BasePresenter<AuthContract.SignUpLocationView>(),
    AuthContract.SignUpLocationPresenter {

    override fun onNextClicked(name: String, location: String) {
        if (location.isBlank()) {
            view?.showError("Please enter your location.")
            return
        }
        view?.navigateToSignUpContacts(name, location.trim())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sign-Up: Step 3 – Contacts & final submission
// ─────────────────────────────────────────────────────────────────────────────

class SignUpContactsPresenter : BasePresenter<AuthContract.SignUpContactsView>(),
    AuthContract.SignUpContactsPresenter {

    override fun onSubmitClicked(
        name: String,
        location: String,
        email: String,
        password: String
    ) {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            view?.showError("Enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            view?.showError("Password is required.")
            return
        }

        val newUser = UserModel(
            name = name,
            email = email,
            location = location,
            password = password
        )

        view?.showLoading()
        MockRepository.register(
            user = newUser,
            onSuccess = {
                view?.hideLoading()
                view?.navigateToDashboard()
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }
}
