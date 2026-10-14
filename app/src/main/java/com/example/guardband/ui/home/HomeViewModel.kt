package com.example.guardband.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.ui.account.EmailVerification
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Host-level logic for [HomeActivity]: the second auth gate, requests to
 * open the pushed screens (Settings, Notifications), and the Track avatar's
 * shortcut to the Profile tab. No UiState — which
 * fragment is on screen is navigation, owned by the Activity.
 *
 * Scoped to the Activity, so tab Fragments share this instance through
 * `activityViewModels { HomeViewModel.Factory }` and forward top-bar clicks here.
 *
 * Auth gate: a fresh ViewModel re-checks the session, because Android can
 * restore this Activity directly without going through Splash.
 *
 * Now that the session is [com.google.firebase.auth.FirebaseAuth]'s persisted
 * user, it survives process death, so this check rarely fires — it used to be
 * the only thing standing between a dead in-memory session and a signed-out
 * user sitting on the Home screen. It is kept because it still catches a
 * restore after the account was signed out or disabled elsewhere, and because
 * the pushed Settings and Notifications screens inherit it.
 *
 * **Second check: an unfinished deletion.** A user whose data was deleted but
 * whose sign-in survived has a deletion marker at `users/{uid}`
 * ([ProfileState.DELETION_PENDING]). Every way into the app lands here - a
 * restored session from Splash, an email login, a Google sign-in - so this is
 * the one place that catches it, and it sends them to finish the deletion
 * ([HomeEvent.ResumeDeletion]) rather than into tabs that would write a
 * contact under a deleted account. The read is asynchronous and the tabs show
 * meanwhile; a read that fails lets them in, exactly as before the marker
 * existed, because a gate that locked people out on a flaky network would be
 * worse than the gap it closes.
 *
 * **Third check: an unverified email.** A password account whose address is
 * not verified yet is sent to
 * [VerifyRequiredActivity][com.example.guardband.ui.verify.VerifyRequiredActivity]
 * ([HomeEvent.RequireEmailVerification]) and never reaches the tabs; Google
 * accounts are never gated ([EmailVerification.requiredBeforeUse]). It is
 * synchronous - the flag rides on the session - so it runs before the
 * deletion read, and an unverified account skips that read entirely: the gate
 * screen's "Delete this account" opens Account information, which resumes a
 * pending deletion on its own. The flag is only as fresh as the session's last
 * reload, so a link opened in a browser can still read as unverified here;
 * the gate screen reloads as soon as it shows and lets them straight through.
 * **This is a product gate, not a security boundary** - see
 * `docs/email-verification.md`.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    init {
        val uid = authRepository.currentUser()?.id
        when {
            uid == null -> _events.trySend(HomeEvent.NavigateToLogin)
            mustVerifyEmail() -> _events.trySend(HomeEvent.RequireEmailVerification)
            else -> checkForUnfinishedDeletion(uid)
        }
    }

    private fun mustVerifyEmail(): Boolean =
        EmailVerification.of(
            providers = authRepository.currentUserProviders(),
            isEmailVerified = authRepository.isEmailVerified()
        ).requiredBeforeUse

    private fun checkForUnfinishedDeletion(uid: String) {
        viewModelScope.launch {
            val state = userProfileRepository.profileState(uid).getOrNull()
            if (state == ProfileState.DELETION_PENDING) {
                _events.send(HomeEvent.ResumeDeletion)
            }
        }
    }

    fun onSettingsClicked() {
        _events.trySend(HomeEvent.ShowSettings)
    }

    fun onNotificationsClicked() {
        _events.trySend(HomeEvent.ShowNotifications)
    }

    fun onProfileClicked() {
        _events.trySend(HomeEvent.ShowProfile)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.userProfileRepository
                )
            }
        }
    }
}
