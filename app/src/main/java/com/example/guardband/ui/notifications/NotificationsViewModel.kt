package com.example.guardband.ui.notifications

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
import com.example.guardband.ui.alert.AlertPlaces
import com.example.guardband.ui.alert.AlertUiState
import com.example.guardband.ui.track.Clock
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
 * Notifications inbox: the band's non-tracking alert history, newest first,
 * grouped under day headings.
 *
 * Fragment-scoped (not activity-scoped): the inbox is a pushed screen with
 * its own back stack, and its subscription ends when the user navigates back.
 *
 * Tracking updates (TRACKING_UPDATE) are filtered out — they carry no
 * incident information and would flood the inbox.
 *
 * **Place names** are looked up via [GeocoderPlaceRepository] exactly as
 * [AlertViewModel][com.example.guardband.ui.alert.AlertViewModel] does.
 * Each unique rounded coordinate is queried once per ViewModel lifetime
 * (memory only — nothing is written to the phone). Rows show coordinates
 * until the name arrives, or permanently if the geocoder has no answer.
 * Unavailable answers are retried after [PLACE_RETRY_MS].
 *
 * [ticks] drives "5 min ago" ageing; injected so tests never wait on a real
 * delay.
 */
class NotificationsViewModel(
    private val alertRepository: AlertRepository,
    private val placeRepository: PlaceRepository = PlaceRepository.NONE,
    private val clock: Clock = Clock.SYSTEM,
    private val ticks: Flow<Unit> = defaultTicks()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertUiState(isLoading = true))
    val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()

    private val _events = Channel<NotificationsEvent>(Channel.BUFFERED)
    val events: Flow<NotificationsEvent> = _events.receiveAsFlow()

    private var observeJob: Job? = null

    /** Place keys already requested or being requested; never asked twice. */
    private val placesRequested = mutableSetOf<String>()

    /** When a point the geocoder could not answer may be retried. */
    private val placeRetryAt = mutableMapOf<String, Long>()

    init {
        observe()
        viewModelScope.launch {
            ticks.collect {
                _uiState.update { it.copy(nowMillis = clock.nowMillis()) }
            }
        }
    }

    fun onRetryClicked() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            alertRepository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW)
                .collect { result ->
                    result
                        .onSuccess { render(it) }
                        .onFailure { reportFailure(it) }
                }
        }
    }

    private fun render(history: AlertHistory) {
        // Drop tracking updates; the inbox is for incidents, not location pings.
        val displayable = history.alerts
            .filter { it.alertType() != AlertType.TRACKING_UPDATE }
            .take(MAX_INBOX)

        _uiState.update {
            it.copy(
                isLoading = false,
                latest = displayable.firstOrNull(),
                history = displayable,
                errorMessage = null,
                emptyMessage = emptyMessageFor(history, displayable),
                nowMillis = clock.nowMillis()
            )
        }

        lookUpPlaces(displayable)
    }

    /**
     * Requests reverse-geocoding for every unique rounded coordinate in
     * [alerts] that has not been asked about yet (or can be retried).
     * Newest first, one at a time — same order and concurrency as
     * [AlertViewModel][com.example.guardband.ui.alert.AlertViewModel].
     *
     * Each found name is merged into [AlertUiState.placeNames] immediately
     * so the row updates as soon as the answer arrives, without waiting for
     * the rest of the list.
     */
    private fun lookUpPlaces(alerts: List<Alert>) {
        val now = clock.nowMillis()
        val wanted = alerts
            .mapNotNull { it.location }
            .map { AlertPlaces.keyOf(it) to it }
            .distinctBy { it.first }
            .filter { (key, _) ->
                key !in placesRequested && (placeRetryAt[key] ?: 0L) <= now
            }
        if (wanted.isEmpty()) return
        wanted.forEach { (key, _) -> placesRequested += key }

        viewModelScope.launch {
            for ((key, location) in wanted) {
                val lookup = placeRepository.placeAt(
                    AlertPlaces.rounded(location.lat),
                    AlertPlaces.rounded(location.lng)
                )
                when (lookup) {
                    is PlaceLookup.Found ->
                        AlertPlaces.label(lookup.place)?.let { name ->
                            _uiState.update {
                                it.copy(placeNames = it.placeNames + (key to name))
                            }
                        }
                    PlaceLookup.NotFound -> Unit   // no name exists; keep coordinates
                    PlaceLookup.Unavailable -> {
                        // Allow a retry after the back-off period.
                        placesRequested -= key
                        placeRetryAt[key] = clock.nowMillis() + PLACE_RETRY_MS
                    }
                }
            }
        }
    }

    private fun emptyMessageFor(history: AlertHistory, displayable: List<*>): String? = when {
        displayable.isNotEmpty() -> null
        history.rawCount == 0 -> MSG_EMPTY_NO_ALERTS
        history.malformedCount == history.rawCount -> MSG_EMPTY_UNREADABLE
        else -> MSG_EMPTY_ONLY_TRACKING
    }

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

        if (hasContent) _events.send(NotificationsEvent.ShowMessage(message))
    }

    private fun messageFor(error: Throwable): String = when (error) {
        AlertError.Network -> MSG_NETWORK
        AlertError.PermissionDenied -> MSG_PERMISSION_DENIED
        AlertError.NotSignedIn -> MSG_NOT_SIGNED_IN
        AlertError.ParseFailure -> MSG_UNREADABLE
        else -> MSG_LOAD_FAILED
    }

    companion object {
        /** Display cap after tracking updates are dropped. */
        const val MAX_INBOX = 50

        const val TICK_INTERVAL_MS = 30_000L

        /**
         * How long to wait before retrying a coordinate the geocoder could
         * not answer. Matches [AlertViewModel]'s value.
         */
        const val PLACE_RETRY_MS = 60_000L

        const val MSG_LOAD_FAILED = "Couldn't load your notifications."
        const val MSG_NETWORK = "Couldn't reach your band. Check your connection."
        const val MSG_PERMISSION_DENIED = "You don't have access to this band's alerts."
        const val MSG_NOT_SIGNED_IN = "Please sign in again to see your notifications."
        const val MSG_UNREADABLE = "Your band sent something we couldn't read."
        const val MSG_EMPTY_NO_ALERTS = "No alerts from your band yet."
        const val MSG_EMPTY_ONLY_TRACKING =
            "No incidents yet — your band has only been sending location updates."
        const val MSG_EMPTY_UNREADABLE = "Your band's recent alerts couldn't be read."

        private fun defaultTicks(): Flow<Unit> = flow {
            while (true) {
                delay(TICK_INTERVAL_MS)
                emit(Unit)
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotificationsViewModel(
                    alertRepository = RepositoryProvider.alertRepository,
                    placeRepository = GeocoderPlaceRepository(
                        checkNotNull(this[APPLICATION_KEY])
                    )
                )
            }
        }
    }
}

/** One-shot events from [NotificationsViewModel]. */
sealed interface NotificationsEvent {
    data class ShowMessage(val text: String) : NotificationsEvent
}
