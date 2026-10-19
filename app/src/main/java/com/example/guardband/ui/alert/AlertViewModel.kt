package com.example.guardband.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.data.repository.AlertError
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.data.repository.GeocoderPlaceRepository
import com.example.guardband.data.repository.PlaceLookup
import com.example.guardband.data.repository.PlaceRepository
import com.example.guardband.ui.track.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The band's ongoing status and its incident history, observed live for as
 * long as the ViewModel lives.
 *
 * **One instance serves two screens.** It is scoped to the Home host, and
 * both the Track tab's recent-alerts panel and the Alert history screen get
 * it with `activityViewModels { AlertViewModel.Factory }`. So there is one
 * subscription to the history window, not one per screen: the panel reads
 * [AlertUiState.recent] and the history screen [AlertUiState.history], both
 * cut from the same emission. The subscription ends when the host's
 * ViewModelStore is cleared, which cancels the repository's `callbackFlow`
 * and its `awaitClose` removes the listener.
 *
 * **It reads history only.** The latest card is the newest non-tracking entry
 * in that same window (D2), not `devices/{id}/latest`, because `latest` is
 * overwritten by every payload including a TRACKING_UPDATE - so the card would
 * otherwise read "Location update" while a panic sat one row below it.
 * [AlertRepository.observeLatestAlert] still exists for the Track tab; not
 * subscribing to it here also means one fewer live listener.
 *
 * D1 hides tracking updates, D3 reads a window of
 * [AlertRepository.DEFAULT_HISTORY_WINDOW] raw entries and shows at most
 * [MAX_HISTORY] of them.
 *
 * A failed read keeps the last good data on screen and reports it as an event;
 * only a failure with nothing to fall back to becomes the error state.
 *
 * **Place names fill in after the list** ([AlertUiState.placeNames]). Each
 * shown alert's location is looked up once per rounded point
 * ([AlertPlaces.keyOf], about 11 m), newest first, one at a time, and kept
 * for the life of this ViewModel (memory only: nothing about a location is
 * written to the phone). A point with no name is not asked again; one the
 * service could not answer (offline) is asked again on a later emission, but
 * not within [PLACE_RETRY_MS]. Rows show coordinates until then.
 */
class AlertViewModel(
    private val alertRepository: AlertRepository,
    private val placeRepository: PlaceRepository = PlaceRepository.NONE,
    private val clock: Clock = Clock.SYSTEM
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertUiState())
    val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()

    private val _events = Channel<AlertEvent>(Channel.BUFFERED)
    val events: Flow<AlertEvent> = _events.receiveAsFlow()

    /** The live subscription, cancelled and replaced on retry. */
    private var observeJob: Job? = null

    /** Place keys asked about, or being asked about; never asked twice. */
    private val placesRequested = mutableSetOf<String>()

    /** When a point the service could not answer may be asked again. */
    private val placeRetryAt = mutableMapOf<String, Long>()

    init {
        observeHistory()
    }

    /**
     * Re-subscribes after a failure.
     *
     * Cancelling [observeJob] cancels the repository's `callbackFlow`, whose
     * `awaitClose` removes the Realtime Database listener, so a retry replaces
     * the listener instead of stacking a second one on top of it.
     */
    fun onRetryClicked() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeHistory()
    }

    private fun observeHistory() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            alertRepository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW)
                .collect { result ->
                    result
                        .onSuccess { history -> render(history) }
                        .onFailure { error -> reportFailure(error) }
                }
        }
    }

    private fun render(history: AlertHistory) {
        // D1 then D3: drop tracking updates, then cap what is left.
        val displayable = history.alerts
            .filter { it.alertType() != AlertType.TRACKING_UPDATE }
            .take(MAX_HISTORY)

        _uiState.update {
            it.copy(
                isLoading = false,
                // D2: the newest surviving entry is the current status.
                latest = displayable.firstOrNull(),
                history = displayable,
                recent = displayable.take(RECENT_ALERTS_LIMIT),
                errorMessage = null,
                emptyMessage = emptyMessageFor(history, displayable)
            )
        }
        lookUpPlaces(displayable)
    }

    /** Asks for the names of [alerts]' points not yet known or asked about, newest first. */
    private fun lookUpPlaces(alerts: List<Alert>) {
        val now = clock.nowMillis()
        val wanted = alerts
            .mapNotNull { it.location }
            .map { AlertPlaces.keyOf(it) to it }
            .distinctBy { it.first }
            .filter { (key, _) -> key !in placesRequested && (placeRetryAt[key] ?: 0L) <= now }
        if (wanted.isEmpty()) return
        wanted.forEach { (key, _) -> placesRequested += key }

        viewModelScope.launch {
            for ((key, location) in wanted) {
                val lookup = placeRepository.placeAt(
                    AlertPlaces.rounded(location.lat),
                    AlertPlaces.rounded(location.lng)
                )
                when (lookup) {
                    is PlaceLookup.Found -> AlertPlaces.label(lookup.place)?.let { name ->
                        _uiState.update { it.copy(placeNames = it.placeNames + (key to name)) }
                    }
                    // No name will come: the row keeps its coordinates for good.
                    PlaceLookup.NotFound -> Unit
                    PlaceLookup.Unavailable -> {
                        placesRequested -= key
                        placeRetryAt[key] = clock.nowMillis() + PLACE_RETRY_MS
                    }
                }
            }
        }
    }

    /**
     * Why there is nothing to show.
     *
     * The three cases read identically as an empty list but mean different
     * things to someone wondering whether their band is working, which is why
     * [AlertHistory] carries its counts.
     */
    private fun emptyMessageFor(history: AlertHistory, displayable: List<Alert>): String? = when {
        displayable.isNotEmpty() -> null
        history.rawCount == 0 -> MSG_EMPTY_NO_ALERTS
        history.malformedCount == history.rawCount -> MSG_EMPTY_UNREADABLE
        else -> MSG_EMPTY_ONLY_TRACKING
    }

    /**
     * A failure with content on screen is a notice; a failure with nothing on
     * screen is the error state, because there the user has no other signal
     * that anything went wrong.
     */
    private suspend fun reportFailure(error: Throwable) {
        val message = messageFor(error)
        val hasContent = _uiState.value.history.isNotEmpty()

        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = if (hasContent) null else message,
                emptyMessage = null
            )
        }

        if (hasContent) _events.send(AlertEvent.ShowMessage(message))
    }

    /**
     * Wording for each [AlertError].
     *
     * Kept in Kotlin rather than `strings.xml` for the reason the other
     * ViewModels do it: a ViewModel holds no Context. Nothing here repeats a
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
        /** D3's display cap, applied after tracking updates are dropped. */
        const val MAX_HISTORY = 50

        /**
         * How many of the newest displayable alerts the Track tab's panel
         * shows. Small on purpose: the panel sits over the map, and the full
         * list is one tap away.
         */
        const val RECENT_ALERTS_LIMIT = 3

        /**
         * How long a point the geocoder could not answer waits before it is
         * asked again. A walking band emits every few seconds; offline, that
         * would otherwise be a lookup per emission.
         */
        const val PLACE_RETRY_MS = 60_000L

        const val MSG_LOAD_FAILED = "Couldn't load alerts from your band."
        const val MSG_NETWORK = "Couldn't reach your band. Check your connection."
        const val MSG_PERMISSION_DENIED = "You don't have access to this band's alerts."
        const val MSG_NOT_SIGNED_IN = "Please sign in again to see your band's alerts."
        const val MSG_UNREADABLE = "Your band sent something we couldn't read."

        const val MSG_EMPTY_NO_ALERTS = "No alerts from your band yet."
        const val MSG_EMPTY_ONLY_TRACKING =
            "No incidents recently — your band has only been sending location updates."
        const val MSG_EMPTY_UNREADABLE = "Your band's recent alerts couldn't be read."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AlertViewModel(
                    RepositoryProvider.alertRepository,
                    GeocoderPlaceRepository(checkNotNull(this[APPLICATION_KEY]))
                )
            }
        }
    }
}
