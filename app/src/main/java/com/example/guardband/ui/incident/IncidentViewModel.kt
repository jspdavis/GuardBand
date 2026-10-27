package com.example.guardband.ui.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.Alert
import com.example.guardband.data.repository.AlertError
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.data.repository.GeocoderPlaceRepository
import com.example.guardband.data.repository.PlaceLookup
import com.example.guardband.data.repository.PlaceRepository
import com.example.guardband.ui.alert.AlertPlaces
import com.example.guardband.ui.track.Clock
import com.example.guardband.ui.track.MapLayerPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One panic's Incident view: the panic, the FR-09 tracking updates that
 * followed it ([IncidentGrouper]), and a summary.
 *
 * **Its own live read** ([AlertRepository.observeIncident]), from the panic
 * onwards, because the alert screens' window is the newest 100 raw entries
 * and a tracking band pushes a panic out of it within minutes. It is the only
 * reader of the tracking updates: no list shows them. Scoped to the
 * Fragment, which the host removes when the screen closes, so clearing this
 * ViewModel cancels the read and `awaitClose` removes the listener; Log Out
 * finishes the host and does the same.
 *
 * The panic is the only one this instance ever shows ([panicSequenceId]); a
 * different panic is a new screen.
 *
 * Nothing here logs: the state is a path of the wearer's positions.
 *
 * @param layerPrefs read once, so the map opens on Track's base map.
 * @param ticks fires the "5 min ago" recompute. Empty by default, so a test
 *   never holds an endless `delay`; [factory] passes the real one.
 */
class IncidentViewModel(
    private val panicSequenceId: Long,
    private val alertRepository: AlertRepository,
    layerPrefs: MapLayerPrefs,
    private val placeRepository: PlaceRepository = PlaceRepository.NONE,
    private val clock: Clock = Clock.SYSTEM,
    private val ticks: Flow<Unit> = emptyFlow()
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncidentUiState(layer = layerPrefs.layer))
    val uiState: StateFlow<IncidentUiState> = _uiState.asStateFlow()

    private val _events = Channel<IncidentEvent>(Channel.BUFFERED)
    val events: Flow<IncidentEvent> = _events.receiveAsFlow()

    /** The live read, cancelled and replaced on retry. */
    private var observeJob: Job? = null

    /** Whether the panic point's name has been asked for, or is being. */
    private var placeRequested = false

    /** When a point the geocoder could not answer may be asked again. */
    private var placeRetryAt = 0L

    init {
        observe()
        viewModelScope.launch {
            ticks.collect { _uiState.update { it.copy(nowMillis = clock.nowMillis()) } }
        }
    }

    /** Re-subscribes after a failure; the cancel removes the old listener first. */
    fun onRetryClicked() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            alertRepository.observeIncident(panicSequenceId).collect { result ->
                result
                    .onSuccess { history -> render(history) }
                    .onFailure { error -> reportFailure(error) }
            }
        }
    }

    private fun render(history: AlertHistory) {
        val incident = IncidentGrouper.incidentFor(panicSequenceId, history.alerts)
        _uiState.update {
            it.copy(
                isLoading = false,
                incident = incident,
                errorMessage = if (incident == null) MSG_NOT_FOUND else null,
                nowMillis = clock.nowMillis()
            )
        }
        incident?.panicLocation?.let(::lookUpPlace)
    }

    /** Names the panic point once, at the place names' rounding, as the alert rows do. */
    private fun lookUpPlace(location: Alert.Location) {
        if (placeRequested || clock.nowMillis() < placeRetryAt) return
        placeRequested = true
        viewModelScope.launch {
            val lookup = placeRepository.placeAt(
                AlertPlaces.rounded(location.lat),
                AlertPlaces.rounded(location.lng)
            )
            when (lookup) {
                is PlaceLookup.Found -> AlertPlaces.label(lookup.place)?.let { name ->
                    _uiState.update { it.copy(placeName = name) }
                }
                // No name exists: the coordinates stay.
                PlaceLookup.NotFound -> Unit
                PlaceLookup.Unavailable -> {
                    placeRequested = false
                    placeRetryAt = clock.nowMillis() + PLACE_RETRY_MS
                }
            }
        }
    }

    /** With a path on screen a failure is a notice; with nothing, the error view. */
    private suspend fun reportFailure(error: Throwable) {
        val message = messageFor(error)
        val hasContent = _uiState.value.incident != null
        _uiState.update {
            it.copy(isLoading = false, errorMessage = if (hasContent) null else message)
        }
        if (hasContent) _events.send(IncidentEvent.ShowMessage(message))
    }

    /** Wording for each [AlertError]; Kotlin, not `strings.xml`, because a ViewModel holds no Context. */
    private fun messageFor(error: Throwable): String = when (error) {
        AlertError.Network -> MSG_NETWORK
        AlertError.PermissionDenied -> MSG_PERMISSION_DENIED
        AlertError.NotSignedIn -> MSG_NOT_SIGNED_IN
        else -> MSG_LOAD_FAILED
    }

    companion object {
        /** How often "5 min ago" is recomputed: it reads the clock, nothing else. */
        const val TICK_INTERVAL_MS = 30_000L

        /** How long a point the geocoder could not answer waits before it is asked again. */
        const val PLACE_RETRY_MS = 60_000L

        const val MSG_LOAD_FAILED = "Couldn't load this incident."
        const val MSG_NETWORK = "Couldn't reach your band. Check your connection."
        const val MSG_PERMISSION_DENIED = "You don't have access to this band's alerts."
        const val MSG_NOT_SIGNED_IN = "Please sign in again to see your band's alerts."
        const val MSG_NOT_FOUND = "This panic is no longer in your band's history."

        /** The factory for the panic numbered [panicSequenceId], from the Fragment's arguments. */
        fun factory(panicSequenceId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                IncidentViewModel(
                    panicSequenceId,
                    RepositoryProvider.alertRepository,
                    MapLayerPrefs.from(application),
                    GeocoderPlaceRepository(application),
                    ticks = everyTick()
                )
            }
        }

        /** The real ticker. A plain loop, so nothing schedules work when unused. */
        private fun everyTick(): Flow<Unit> = flow {
            while (true) {
                delay(TICK_INTERVAL_MS)
                emit(Unit)
            }
        }
    }
}
