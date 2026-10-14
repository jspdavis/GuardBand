package com.example.guardband.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.ui.auth.GoogleIdTokenResult
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Delete account, driven from the danger zone at the bottom of Account
 * information ([AccountActivity]), one level below Settings so a destructive
 * action is not a stray tap away.
 *
 * Its own ViewModel rather than part of [AccountViewModel], which only shows
 * the account. This flow has six steps, two retry guards and a credential
 * prompt, and keeping it apart keeps a destructive multi-step operation out
 * of a read-only screen's state.
 *
 * **The order of operations is the whole design:**
 *
 * 1. **Re-authenticate.** Firebase refuses `delete()` on a session that has
 *    been sitting around ([AuthError.RequiresRecentLogin]), and a destructive
 *    action should ask anyway. A password account types its password; a Google
 *    account goes back through the chooser.
 * 2. **Replace `users/{uid}` with the deletion marker** - profile, every
 *    emergency contact and the consent record go, and `deletion_pending: true`
 *    lands, in one write - **while still signed in**. The rules authorise it
 *    with `auth.uid`, so after step 3 there is no session left that could do
 *    it and the data would be orphaned for good.
 * 3. **Delete the Auth user.**
 * 4. **Go to Login** with the back stack cleared. The Fragment also forgets the
 *    Google account, exactly as Log Out does.
 *
 * **A half-finished delete resumes, here or on the next sign-in.** The marker
 * from step 2 is what survives a user giving up after step 3 failed: the Home
 * gate reads it and sends them here, and this ViewModel reads it on start and
 * opens straight into "finish deleting" ([MSG_RESUME_PENDING]). Without it, the
 * next sign-in used to provision a fresh, thin profile and the account came
 * back. Once the data is gone, **Log Out** is offered beside Retry: the marker
 * keeps the deletion waiting, so walking away no longer strands anyone.
 *
 * Within one visit, [DeleteAccountUiState.profileDeleted] and
 * [DeleteAccountUiState.reauthenticated] are the guards, in the same spirit as
 * `accountCreated` in
 * [SignUpConsentViewModel][com.example.guardband.ui.signup.SignUpConsentViewModel]:
 * [onRetryClicked] re-runs only what has not landed. Once the data is gone the
 * state never returns to [DeleteAccountStep.IDLE], because that would leave an
 * account with nothing behind it and no visible way to finish.
 *
 * **`devices/` is never touched.** `guardband-001` is shared by every install
 * until pairing exists, so one user deleting their account must not take the
 * band's data with them.
 *
 * Nothing here logs. The password is passed straight to the repository and
 * held nowhere; no uid, email or Firebase text reaches a message.
 */
class DeleteAccountViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    private val _events = Channel<DeleteAccountEvent>(Channel.BUFFERED)
    val events: Flow<DeleteAccountEvent> = _events.receiveAsFlow()

    init {
        resumeIfDeletionPending()
    }

    // ── An unfinished deletion from before ────────────────────────────────────

    /**
     * Opens straight into "finish deleting" when this account's data was
     * already deleted on an earlier visit and only the sign-in survived.
     *
     * The state is exactly what a failure after step 2 leaves in-session -
     * [DeleteAccountStep.FAILED], [DeleteAccountUiState.profileDeleted], no
     * fresh credential - so Retry, the row and Log Out all behave the way they
     * would have then. Retry asks for the password or the Google account again
     * before anything is called: a destructive step never runs on an old
     * credential.
     *
     * A failed read changes nothing: the screen stays as it was, and the
     * ordinary flow still writes the marker over the marker if the user
     * deletes. A flow the user has already started is never overwritten.
     */
    private fun resumeIfDeletionPending() {
        val user = authRepository.currentUser() ?: return
        viewModelScope.launch {
            val state = userProfileRepository.profileState(user.id).getOrNull()
            if (state != ProfileState.DELETION_PENDING) return@launch
            val method = reauthMethodFor(authRepository.currentUserProviders())
            _uiState.update {
                if (it.step != DeleteAccountStep.IDLE) {
                    it
                } else {
                    it.copy(
                        step = DeleteAccountStep.FAILED,
                        method = method,
                        profileDeleted = true,
                        reauthenticated = false,
                        errorMessage = MSG_RESUME_PENDING
                    )
                }
            }
        }
    }

    // ── Starting ──────────────────────────────────────────────────────────────

    /**
     * The Delete account row. Works out how this user can prove who they are,
     * then puts up the modal.
     *
     * [SignInProvider.PASSWORD] wins a mixed account. Firebase's
     * one-account-per-email setting means this app never creates one - a second
     * method comes back as [AuthError.AccountExistsWithDifferentCredential]
     * instead of linking - so the case is theoretical, and for re-authentication
     * either credential satisfies Firebase equally. That makes the choice a UX
     * one, and typing a password needs no chooser, no Play Services and no
     * second account picked by mistake.
     *
     * No recognised provider means the flow cannot ask for anything, so it
     * refuses to start rather than guessing.
     */
    fun onDeleteAccountClicked() {
        val state = _uiState.value
        if (state.isBusy || state.step == DeleteAccountStep.DONE) return

        // An unfinished delete resumes instead of re-asking: the data is
        // already gone, so the modal would describe work that is half done.
        if (state.isUnfinished) {
            onRetryClicked()
            return
        }

        if (authRepository.currentUser() == null) {
            sessionGoneBeforeAnythingChanged()
            return
        }

        val method = reauthMethodFor(authRepository.currentUserProviders())
        if (method == null) {
            _events.trySend(DeleteAccountEvent.ShowMessage(MSG_CANNOT_REAUTHENTICATE))
            return
        }

        _uiState.update {
            it.copy(step = DeleteAccountStep.CONFIRMING, method = method, errorMessage = null)
        }
        _events.trySend(DeleteAccountEvent.ShowConfirmation)
    }

    /**
     * Log Out, offered only once the data is gone and the sign-in is not.
     *
     * Safe to walk away at that point because the deletion marker is already
     * stored: the next sign-in, on this phone or another, lands back here to
     * finish. Before the data is gone there is nothing half-done to leave, and
     * Settings has the ordinary Log Out.
     */
    fun onLogOutClicked() {
        val state = _uiState.value
        if (state.isBusy || !state.isUnfinished) return
        authRepository.signOut()
        _events.trySend(DeleteAccountEvent.NavigateToLogin)
    }

    /** Cancel, from the modal or the password prompt. Only legal before anything is written. */
    fun onCancelled() {
        val state = _uiState.value
        if (state.isBusy || state.profileDeleted) return
        _uiState.update {
            it.copy(step = DeleteAccountStep.IDLE, method = null, errorMessage = null)
        }
    }

    /** Delete, in the modal. Asks for whatever [DeleteAccountUiState.method] needs. */
    fun onConfirmed() {
        val state = _uiState.value
        if (state.step != DeleteAccountStep.CONFIRMING) return
        askForCredential(state.method)
    }

    // ── Re-authentication ─────────────────────────────────────────────────────

    /**
     * The password from the prompt.
     *
     * Only blankness is checked. The app's eight-character rule is for
     * choosing a password, not for proving one: an older account may hold a
     * shorter one, and rejecting it here would lock that user out of their own
     * delete.
     *
     * The email is never passed along - the repository takes it from the live
     * session, so this cannot re-authenticate against another record.
     */
    fun onPasswordSubmitted(password: String) {
        val state = _uiState.value
        if (state.isBusy) return
        if (InputValidator.isBlank(password)) {
            _events.trySend(DeleteAccountEvent.ShowMessage(MSG_PASSWORD_REQUIRED))
            _events.trySend(DeleteAccountEvent.AskForPassword)
            return
        }

        _uiState.update { it.copy(step = DeleteAccountStep.REAUTHENTICATING, errorMessage = null) }
        viewModelScope.launch {
            authRepository.reauthenticateWithPassword(password)
                .onSuccess { onReauthenticated() }
                .onFailure { error -> failReauth(error) }
        }
    }

    /** A fresh Google token from the chooser. */
    fun onGoogleIdToken(idToken: String) {
        if (_uiState.value.isBusy) return

        _uiState.update { it.copy(step = DeleteAccountStep.REAUTHENTICATING, errorMessage = null) }
        viewModelScope.launch {
            authRepository.reauthenticateWithGoogle(idToken)
                .onSuccess { onReauthenticated() }
                .onFailure { error -> failReauth(error) }
        }
    }

    /**
     * The chooser did not produce a token.
     *
     * A cancel is silent and simply stops: the user backed out of a destructive
     * action on purpose, and nothing has been written. Returning to
     * [DeleteAccountStep.IDLE] is safe for exactly that reason - this can only
     * be reached before the first write.
     */
    fun onGoogleError(result: GoogleIdTokenResult) {
        if (result == GoogleIdTokenResult.Cancelled) {
            _uiState.update {
                it.copy(step = DeleteAccountStep.IDLE, method = null, errorMessage = null)
            }
            return
        }
        fail(
            when (result) {
                GoogleIdTokenResult.NoGoogleAccount -> MSG_NO_GOOGLE_ACCOUNT
                GoogleIdTokenResult.Unavailable -> MSG_GOOGLE_UNAVAILABLE
                GoogleIdTokenResult.Network -> MSG_NO_CONNECTION
                else -> MSG_GOOGLE_FAILED
            }
        )
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    /**
     * Resumes at the first step that has not landed.
     *
     * Without a fresh credential it asks for one again; with one it re-runs the
     * data removal and then the account deletion, skipping whichever already
     * succeeded. There is nothing to undo first, because each step is a single
     * write.
     */
    fun onRetryClicked() {
        val state = _uiState.value
        if (state.isBusy) return
        if (!state.reauthenticated) {
            askForCredential(state.method)
            return
        }
        deleteNow()
    }

    // ── The deletes ───────────────────────────────────────────────────────────

    private fun onReauthenticated() {
        _uiState.update { it.copy(reauthenticated = true) }
        deleteNow()
    }

    /**
     * Data first, then the account.
     *
     * The uid comes from the live session on every attempt rather than being
     * held since the first one - the same rule
     * [SignUpConsentViewModel][com.example.guardband.ui.signup.SignUpConsentViewModel]'s
     * retry follows.
     */
    private fun deleteNow() {
        val user = authRepository.currentUser()
        if (user == null) {
            if (_uiState.value.profileDeleted) {
                fail(MSG_SESSION_LOST_MID_DELETE)
            } else {
                sessionGoneBeforeAnythingChanged()
            }
            return
        }

        _uiState.update { it.copy(step = DeleteAccountStep.DELETING, errorMessage = null) }
        viewModelScope.launch {
            if (!_uiState.value.profileDeleted) {
                val error = userProfileRepository.markDeletionPending(user.id).exceptionOrNull()
                if (error != null) {
                    // Nothing is gone yet, and the account is untouched too, so
                    // Retry is the honest offer rather than starting over.
                    fail(dataMessageFor(error))
                    return@launch
                }
                _uiState.update { it.copy(profileDeleted = true) }
            }

            authRepository.deleteAccount()
                .onSuccess {
                    // delete() already clears the session; this is belt and
                    // braces, because a session that somehow survived would put
                    // a deleted account straight back through the Splash gate.
                    authRepository.signOut()
                    _uiState.update {
                        it.copy(step = DeleteAccountStep.DONE, errorMessage = null)
                    }
                    _events.send(DeleteAccountEvent.NavigateToLogin)
                }
                .onFailure { error ->
                    // The data is gone and the account is not. Clearing
                    // `reauthenticated` on RequiresRecentLogin is what sends
                    // Retry back through the prompt instead of straight into a
                    // call Firebase will refuse again.
                    if (error == AuthError.RequiresRecentLogin) {
                        _uiState.update { it.copy(reauthenticated = false) }
                    }
                    fail(accountMessageFor(error))
                }
        }
    }

    // ── Failure plumbing ──────────────────────────────────────────────────────

    /**
     * How this account proves who it is. [SignInProvider.PASSWORD] wins a
     * mixed account - see [onDeleteAccountClicked]. Null means none this app
     * can ask for.
     */
    private fun reauthMethodFor(providers: Set<SignInProvider>): SignInProvider? = when {
        SignInProvider.PASSWORD in providers -> SignInProvider.PASSWORD
        SignInProvider.GOOGLE in providers -> SignInProvider.GOOGLE
        else -> null
    }

    private fun askForCredential(method: SignInProvider?) {
        when (method) {
            SignInProvider.PASSWORD -> {
                _uiState.update { it.copy(step = DeleteAccountStep.CONFIRMING) }
                _events.trySend(DeleteAccountEvent.AskForPassword)
            }

            SignInProvider.GOOGLE -> {
                _uiState.update { it.copy(step = DeleteAccountStep.CONFIRMING) }
                _events.trySend(DeleteAccountEvent.RequestGoogleIdToken)
            }

            // Only reachable if the flow was somehow entered without a method.
            null -> fail(MSG_CANNOT_REAUTHENTICATE)
        }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(step = DeleteAccountStep.FAILED, errorMessage = message) }
    }

    private fun failReauth(error: Throwable) {
        if (error == AuthError.NotSignedIn) {
            sessionGoneBeforeAnythingChanged()
            return
        }
        fail(reauthMessageFor(error))
    }

    /**
     * The session died with nothing written, so there is no account state to
     * explain - only a dead session, and Login is the one honest destination.
     */
    private fun sessionGoneBeforeAnythingChanged() {
        _uiState.update {
            it.copy(step = DeleteAccountStep.IDLE, method = null, errorMessage = null)
        }
        _events.trySend(DeleteAccountEvent.ShowMessage(MSG_SESSION_EXPIRED))
        _events.trySend(DeleteAccountEvent.NavigateToLogin)
    }

    // ── Wording ───────────────────────────────────────────────────────────────

    /** A failure proving who they are. Never shows the exception text. */
    private fun reauthMessageFor(error: Throwable): String = when (error) {
        AuthError.InvalidCredentials -> MSG_WRONG_PASSWORD
        AuthError.UserMismatch -> MSG_WRONG_GOOGLE_ACCOUNT
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        AuthError.UserDisabled -> MSG_ACCOUNT_UNAVAILABLE
        else -> MSG_REAUTH_FAILED
    }

    /** A failure removing `users/{uid}`. Nothing is deleted yet when this is shown. */
    private fun dataMessageFor(error: Throwable): String = when (error) {
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.PermissionDenied -> MSG_DATA_DELETE_REFUSED
        else -> MSG_DATA_DELETE_FAILED
    }

    /**
     * A failure deleting the Auth user, which reads differently: the data is
     * already gone, so the message says what is left to finish rather than that
     * the delete failed.
     */
    private fun accountMessageFor(error: Throwable): String = when (error) {
        AuthError.RequiresRecentLogin -> MSG_NEEDS_RECENT_LOGIN
        AuthError.Network -> MSG_NO_CONNECTION_UNFINISHED
        else -> MSG_ACCOUNT_DELETE_FAILED
    }

    companion object {
        const val MSG_CANNOT_REAUTHENTICATE =
            "We can't confirm it's you on this device. Sign in again, then delete your account."
        const val MSG_PASSWORD_REQUIRED = "Enter your password to confirm."
        const val MSG_WRONG_PASSWORD = "That password is incorrect."
        const val MSG_WRONG_GOOGLE_ACCOUNT =
            "That's a different Google account. Choose the one you signed in with."
        const val MSG_NO_GOOGLE_ACCOUNT = "No Google account is available on this device."
        const val MSG_GOOGLE_UNAVAILABLE = "Google sign-in isn't available on this device."
        const val MSG_GOOGLE_FAILED = "Could not confirm your Google account. Please try again."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_ACCOUNT_UNAVAILABLE = "This account is no longer available."
        const val MSG_REAUTH_FAILED = "Could not confirm it's you. Please try again."
        const val MSG_SESSION_EXPIRED = "You are no longer signed in. Please sign in again."

        const val MSG_DATA_DELETE_FAILED = "Could not delete your data. Tap Retry."
        const val MSG_DATA_DELETE_REFUSED = "Deleting your data was refused. Tap Retry."

        /** The data is gone and the sign-in is not, so Retry finishes rather than restarts. */
        const val MSG_ACCOUNT_DELETE_FAILED =
            "Your data is deleted, but your sign-in is still active. Tap Retry to finish."
        const val MSG_NO_CONNECTION_UNFINISHED =
            "Your data is deleted, but your sign-in is still active. Reconnect and tap Retry."
        const val MSG_NEEDS_RECENT_LOGIN =
            "Your data is deleted. Confirm it's you once more to remove your sign-in."
        const val MSG_SESSION_LOST_MID_DELETE =
            "Your data is deleted, but you were signed out before your sign-in could be " +
                "removed. Sign in again to finish."

        /** Opening the screen on an account whose earlier deletion did not finish. */
        const val MSG_RESUME_PENDING =
            "Your account deletion didn't finish. Your data is already deleted; " +
                "tap Retry and confirm it's you to remove your sign-in."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeleteAccountViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.userProfileRepository
                )
            }
        }
    }
}
