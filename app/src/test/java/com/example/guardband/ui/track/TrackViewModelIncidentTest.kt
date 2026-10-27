package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeMapLayerPrefs
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Track's two ways into an Incident view: a panic pin, and "View incident" on
 * the band's bubble ([TrackUiState.bandPanic]), offered while the band's
 * latest non-tracking alert is a panic.
 *
 * Every ViewModel is built inside its test, with no ticker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackViewModelIncidentTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun spot(index: Int) = Alert.Location(10.3157 + index * 0.001, 123.8854, 8.5)

    private fun alert(sequenceId: Long, type: String, location: Alert.Location? = spot(sequenceId.toInt())) =
        Alert(
            schemaVersion = "1.0",
            deviceId = "guardband-001",
            type = type,
            timestamp = "2026-10-27T08:00:00Z",
            location = location,
            battery = Alert.Battery(64, false),
            sequenceId = sequenceId
        )

    private class Opened(val vm: TrackViewModel, val repository: FakeAlertRepository, val events: List<TrackEvent>)

    private fun TestScope.open(): Opened {
        val repository = FakeAlertRepository()
        val vm = TrackViewModel(repository, FakeAuthRepository(signedIn = true), FakeMapLayerPrefs(), staleTicks = emptyFlow())
        val seen = mutableListOf<TrackEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { seen += it } }
        advanceUntilIdle()
        return Opened(vm, repository, seen)
    }

    private fun Opened.openIncidents() = events.filterIsInstance<TrackEvent.OpenIncident>().map { it.panic.sequenceId }

    // ── "View incident" on the band's bubble ──────────────────────────────────

    @Test
    fun `the bubble offers the incident while the latest non-tracking alert is a panic`() = runTest {
        val opened = open()

        opened.vm.onAlertsChanged(listOf(alert(5, "PANIC"), alert(4, "CHECKIN")))

        assertEquals(5L, opened.vm.uiState.value.bandPanic?.sequenceId)
    }

    @Test
    fun `tracking updates after the panic keep it offered`() = runTest {
        val opened = open()

        opened.vm.onAlertsChanged(
            listOf(alert(8, "TRACKING_UPDATE"), alert(7, "TRACKING_UPDATE"), alert(5, "PANIC"))
        )

        assertEquals(5L, opened.vm.uiState.value.bandPanic?.sequenceId)
    }

    @Test
    fun `a newer check-in or low-battery alert takes the offer away`() = runTest {
        val opened = open()
        opened.vm.onAlertsChanged(listOf(alert(5, "PANIC")))

        opened.vm.onAlertsChanged(listOf(alert(6, "CHECKIN"), alert(5, "PANIC")))
        assertNull(opened.vm.uiState.value.bandPanic)

        opened.vm.onAlertsChanged(listOf(alert(7, "LOW_BATTERY"), alert(5, "PANIC")))
        assertNull(opened.vm.uiState.value.bandPanic)
    }

    @Test
    fun `with no alerts, or no panic last, nothing is offered`() = runTest {
        val opened = open()
        assertNull(opened.vm.uiState.value.bandPanic)

        opened.vm.onAlertsChanged(listOf(alert(2, "CHECKIN"), alert(1, "LOW_BATTERY")))
        assertNull(opened.vm.uiState.value.bandPanic)

        opened.vm.onAlertsChanged(emptyList())
        assertNull(opened.vm.uiState.value.bandPanic)
    }

    @Test
    fun `a panic whose pin the face hides is still offered on the bubble`() = runTest {
        val opened = open()
        opened.vm.onAlertsChanged(listOf(alert(5, "PANIC", spot(1)), alert(4, "CHECKIN", spot(2))))

        opened.repository.emitLatest(alert(9, "TRACKING_UPDATE", spot(1)))
        advanceUntilIdle()

        assertEquals(listOf(4L), opened.vm.uiState.value.alertPins.map { it.alert.sequenceId })
        assertEquals(5L, opened.vm.uiState.value.bandPanic?.sequenceId)
    }

    @Test
    fun `View incident opens the offered panic`() = runTest {
        val opened = open()
        opened.vm.onAlertsChanged(listOf(alert(5, "PANIC")))

        opened.vm.onViewIncidentClicked()
        advanceUntilIdle()

        assertEquals(listOf(5L), opened.openIncidents())
    }

    @Test
    fun `View incident with nothing offered opens nothing`() = runTest {
        val opened = open()
        opened.vm.onAlertsChanged(listOf(alert(6, "CHECKIN"), alert(5, "PANIC")))

        opened.vm.onViewIncidentClicked()
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), opened.openIncidents())
    }

    // ── Pins ──────────────────────────────────────────────────────────────────

    @Test
    fun `a panic pin opens its incident, not a label`() = runTest {
        val opened = open()
        opened.vm.onAlertsChanged(listOf(alert(5, "PANIC"), alert(4, "CHECKIN")))

        opened.vm.onAlertPinTapped(alert(5, "PANIC"))
        advanceUntilIdle()

        assertEquals(listOf(5L), opened.openIncidents())
        assertNull(opened.vm.uiState.value.pinLabel)
    }

    @Test
    fun `other pins still open only their label`() = runTest {
        val opened = open()
        opened.vm.onAlertsChanged(listOf(alert(5, "PANIC"), alert(4, "CHECKIN"), alert(3, "LOW_BATTERY")))

        opened.vm.onAlertPinTapped(alert(4, "CHECKIN"))
        assertEquals(4L, opened.vm.uiState.value.pinLabel?.pin?.alert?.sequenceId)
        opened.vm.onAlertPinTapped(alert(3, "LOW_BATTERY"))
        advanceUntilIdle()

        assertEquals(3L, opened.vm.uiState.value.pinLabel?.pin?.alert?.sequenceId)
        assertEquals(emptyList<Long>(), opened.openIncidents())
    }
}
