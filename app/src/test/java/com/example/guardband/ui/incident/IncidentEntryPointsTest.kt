package com.example.guardband.ui.incident

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeMapLayerPrefs
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.alert.AlertViewModel
import com.example.guardband.ui.home.HomeEvent
import com.example.guardband.ui.home.HomeViewModel
import com.example.guardband.ui.track.TrackEvent
import com.example.guardband.ui.track.TrackViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The three ways into an Incident view - a panic row (the sheet's
 * `recent`, Alert history's `history`), a panic pin, and "View incident" on
 * the band's bubble - all end at [HomeViewModel.onOpenIncident], the one
 * opener, and open the same incident.
 *
 * Every ViewModel is built inside its test, and no ticker runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentEntryPointsTest {

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

    private fun TestScope.homeViewModel(): Pair<HomeViewModel, List<HomeEvent>> {
        val vm = HomeViewModel(
            FakeAuthRepository(
                signedIn = true,
                emailVerified = true,
                providers = setOf(SignInProvider.PASSWORD)
            ),
            FakeUserProfileRepository()
        )
        val seen = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { seen += it } }
        return vm to seen
    }

    @Test
    fun `a panic row, a panic pin and the band's bubble open the same incident`() = runTest {
        val panic = alert(5, "PANIC")
        val repository = FakeAlertRepository(
            history = AlertHistory(
                alerts = listOf(alert(7, "TRACKING_UPDATE"), alert(6, "TRACKING_UPDATE"), panic, alert(4, "CHECKIN")),
                rawCount = 4,
                malformedCount = 0
            )
        )

        // The sheet's newest three and Alert history are both this list.
        val alerts = AlertViewModel(repository)
        advanceUntilIdle()
        val row = alerts.uiState.value.recent.first { IncidentEntry.opensIncident(it) }

        val track = TrackViewModel(
            repository,
            FakeAuthRepository(signedIn = true),
            FakeMapLayerPrefs(),
            staleTicks = emptyFlow()
        )
        val trackEvents = mutableListOf<TrackEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { track.events.collect { trackEvents += it } }
        advanceUntilIdle()
        track.onAlertsChanged(alerts.uiState.value.history)
        track.onAlertPinTapped(track.uiState.value.alertPins.single { it.alert.sequenceId == 5L }.alert)
        track.onViewIncidentClicked()
        advanceUntilIdle()

        val (home, homeEvents) = homeViewModel()
        advanceUntilIdle()
        home.onOpenIncident(row)
        trackEvents.filterIsInstance<TrackEvent.OpenIncident>().forEach { home.onOpenIncident(it.panic) }
        advanceUntilIdle()

        assertEquals(2, trackEvents.filterIsInstance<TrackEvent.OpenIncident>().size)
        assertEquals(
            List(3) { HomeEvent.ShowIncident(5) },
            homeEvents.filterIsInstance<HomeEvent.ShowIncident>()
        )
    }

    @Test
    fun `the one opener ignores anything that is not a panic`() = runTest {
        val (home, events) = homeViewModel()
        advanceUntilIdle()

        for (type in listOf("CHECKIN", "LOW_BATTERY", "TRACKING_UPDATE", "FALL_DETECTED")) {
            home.onOpenIncident(alert(3, type))
        }
        home.onOpenIncident(alert(4, "PANIC"))
        advanceUntilIdle()

        assertEquals(listOf(HomeEvent.ShowIncident(4)), events.filterIsInstance<HomeEvent.ShowIncident>())
    }

    @Test
    fun `only a panic opens an incident`() {
        assertTrue(IncidentEntry.opensIncident(alert(1, "PANIC")))
        for (type in listOf("CHECKIN", "LOW_BATTERY", "TRACKING_UPDATE", "FALL_DETECTED", "")) {
            assertEquals(false, IncidentEntry.opensIncident(alert(1, type)))
        }
    }
}
