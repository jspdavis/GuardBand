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
import com.example.guardband.ui.consent.ConsentGate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Host-level logic for [HomeActivity]: the second auth gate, requests to
 * open the pushed screens (Settings, Notifications, Alert history). No
 * UiState beyond [gate] — which
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
 * contact under a deleted account. A pending deletion always wins over the
 * consent check below.
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
 *
 * **Fourth check: a missing consent record (2026-10-14).** An account with no
 * `users/{uid}/consent` - one made in the Console, or a Google account whose
 * record went missing, both of which [ProfileProvisioning][com.example.guardband.data.repository.ProfileProvisioning]
 * gives only a name and an email - is sent to the consent screen
 * ([HomeEvent.RequireConsent]) and never reaches the tabs. Only a **missing**
 * record counts ([ConsentGate.needsConsent]); an account that agreed to an
 * older version is let in. Someone who has just finished sign-up passes
 * without special treatment, because sign-up's own write stored their consent
 * before they got here - no flag is passed in, so none can go stale.
 *
 * **The record is read before the tabs show** ([gate]). Both reads - the
 * deletion marker, then consent - run while [HomeGateState.CHECKING] keeps
 * the tabs covered, so nobody sees the app and is then taken out of it. A read
 * that fails, or takes longer than [GATE_READ_TIMEOUT_MS], is
 * [HomeGateState.FAILED]: a retry, **never** the consent screen and never the
 * tabs. Until 2026-10-14 a failed deletion read let the user in; it does not
 * any more, because "could not tell" must not open the app to an account that
 * may have no consent. The timeout matters: with no disk persistence, an
 * RTDB read made offline does not fail, it waits for a connection forever.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private val _gate = MutableStateFlow(HomeGateState.CHECKING)

    /** Whether the tabs may show yet. See the class KDoc. */
    val gate: StateFlow<HomeGateState> = _gate.asStateFlow()

    init {
        runGate()
    }

    /**
     * Retry on the failed cover. Re-runs every check from the session down,
     * so a session that ended in the meantime goes to Login rather than being
     * read for.
     */
    fun onRetryClicked() {
        if (_gate.value != HomeGateState.FAILED) return
        _gate.value = HomeGateState.CHECKING
        runGate()
    }

    private fun runGate() {
        val uid = authRepository.currentUser()?.id
        when {
            uid == null -> _events.trySend(HomeEvent.NavigateToLogin)
            mustVerifyEmail() -> _events.trySend(HomeEvent.RequireEmailVerification)
            else -> checkRecord(uid)
        }
    }

    private fun mustVerifyEmail(): Boolean =
        EmailVerification.of(
            providers = authRepository.currentUserProviders(),
            isEmailVerified = authRepository.isEmailVerified()
        ).requiredBeforeUse

    private fun checkRecord(uid: String) {
        viewModelScope.launch {
            when (withTimeoutOrNull(GATE_READ_TIMEOUT_MS) { readRecord(uid) }) {
                RecordOutcome.DELETION_PENDING -> _events.send(HomeEvent.ResumeDeletion)
                RecordOutcome.NEEDS_CONSENT -> _events.send(HomeEvent.RequireConsent)
                RecordOutcome.OPEN -> _gate.value = HomeGateState.OPEN
                // Null is the timeout: the same as a failed read.
                RecordOutcome.FAILED, null -> _gate.value = HomeGateState.FAILED
            }
        }
    }

    /**
     * The deletion marker first, because a pending deletion wins - and its
     * record has no consent anyway. Consent is only read for a live (or
     * absent) record. Nothing here writes.
     */
    private suspend fun readRecord(uid: String): RecordOutcome {
        val state = userProfileRepository.profileState(uid).getOrElse {
            return RecordOutcome.FAILED
        }
        if (state == ProfileState.DELETION_PENDING) return RecordOutcome.DELETION_PENDING

        val consent = userProfileRepository.consentRecord(uid).getOrElse {
            return RecordOutcome.FAILED
        }
        return if (ConsentGate.needsConsent(consent)) {
            RecordOutcome.NEEDS_CONSENT
        } else {
            RecordOutcome.OPEN
        }
    }

    private enum class RecordOutcome { DELETION_PENDING, NEEDS_CONSENT, OPEN, FAILED }

    fun onSettingsClicked() {
        _events.trySend(HomeEvent.ShowSettings)
    }

    fun onNotificationsClicked() {
        _events.trySend(HomeEvent.ShowNotifications)
    }

    /** "See all" on Track's recent-alerts panel. */
    fun onSeeAllAlertsClicked() {
        _events.trySend(HomeEvent.ShowAlertHistory)
    }

    companion object {
        /**
         * How long the gate waits for the record before offering Retry. Both
         * reads share it. Long enough for a slow mobile connection; short
         * enough that an offline launch is not a spinner forever.
         */
        const val GATE_READ_TIMEOUT_MS = 10_000L

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
