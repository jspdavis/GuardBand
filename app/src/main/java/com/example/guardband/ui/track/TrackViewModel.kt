package com.example.guardband.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.data.model.CheckIn
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
 * exactly why the Alert history screen derives its card from history
 * instead (D2).
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
 * The Check-in pill reads the wearer's latest check-in (FR-10) through its own
 * subscription, and its "X ago" is recomputed on the same ticker. It is a
 * status, not an action: nothing here can send a check-in, because only the
 * band does.
 *
 * The marker's face, [TrackUiState.markerFace], is worked out once, from the
 * session, in [markerFace]: the one place that decides whose picture marks
 * the band.
 *
 * **Recent alerts are pins** ([TrackUiState.alertPins], 2026-10-27), chosen by
 * [AlertPinSelector] from the list the Fragment hands over in
 * [onAlertsChanged]: the recent-alerts sheet's own, so no listener is added
 * here. A tapped pin opens a label ([TrackUiState.pinLabel]) that ages on the
 * staleness ticker, or, for a panic, its Incident view. The band's bubble
 * offers "View incident" while [TrackUiState.bandPanic] is set
 * ([onViewIncidentClicked]). Both send [TrackEvent.OpenIncident], which the
 * Fragment hands to the host's one incident opener.
 *
 * Nothing here logs. The state holds the wearer's coordinates, and the face a
 * photo link.
 *
 * @param layerPrefs where the picked base map is kept, so the map reopens on
 *   it. Read once, for the first state.
 * @param staleTicks fires the staleness recompute. Injected so a test can
 *   drive it by hand, or pass an empty flow and be sure nothing is ticking in
 *   the background.
 */
class TrackViewModel(
    private val alertRepository: AlertRepository,
    private val authRepository: AuthRepository,
    private val layerPrefs: MapLayerPrefs,
    private val clock: Clock = Clock.SYSTEM,
    private val staleTicks: Flow<Unit> = defaultStaleTicks()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TrackUiState(markerFace = markerFace(), selectedLayer = layerPrefs.layer)
    )
    val uiState: StateFlow<TrackUiState> = _uiState.asStateFlow()

    private val _events = Channel<TrackEvent>(Channel.BUFFERED)
    val events: Flow<TrackEvent> = _events.receiveAsFlow()

    /**
     * What the band's reports add up to: retained position and battery, and
     * when it last reported. [BandReport] holds the rules, shared with the
     * Device tab. Kept outside [uiState] because it is an input to what the
     * state shows, not something the screen renders.
     */
    private var band: BandReport = BandReport.NONE

    /** The live subscription, cancelled and replaced on retry. */
    private var observeJob: Job? = null

    /**
     * When the latest check-in happened, or null when there is none or its
     * timestamp could not be read. An input to [TrackUiState.checkIn], kept
     * out of the state for the same reason as [band].
     */
    private var lastCheckInMillis: Long? = null

    /** The check-in subscription, cancelled and replaced on retry. */
    private var checkInJob: Job? = null

    /**
     * The alerts the pins are chosen from, as last handed over by
     * [onAlertsChanged]. Kept so a new band position can re-choose them: a
     * pin under the face is dropped.
     */
    private var pinSource: List<Alert> = emptyList()

    init {
        observeLatest()
        observeCheckIns()
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
        observeCheckIns()
    }

    /**
     * D7: the user picked a base map in the layer picker. View-only state goes
     * through the Fragment (the picker is a dialog, like recentering is a
     * camera move), so this only records the choice; the Fragment re-applies the
     * tile source when [TrackUiState.selectedLayer] changes. The choice is
     * also saved, so the map reopens on it after the app is closed.
     */
    fun onLayerSelected(layer: MapLayer) {
        if (_uiState.value.selectedLayer == layer) return
        layerPrefs.layer = layer
        _uiState.update { it.copy(selectedLayer = layer) }
    }

    /**
     * The alerts behind the recent-alerts sheet, handed over by the Fragment
     * from the host-scoped
     * [AlertViewModel][com.example.guardband.ui.alert.AlertViewModel], which
     * already reads the history window for the sheet and Alert history. Taking
     * them from there rather than from the repository means the pins add no
     * second history listener, and can never disagree with the sheet.
     *
     * The pins are re-chosen ([AlertPinSelector]); an open label whose pin is
     * no longer among them closes.
     */
    fun onAlertsChanged(alerts: List<Alert>) {
        pinSource = alerts
        recomputePins()
    }

    /**
     * A pin was tapped. A panic's opens its Incident view
     * ([TrackEvent.OpenIncident]); any other type's opens its label. A pin no
     * longer on the map (a tap racing an update) is ignored.
     */
    fun onAlertPinTapped(alert: Alert) {
        val pin = _uiState.value.alertPins.firstOrNull { it.alert.sequenceId == alert.sequenceId }
            ?: return
        if (pin.type == AlertType.PANIC) {
            _events.trySend(TrackEvent.OpenIncident(pin.alert))
            return
        }
        _uiState.update { it.copy(pinLabel = AlertPinLabel(pin, elapsedOf(pin.alert))) }
    }

    /**
     * "View incident" on the band's bubble: opens [TrackUiState.bandPanic]'s
     * Incident view. Does nothing when the bubble offers none (the action is
     * hidden then; this guards a tap racing a check-in).
     */
    fun onViewIncidentClicked() {
        val panic = _uiState.value.bandPanic ?: return
        _events.trySend(TrackEvent.OpenIncident(panic))
    }

    /**
     * The map was tapped anywhere that is not a pin - the map itself, the
     * band's marker, or the open label - so the label closes. A pin's tap
     * arrives here first and then reopens its own label.
     */
    fun onMapTapped() {
        if (_uiState.value.pinLabel == null) return
        _uiState.update { it.copy(pinLabel = null) }
    }

    /**
     * **Whose face marks the band. The one place that decides it.**
     *
     * Today it is the signed-in user's own, because there is no wearer or
     * guardian yet: whoever is looking is the only person the app knows. Once
     * roles or pairing exist, the wearer's photo and name belong here instead,
     * and nothing else has to change. The session is read synchronously, so
     * the first state already carries it.
     */
    private fun markerFace(): BandMarkerFace = BandMarkerFace.of(
        person = authRepository.currentUser(),
        providers = authRepository.currentUserProviders()
    )

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

    private fun observeCheckIns() {
        checkInJob?.cancel()
        checkInJob = viewModelScope.launch {
            alertRepository.observeLatestCheckIn().collect { result ->
                result
                    .onSuccess { checkIn -> renderCheckIn(checkIn) }
                    .onFailure { reportCheckInFailure() }
            }
        }
    }

    private fun renderCheckIn(checkIn: CheckIn?) {
        lastCheckInMillis = checkIn?.let { BandStatus.parseIsoUtcMillis(it.timestamp) }
        _uiState.update {
            it.copy(
                checkIn = if (checkIn == null) CheckInStatus.None
                else CheckInStatus.Received(checkInElapsed())
            )
        }
    }

    /**
     * A failed read keeps whatever the pill already says: a check-in shown a
     * minute ago is still true, and aging it on the ticker stays honest. Only
     * a pill with nothing to show yet becomes [CheckInStatus.Unavailable].
     * No event either - the map's own failure already tells the user the
     * connection is in trouble, and two notices for one outage is noise.
     */
    private fun reportCheckInFailure() {
        _uiState.update {
            if (it.checkIn == CheckInStatus.Loading) it.copy(checkIn = CheckInStatus.Unavailable)
            else it
        }
    }

    private fun checkInElapsed(): Elapsed? =
        lastCheckInMillis?.let { BandStatus.elapsedSince(it, clock.nowMillis()) }

    /**
     * A null alert is the repository's explicit "the band has never reported",
     * not a failure. That clears everything rather than retaining a position:
     * the record is gone, so there is nothing to be the last known anything.
     */
    private fun render(alert: Alert?) {
        band = band.after(alert)
        if (alert == null) {
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
            recomputePins()
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                hasReported = true,
                // D3, and the same rule for battery: a report that omits one of
                // these is a report, not an erasure. BandReport applies it.
                location = band.location,
                battery = band.battery,
                errorMessage = null
            )
        }
        // The face may have moved onto, or off, a pin.
        recomputePins()
        recomputeStaleness()
    }

    /**
     * Re-chooses the pins from [pinSource] around the band's current marker.
     * An open label stays open, aged afresh, while its pin is still chosen,
     * and closes when it is not. An unchanged choice copies an equal state,
     * which the StateFlow drops, so a walking band moving the face costs the
     * screen nothing.
     */
    private fun recomputePins() {
        _uiState.update { state ->
            val pins = AlertPinSelector.select(pinSource, state.location)
            val label = state.pinLabel?.let { open ->
                pins.firstOrNull { it.alert.sequenceId == open.pin.alert.sequenceId }
                    ?.let { pin -> AlertPinLabel(pin, elapsedOf(pin.alert)) }
            }
            state.copy(alertPins = pins, pinLabel = label, bandPanic = latestPanic())
        }
    }

    /** [pinSource]'s newest non-tracking alert, if it is a panic (see [TrackUiState.bandPanic]). */
    private fun latestPanic(): Alert? =
        pinSource
            .filter { it.alertType() != AlertType.TRACKING_UPDATE }
            .maxByOrNull { it.sequenceId }
            ?.takeIf { it.alertType() == AlertType.PANIC }

    /** How long ago [alert] was, by the injected clock; null when its timestamp is unreadable. */
    private fun elapsedOf(alert: Alert): Elapsed? =
        BandStatus.parseIsoUtcMillis(alert.timestamp)
            ?.let { BandStatus.elapsedSince(it, clock.nowMillis()) }

    /**
     * Derives [TrackUiState.isOnline], [TrackUiState.elapsed] and the age of a
     * received [TrackUiState.checkIn] from the clock.
     *
     * A band with no readable report time - never reported, or a timestamp
     * that could not be parsed - clears both rather than leaving the previous
     * report's freshness on screen ([BandReport.isReporting]). "We cannot say
     * how fresh this is" has to be distinguishable from "it is fresh".
     */
    private fun recomputeStaleness() {
        val now = clock.nowMillis()

        _uiState.update {
            it.copy(
                isOnline = band.isReporting(now),
                elapsed = band.elapsed(now),
                // The pill ages on the same tick. Only a received check-in has
                // an age; the other states have nothing to recompute.
                checkIn = if (it.checkIn is CheckInStatus.Received) {
                    CheckInStatus.Received(checkInElapsed())
                } else {
                    it.checkIn
                },
                // An open pin label ages on the same tick.
                pinLabel = it.pinLabel?.let { open -> open.copy(elapsed = elapsedOf(open.pin.alert)) }
            )
        }
    }

    /**
     * A failure with a position on screen is a notice; a failure with nothing
     * on screen is the error state, because there the user has no other signal
     * that anything went wrong. Same split as the alert screens.
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
     * Wording for each [AlertError], reusing the alert screens' typed errors and
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

    companion object {
        /**
         * How often staleness is recomputed. Well under
         * [BandStatus.ONLINE_THRESHOLD_MS] so the badge turns over promptly,
         * and far too cheap to matter: it reads the clock and copies a state
         * object, with no database traffic.
         */
        const val TICK_INTERVAL_MS = 30_000L


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
                    RepositoryProvider.alertRepository,
                    RepositoryProvider.authRepository,
                    MapLayerPrefs.from(checkNotNull(this[APPLICATION_KEY]))
                )
            }
        }
    }
}
