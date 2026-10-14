package com.example.guardband.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The verify-email link: apply the code, then say whether it worked.
 *
 * Shaped like [ResetPasswordViewModel][com.example.guardband.ui.reset.ResetPasswordViewModel]
 * on purpose, so the two email flows feel alike: the Activity parses the link
 * and hands over the code or null; the code is a private field here and never
 * reaches an extra, saved state, a message or a log. It does not survive
 * process death - the user taps the link again.
 *
 * **Needs no signed-in user.** The code is the authorisation, so a link opened
 * on a phone where nobody is signed in still verifies the address. When
 * someone *is* signed in, the session is reloaded afterwards so Account
 * information shows "Verified" without signing anyone out; that reload is best
 * effort, because Account information reloads again whenever it is shown.
 *
 * **What the result unlocks.** An unverified password account is held at
 * [VerifyRequiredActivity] by the Home gate; Continue goes through Splash to
 * Home, which lets a now-verified account in. If the reload here failed, the
 * gate screen reloads again as soon as it shows.
 */
class VerifyEmailViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    private val _events = Channel<VerifyEmailEvent>(Channel.BUFFERED)
    val events: Flow<VerifyEmailEvent> = _events.receiveAsFlow()

    /** Held only here, only for Retry. */
    private var oobCode: String? = null

    /**
     * The link, parsed by the Activity.
     *
     * @param code the verify-email `oobCode`, or null when the link is not a
     *   usable verify-email link.
     * @param unsupported true for a Firebase email link with a mode this app
     *   does not handle, which gets its own wording rather than "invalid".
     */
    fun onLinkOpened(code: String?, unsupported: Boolean) {
        if (_uiState.value.isBusy && oobCode != null) return
        when {
            unsupported -> _uiState.value = VerifyEmailUiState(
                stage = VerifyEmailStage.UNSUPPORTED,
                message = MSG_UNSUPPORTED
            )

            code.isNullOrBlank() -> fail(MSG_LINK_UNUSABLE)

            else -> {
                oobCode = code
                verify(code)
            }
        }
    }

    /** Offered only when the last attempt failed for want of a connection. */
    fun onRetryClicked() {
        val code = oobCode
        if (_uiState.value.isBusy || code == null || !_uiState.value.canRetry) return
        verify(code)
    }

    fun onContinueClicked() {
        if (_uiState.value.isBusy) return
        _events.trySend(VerifyEmailEvent.Continue)
    }

    private fun verify(code: String) {
        _uiState.value = VerifyEmailUiState(stage = VerifyEmailStage.VERIFYING)
        viewModelScope.launch {
            authRepository.applyEmailVerification(code)
                .onSuccess {
                    if (authRepository.isLoggedIn()) {
                        // Best effort: Account information reloads on its own.
                        authRepository.reloadUser()
                    }
                    _uiState.value = VerifyEmailUiState(
                        stage = VerifyEmailStage.VERIFIED,
                        message = MSG_VERIFIED
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        VerifyEmailUiState(
                            stage = VerifyEmailStage.FAILED,
                            message = messageFor(error),
                            canRetry = error == AuthError.Network
                        )
                    }
                }
        }
    }

    private fun fail(message: String) {
        _uiState.value = VerifyEmailUiState(stage = VerifyEmailStage.FAILED, message = message)
    }

    /** Never the exception text. */
    private fun messageFor(error: Throwable): String = when (error) {
        AuthError.ExpiredActionCode -> MSG_LINK_EXPIRED
        AuthError.InvalidActionCode -> MSG_LINK_USED
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        AuthError.UserDisabled -> MSG_ACCOUNT_UNAVAILABLE
        else -> MSG_LINK_UNUSABLE
    }

    companion object {
        const val MSG_VERIFIED = "Your email address is verified. You can close this, or continue to GuardBand."
        const val MSG_LINK_EXPIRED =
            "This verification link has expired. Open Account information in the app and tap Resend email."
        const val MSG_LINK_USED =
            "This verification link has already been used, or a newer one was sent. " +
                "Account information in the app shows whether your email is verified."
        const val MSG_LINK_UNUSABLE =
            "This verification link isn't valid. Open Account information in the app and tap Resend email."
        const val MSG_UNSUPPORTED =
            "This kind of email link isn't supported in the GuardBand app."
        const val MSG_NO_CONNECTION = "No connection. Check your network and tap Retry."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_ACCOUNT_UNAVAILABLE = "This account is no longer available."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { VerifyEmailViewModel(RepositoryProvider.authRepository) }
        }
    }
}
