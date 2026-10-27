package com.example.guardband.ui.incident

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.model.Alert
import com.example.guardband.data.repository.AlertError
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.PlaceLookup
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeMapLayerPrefs
import com.example.guardband.testing.FakePlaceRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.track.Clock
import com.example.guardband.ui.track.Elapsed
import com.example.guardband.ui.track.MapLayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Incident view's state: the summary, the "no tracking updates yet"
 * state, errors, the place name, aging, and that its read is its own and goes
 * away with it.
 *
 * Every ViewModel is built inside its test; the ticker is empty or driven by
 * hand, never a `delay` loop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 2025-10-09T08:53:20Z. */
    private val t0 = 1_760_000_000_000L
    private val minute = 60_000L

    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis(): Long = millis
    }

    private fun isoAt(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private fun spot(index: Int) = Alert.Location(10.3157 + index * 0.0001, 123.8854, 8.5)

    private fun alert(sequenceId: Long, type: String, millis: Long, battery: Int = 70) = Alert(
        schemaVersion = "1.0",
        deviceId = "guardband-001",
        type = type,
        timestamp = isoAt(millis),
        location = spot(sequenceId.toInt()),
        battery = Alert.Battery(battery, false),
        sequenceId = sequenceId
    )

    /** What the read returns: newest first, as the repository gives it. */
    private fun history(vararg alerts: Alert) = AlertHistory(
        alerts = alerts.sortedByDescending { it.sequenceId },
        rawCount = alerts.size,
        malformedCount = 0
    )

    /** The panic, 10 minutes before [t0]. */
    private val panic get() = alert(10, "PANIC", t0 - 10 * minute, battery = 70)

    private fun viewModel(
        repository: FakeAlertRepository = FakeAlertRepository(),
        places: FakePlaceRepository = FakePlaceRepository(),
        clock: Clock = FakeClock(t0),
        ticks: Flow<Unit> = emptyFlow(),
        layer: MapLayer = MapLayer.AUTO
    ) = IncidentViewModel(10, repository, FakeMapLayerPrefs(layer), places, clock, ticks)

    // ── Summary ───────────────────────────────────────────────────────────────

    @Test
    fun `the summary has the panic's time, place, the latest battery and the updates`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        repository.emitIncident(
            history(
                panic,
                alert(11, "TRACKING_UPDATE", t0 - 9 * minute, battery = 68),
                alert(12, "TRACKING_UPDATE", t0 - 2 * minute, battery = 66)
            )
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(10L, state.incident?.panic?.sequenceId)
        assertEquals(Elapsed.Minutes(10), state.panicElapsed)
        assertEquals(FakePlaceRepository.CEBU_LABEL, state.placeName)
        assertEquals(Alert.Battery(66, false), state.incident?.latestBattery)
        assertEquals(2, state.updateCount)
        assertEquals(12L, state.latestUpdate?.sequenceId)
        assertEquals(Elapsed.Minutes(2), state.latestUpdateElapsed)
        assertFalse(state.showsNoUpdates)
        assertEquals(listOf(spot(10), spot(11), spot(12)), state.incident?.path)
    }

    @Test
    fun `it reads its own panic onwards, and nothing from the alert screens' window`() = runTest {
        val repository = FakeAlertRepository()
        viewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf(10L), repository.requestedIncidents)
        assertEquals(0, repository.historySubscriptions)
    }

    @Test
    fun `it opens on the base map Track last used`() = runTest {
        assertEquals(MapLayer.SATELLITE, viewModel(layer = MapLayer.SATELLITE).uiState.value.layer)
    }

    @Test
    fun `it is loading until the first read`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.incident)
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    fun `a panic with no tracking updates says none has arrived`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        repository.emitIncident(history(panic, alert(11, "CHECKIN", t0)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.showsNoUpdates)
        assertEquals(0, state.updateCount)
        assertNull(state.latestUpdate)
        assertEquals(listOf(spot(10)), state.incident?.path)
        assertEquals(Alert.Battery(70, false), state.incident?.latestBattery)
    }

    @Test
    fun `updates arriving later extend the incident live`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        repository.emitIncident(history(panic))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showsNoUpdates)

        repository.emitIncident(history(panic, alert(11, "TRACKING_UPDATE", t0 - minute)))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showsNoUpdates)
        assertEquals(1, vm.uiState.value.updateCount)
    }

    @Test
    fun `the next panic ends this incident's path`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        repository.emitIncident(
            history(
                panic,
                alert(11, "TRACKING_UPDATE", t0 - 9 * minute),
                alert(12, "PANIC", t0 - 8 * minute),
                alert(13, "TRACKING_UPDATE", t0 - 7 * minute)
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(11L), vm.uiState.value.incident?.trackingUpdates?.map { it.sequenceId })
    }

    // ── Errors ────────────────────────────────────────────────────────────────

    @Test
    fun `a panic no longer in the history says so`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        repository.emitIncident(history(alert(11, "TRACKING_UPDATE", t0)))
        advanceUntilIdle()

        assertNull(vm.uiState.value.incident)
        assertEquals(IncidentViewModel.MSG_NOT_FOUND, vm.uiState.value.errorMessage)
    }

    @Test
    fun `a failed first read is the error view, and retry reads again`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        repository.failIncident(AlertError.Network)
        advanceUntilIdle()

        assertEquals(IncidentViewModel.MSG_NETWORK, vm.uiState.value.errorMessage)

        repository.emitIncident(history(panic))
        vm.onRetryClicked()
        advanceUntilIdle()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals(10L, vm.uiState.value.incident?.panic?.sequenceId)
        assertEquals(listOf(10L, 10L), repository.requestedIncidents)
        assertEquals(1, repository.activeIncidentCollectors)
    }

    @Test
    fun `a failure with the path on screen keeps it and says so once`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        val events = mutableListOf<IncidentEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        repository.emitIncident(history(panic))
        advanceUntilIdle()

        repository.failIncident(AlertError.PermissionDenied)
        advanceUntilIdle()

        assertEquals(10L, vm.uiState.value.incident?.panic?.sequenceId)
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(listOf(IncidentEvent.ShowMessage(IncidentViewModel.MSG_PERMISSION_DENIED)), events)
    }

    // ── Place, time, listener ─────────────────────────────────────────────────

    @Test
    fun `the place is asked for once, at the rounded panic point`() = runTest {
        val repository = FakeAlertRepository()
        val places = FakePlaceRepository()
        viewModel(repository, places)
        repository.emitIncident(history(panic))
        advanceUntilIdle()
        repository.emitIncident(history(panic, alert(11, "TRACKING_UPDATE", t0)))
        advanceUntilIdle()

        assertEquals(1, places.asked.size)
    }

    @Test
    fun `with no place name the coordinates stay`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository, FakePlaceRepository { _, _ -> PlaceLookup.NotFound })
        repository.emitIncident(history(panic))
        advanceUntilIdle()

        assertNull(vm.uiState.value.placeName)
    }

    @Test
    fun `the times age on the ticker`() = runTest {
        val repository = FakeAlertRepository()
        val clock = FakeClock(t0)
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val vm = viewModel(repository, clock = clock, ticks = ticks)
        repository.emitIncident(history(panic))
        advanceUntilIdle()

        clock.millis = t0 + 2 * 60 * minute
        ticks.emit(Unit)
        advanceUntilIdle()

        assertEquals(Elapsed.Hours(2), vm.uiState.value.panicElapsed)
    }

    @Test
    fun `closing the screen removes its read`() = runTest {
        val repository = FakeAlertRepository()
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { viewModel(repository) } }
        ViewModelProvider(store, factory)[IncidentViewModel::class.java]
        advanceUntilIdle()
        assertEquals(1, repository.activeIncidentCollectors)

        // What removing the Fragment does, and what Log Out does by finishing the host.
        store.clear()
        advanceUntilIdle()

        assertEquals(0, repository.activeIncidentCollectors)
    }
}
