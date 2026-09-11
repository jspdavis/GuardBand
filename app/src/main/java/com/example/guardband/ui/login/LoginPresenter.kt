package com.example.guardband.ui.login

import android.util.Patterns
import com.example.guardband.base.BasePresenter
import com.example.guardband.data.repository.AuthRepository

class LoginPresenter(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : BasePresenter<LoginContract.View>(), LoginContract.Presenter {

    // ── Validation helpers ────────────────────────────────────────────────────

    /**
     * Returns true when [value] looks like a phone number:
     * optional leading '+', then 10–15 digits.
     */
    private fun isPhoneNumber(value: String): Boolean =
        Regex("^\\+?\\d{10,15}$").matches(value.trim())

    /**
     * Returns true when [value] is a well-formed email address
     * according to Android's [Patterns.EMAIL_ADDRESS].
     */
    private fun isEmail(value: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()

    // ── Presenter contract ────────────────────────────────────────────────────

    override fun onLoginClicked(identifier: String, password: String) {
        val trimmedId = identifier.trim()

        // 1) Both fields are required.
        if (trimmedId.isBlank()) {
            view?.showIdentifierError("This field is required.")
            return
        } else {
            view?.showIdentifierError(null)
        }

        if (password.isBlank()) {
            view?.showError("Password is required.")
            return
        }

        // 2) Dynamically validate the identifier format before hitting Firebase.
        //    This prevents Firebase from throwing "Email is badly formatted" when
        //    the user enters a phone number or a garbled string.
        if (!isPhoneNumber(trimmedId) && !isEmail(trimmedId)) {
            view?.showIdentifierError("Invalid phone number or email format.")
            return
        }

        view?.showLoading()
        authRepository.login(
            identifier = trimmedId,
            password = password,
            onSuccess = {
                view?.hideLoading()
                // Wireframe: successful login always lands on Main/Dashboard.
                view?.navigateToMain()
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onGoogleSignInClicked() {
        view?.launchGoogleSignIn()
    }

    override fun onGoogleIdTokenReceived(idToken: String) {
        view?.showLoading()
        authRepository.signInWithGoogleIdToken(
            idToken = idToken,
            onSuccess = { user, isNewUser ->
                view?.hideLoading()
                when {
                    isNewUser || !user.profileComplete -> {
                        // New Google users land on Sign-Up Step 2 (Location).
                        view?.navigateToSignUpLocation(user)
                    }
                    else -> view?.navigateToMain()
                }
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    override fun onGoogleSignInFailed(message: String) {
        view?.showError(message)
    }

    override fun onCreateAccountClicked() {
        view?.navigateToSignUp()
    }

    override fun onForgotPasswordClicked() {
        view?.navigateToForgotPassword()
    }
}
