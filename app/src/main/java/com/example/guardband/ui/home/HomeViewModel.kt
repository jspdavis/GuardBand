package com.example.guardband.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.model.Role
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.ui.account.EmailVerification
import com.example.guardband.ui.consent.ConsentGate
import com.example.guardband.ui.incident.IncidentEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Host-level logic for [HomeActivity]: the second auth gate, requests to
 * open the pushed screens (Settings, Notifications, Alert history).
 *
 * Scoped to the Activity, so tab Fragments share this instance through
 * `activityViewModels { HomeViewModel.Factory }` and forward top-bar clicks here.
 *
 * Auth gate: a fresh ViewModel re-checks the session, because Android can
 * restore this Activity directly without going through Splash.
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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** A notification's target, held until the gate opens. */
    private var pendingTarget: HomeLaunchTarget? = null

    private var resolvedRole: Role? = null

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
        _uiState.update { it.copy(gate = HomeGateState.CHECKING) }
        runGate()
    }

    private fun runGate() {
        val uid = authRepository.currentUser()?.id
        when {
            uid == null -> leave(HomeEvent.NavigateToLogin)
            mustVerifyEmail() -> leave(HomeEvent.RequireEmailVerification)
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
                RecordOutcome.DELETION_PENDING -> leave(HomeEvent.ResumeDeletion)
                RecordOutcome.NEEDS_CONSENT -> leave(HomeEvent.RequireConsent)
                RecordOutcome.NEEDS_ROLE -> leave(HomeEvent.RequireCompleteProfile)
                RecordOutcome.OPEN -> {
                    _gate.value = HomeGateState.OPEN
                    _uiState.update { it.copy(gate = HomeGateState.OPEN, role = resolvedRole) }
                    pendingTarget?.let(::openTarget)
                    pendingTarget = null
                }
                // Null is the timeout: the same as a failed read.
                RecordOutcome.FAILED, null -> {
                    _gate.value = HomeGateState.FAILED
                    _uiState.update { it.copy(gate = HomeGateState.FAILED) }
                }
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
        if (ConsentGate.needsConsent(consent)) {
            return RecordOutcome.NEEDS_CONSENT
        }

        val profile = userProfileRepository.fetchProfile(uid).getOrElse {
            return RecordOutcome.FAILED
        }
        val role = profile?.role
        if (role == null) {
            return RecordOutcome.NEEDS_ROLE
        }
        resolvedRole = role
        return RecordOutcome.OPEN
    }

    private enum class RecordOutcome { DELETION_PENDING, NEEDS_CONSENT, NEEDS_ROLE, OPEN, FAILED }

    /** Leaving the tabs: a held notification target goes with them. */
    private fun leave(event: HomeEvent) {
        pendingTarget = null
        _events.trySend(event)
    }

    /**
     * A notification was tapped. Opens [target] now if the gate is open,
     * otherwise once it opens; dropped if the gate sends the user elsewhere.
     */
    fun onLaunchTargetRequested(target: HomeLaunchTarget) {
        if (_gate.value == HomeGateState.OPEN) openTarget(target) else pendingTarget = target
    }

    private fun openTarget(target: HomeLaunchTarget) {
        _events.trySend(
            when (target) {
                HomeLaunchTarget.ALERT_HISTORY -> HomeEvent.ShowAlertHistory
                HomeLaunchTarget.ALERT_NOTIFICATIONS -> HomeEvent.ShowAlertNotifications
            }
        )
    }

    fun onSettingsClicked() {
        _events.trySend(HomeEvent.ShowSettings)
    }

    /** The Device tab's emergency-contacts row: switch to the Contacts tab. */
    fun onContactsSummaryClicked() {
        _events.trySend(HomeEvent.ShowContacts)
    }

    fun onNotificationsClicked() {
        _events.trySend(HomeEvent.ShowNotifications)
    }

    /** "See all" on Track's recent-alerts panel. */
    fun onSeeAllAlertsClicked() {
        _events.trySend(HomeEvent.ShowAlertHistory)
    }

    /**
     * **The one way to open an Incident view.** Every entry point ends here: a
     * panic row in Track's sheet or in Alert history, a panic pin on the
     * Track map, and "View incident" on the band's bubble. Anything that is
     * not a panic ([IncidentEntry.opensIncident]) is ignored, so no caller
     * can open an incident for a check-in by mistake.
     */
    fun onOpenIncident(alert: Alert) {
        if (!IncidentEntry.opensIncident(alert)) return
        _events.trySend(HomeEvent.ShowIncident(alert.sequenceId))
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
