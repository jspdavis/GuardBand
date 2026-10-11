package com.example.guardband.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.Alert
import com.example.guardband.data.repository.AlertError
import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Track tab: the band's last known position and how it is doing right now.
 *
 * **It reads `devices/{id}/latest`,** which is the one screen that should.
 * `latest` is overwritten by every payload including a TRACKING_UPDATE, which
 * is exactly what this tab wants - the newest ping, whatever kind it was - and
 * exactly why the Alert tab derives its card from history instead (D2).
 *
 * Two things here are less obvious than they look:
 *
 *  - **The last known position is retained** across reports that carry none
 *    (D3). A tracking ping with no GPS fix means "the band is talking but
 *    cannot see the sky", and blanking the map in that moment would throw away
 *    the most useful thing on screen.
 *  - **Staleness is recomputed on a timer,** not only when data arrives. A band
 *    that goes quiet produces no emission to react to, so without
 *    [staleTicks] the badge could never flip from online to offline - it would
 *    sit on whatever it said when the last report landed.
 *
 * Nothing here logs. The state holds the wearer's coordinates.
 *
 * @param staleTicks fires the staleness recompute. Injected so a test can
 *   drive it by hand, or pass an empty flow and be sure nothing is ticking in
 *   the background.
 */
class TrackViewModel(
    private val authRepository: AuthRepository,
    private val alertRepository: AlertRepository,
    private val clock: Clock = Clock.SYSTEM,
    private val staleTicks: Flow<Unit> = defaultStaleTicks()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackUiState(userName = sessionName()))
    val uiState: StateFlow<TrackUiState> = _uiState.asStateFlow()

    private val _events = Channel<TrackEvent>(Channel.BUFFERED)
    val events: Flow<TrackEvent> = _events.receiveAsFlow()

    /**
     * When the band last reported, parsed from the newest payload's timestamp.
     * Null while it has never reported, or when its timestamp could not be
     * read. Kept outside [uiState] because it is an input to what the state
     * shows, not something the screen renders.
     */
    private var lastReportMillis: Long? = null

    /** The live subscription, cancelled and replaced on retry. */
    private var observeJob: Job? = null

    init {
        observeLatest()
        viewModelScope.launch {
            staleTicks.collect { recomputeStaleness() }
        }
    }

    /**
     * Re-subscribes after a failure.
     *
     * Cancelling [observeJob] cancels the repository's `callbackFlow`, whose
     * `awaitClose` removes the database listener, so a retry replaces the
     * listener rather than stacking a second one on top of it.
     */
    fun onRetryClicked() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeLatest()
    }

    /** D6. Does nothing without a fix; the button is disabled in that state. */
    fun onNavigateClicked() {
        val location = _uiState.value.location ?: return
        _events.trySend(
            TrackEvent.OpenNavigation(NavigationUrl.googleMapsSearch(location.lat, location.lng))
        )
    }

    /** D6's fallback: the device has nothing that can open a maps link. */
    fun onNavigationUnavailable() {
        _events.trySend(TrackEvent.ShowMessage(MSG_NAVIGATION_UNAVAILABLE))
    }

    /** D7: a UI-only endpoint. It must not write anything to the band. */
    fun onCheckInClicked() {
        _events.trySend(TrackEvent.ShowMessage(MSG_CHECK_IN_ON_BAND))
    }

    /** D7: a UI-only endpoint. */
    fun onLayersClicked() {
        _events.trySend(TrackEvent.ShowMessage(MSG_MAP_LAYERS_UNAVAILABLE))
    }

    private fun observeLatest() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            alertRepository.observeLatestAlert().collect { result ->
                result
                    .onSuccess { alert -> render(alert) }
                    .onFailure { error -> reportFailure(error) }
            }
        }
    }

    /**
     * A null alert is the repository's explicit "the band has never reported",
     * not a failure. That clears everything rather than retaining a position:
     * the record is gone, so there is nothing to be the last known anything.
     */
    private fun render(alert: Alert?) {
        if (alert == null) {
            lastReportMillis = null
            _uiState.update {
                it.copy(
                    isLoading = false,
                    hasReported = false,
                    location = null,
                    battery = null,
                    isOnline = false,
                    elapsed = null,
                    errorMessage = null
                )
            }
            return
        }

        lastReportMillis = BandStatus.parseIsoUtcMillis(alert.timestamp)
        _uiState.update {
            it.copy(
                isLoading = false,
                hasReported = true,
                // D3, and the same rule for battery: a report that omits one of
                // these is a report, not an erasure.
                location = alert.location ?: it.location,
                battery = alert.battery ?: it.battery,
                errorMessage = null
            )
        }
        recomputeStaleness()
    }

    /**
     * Derives [TrackUiState.isOnline] and [TrackUiState.elapsed] from the clock.
     *
     * A null [lastReportMillis] - never reported, or a timestamp that could not
     * be parsed - clears both rather than leaving the previous report's
     * freshness on screen. "We cannot say how fresh this is" has to be
     * distinguishable from "it is fresh".
     */
    private fun recomputeStaleness() {
        val reportedAt = lastReportMillis
        val now = clock.nowMillis()

        _uiState.update {
            it.copy(
                isOnline = reportedAt != null && BandStatus.isOnline(reportedAt, now),
                elapsed = reportedAt?.let { at -> BandStatus.elapsedSince(at, now) }
            )
        }
    }

    /**
     * A failure with a position on screen is a notice; a failure with nothing
     * on screen is the error state, because there the user has no other signal
     * that anything went wrong. Same split as the Alert tab.
     */
    private suspend fun reportFailure(error: Throwable) {
        val message = messageFor(error)
        val hasContent = _uiState.value.location != null

        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = if (hasContent) null else message
            )
        }

        if (hasContent) _events.send(TrackEvent.ShowMessage(message))
    }

    /**
     * Wording for each [AlertError], reusing the Alert tab's typed errors and
     * mapping them here rather than in the repository.
     *
     * Kept in Kotlin rather than `strings.xml` for the reason every other
     * ViewModel does it: a ViewModel holds no Context. Nothing here repeats a
     * Firebase message - [AlertError] carries none.
     */
    private fun messageFor(error: Throwable): String = when (error) {
        AlertError.Network -> MSG_NETWORK
        AlertError.PermissionDenied -> MSG_PERMISSION_DENIED
        AlertError.NotSignedIn -> MSG_NOT_SIGNED_IN
        AlertError.ParseFailure -> MSG_UNREADABLE
        else -> MSG_LOAD_FAILED
    }

    /** The chip's name, straight from the session. */
    private fun sessionName(): String =
        authRepository.currentUser()?.name?.takeUnless { it.isBlank() } ?: DEFAULT_USER_NAME

    companion object {
        const val DEFAULT_USER_NAME = "User"

        /**
         * How often staleness is recomputed. Well under
         * [BandStatus.ONLINE_THRESHOLD_MS] so the badge turns over promptly,
         * and far too cheap to matter: it reads the clock and copies a state
         * object, with no database traffic.
         */
        const val TICK_INTERVAL_MS = 30_000L

        const val MSG_CHECK_IN_ON_BAND =
            "Check-ins come from the band: double-press its button."
        const val MSG_MAP_LAYERS_UNAVAILABLE = "Map layers aren't available yet."
        const val MSG_NAVIGATION_UNAVAILABLE = "No app on this phone can open maps."

        const val MSG_LOAD_FAILED = "Couldn't load your band's location."
        const val MSG_NETWORK = "Couldn't reach your band. Check your connection."
        const val MSG_PERMISSION_DENIED = "You don't have access to this band's location."
        const val MSG_NOT_SIGNED_IN = "Please sign in again to see your band's location."
        const val MSG_UNREADABLE = "Your band sent a position we couldn't read."

        /** The real ticker. A plain loop, so nothing schedules work when unused. */
        private fun defaultStaleTicks(): Flow<Unit> = flow {
            while (true) {
                delay(TICK_INTERVAL_MS)
                emit(Unit)
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TrackViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.alertRepository
                )
            }
        }
    }
}
