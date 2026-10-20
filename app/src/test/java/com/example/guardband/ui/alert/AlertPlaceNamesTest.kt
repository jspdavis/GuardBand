package com.example.guardband.ui.alert

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.Place
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.PlaceLookup
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAlertRepository.Companion.alert
import com.example.guardband.testing.FakePlaceRepository
import com.example.guardband.testing.FakePlaceRepository.Companion.CEBU_LABEL
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.track.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Place names on the alert rows: looked up after the list shows, once per
 * rounded point, newest first; a point with no name is never asked again,
 * and one the service could not answer is asked again only after
 * [AlertViewModel.PLACE_RETRY_MS].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertPlaceNamesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var now = 1_000_000L
    private val clock = Clock { now }
    private val places = FakePlaceRepository()

    private fun history(vararg alerts: Alert) =
        AlertHistory(alerts.sortedByDescending { it.sequenceId }, alerts.size, 0)

    private fun at(alert: Alert, lat: Double, lng: Double) =
        alert.copy(location = Alert.Location(lat = lat, lng = lng, accuracyMeters = 5.0))

    private fun viewModel(repository: FakeAlertRepository) = AlertViewModel(repository, places, clock)

    @Test
    fun `a shown alert's place name fills in after the list`() = runTest {
        val vm = viewModel(FakeAlertRepository(history(alert(1, "PANIC"))))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CEBU_LABEL, state.placeNameFor(state.history.single()))
        assertEquals(listOf(CEBU_LABEL), state.rowsOf(state.history).map { it.placeName })
    }

    @Test
    fun `alerts at the same point are looked up once`() = runTest {
        // FakeAlertRepository.alert puts every alert at the same point.
        viewModel(FakeAlertRepository(history(alert(1, "PANIC"), alert(2, "CHECKIN"), alert(3, "LOW_BATTERY"))))
        advanceUntilIdle()

        assertEquals(1, places.asked.size)
    }

    @Test
    fun `the geocoder is sent rounded coordinates`() = runTest {
        viewModel(FakeAlertRepository(history(at(alert(1, "PANIC"), 10.3157449, 123.8854551))))
        advanceUntilIdle()

        assertEquals(listOf(10.3157 to 123.8855), places.asked)
    }

    @Test
    fun `each distinct point is looked up, newest first`() = runTest {
        viewModel(
            FakeAlertRepository(
                history(
                    at(alert(1, "PANIC"), 10.30, 123.90),
                    at(alert(2, "PANIC"), 10.31, 123.91),
                    at(alert(3, "PANIC"), 10.32, 123.92)
                )
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(10.32 to 123.92, 10.31 to 123.91, 10.30 to 123.90), places.asked)
    }

    @Test
    fun `a known point is not asked again on the next emission`() = runTest {
        val repository = FakeAlertRepository(history(alert(1, "PANIC")))
        viewModel(repository)
        advanceUntilIdle()

        repository.emitHistory(history(alert(1, "PANIC"), alert(2, "CHECKIN")))
        advanceUntilIdle()

        assertEquals(1, places.asked.size)
    }

    @Test
    fun `an alert without a location is not looked up`() = runTest {
        val vm = viewModel(FakeAlertRepository(history(alert(1, "PANIC").copy(location = null))))
        advanceUntilIdle()

        assertTrue(places.asked.isEmpty())
        assertNull(vm.uiState.value.placeNameFor(vm.uiState.value.history.single()))
    }

    @Test
    fun `hidden tracking updates are not looked up`() = runTest {
        viewModel(FakeAlertRepository(history(at(alert(1, "TRACKING_UPDATE"), 10.40, 124.00))))
        advanceUntilIdle()

        assertTrue(places.asked.isEmpty())
    }

    @Test
    fun `a point with no name keeps its coordinates and is never asked again`() = runTest {
        places.answer = { _, _ -> PlaceLookup.NotFound }
        val repository = FakeAlertRepository(history(alert(1, "PANIC")))
        val vm = viewModel(repository)
        advanceUntilIdle()

        now += AlertViewModel.PLACE_RETRY_MS * 10
        repository.emitHistory(history(alert(1, "PANIC"), alert(2, "PANIC")))
        advanceUntilIdle()

        assertEquals(1, places.asked.size)
        assertTrue(vm.uiState.value.placeNames.isEmpty())
    }

    @Test
    fun `a place with nothing usable in it is treated as no name`() = runTest {
        places.answer = { _, _ -> PlaceLookup.Found(Place(street = "Unnamed Road")) }
        val vm = viewModel(FakeAlertRepository(history(alert(1, "PANIC"))))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.placeNames.isEmpty())
    }

    @Test
    fun `an unanswered point is not asked again within the retry wait`() = runTest {
        places.answer = { _, _ -> PlaceLookup.Unavailable }
        val repository = FakeAlertRepository(history(alert(1, "PANIC")))
        viewModel(repository)
        advanceUntilIdle()

        now += AlertViewModel.PLACE_RETRY_MS - 1
        repository.emitHistory(history(alert(1, "PANIC"), alert(2, "PANIC")))
        advanceUntilIdle()

        assertEquals(1, places.asked.size)
    }

    @Test
    fun `an unanswered point is asked again after the wait, and its name fills in`() = runTest {
        places.answer = { _, _ -> PlaceLookup.Unavailable }
        val repository = FakeAlertRepository(history(alert(1, "PANIC")))
        val vm = viewModel(repository)
        advanceUntilIdle()

        now += AlertViewModel.PLACE_RETRY_MS
        places.answer = { _, _ -> PlaceLookup.Found(FakePlaceRepository.CEBU) }
        repository.emitHistory(history(alert(1, "PANIC"), alert(2, "PANIC")))
        advanceUntilIdle()

        assertEquals(2, places.asked.size)
        assertEquals(CEBU_LABEL, vm.uiState.value.placeNameFor(vm.uiState.value.history.first()))
    }

    @Test
    fun `the latest card and the recent slice get the same name`() = runTest {
        val vm = viewModel(FakeAlertRepository(history(alert(1, "PANIC"))))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CEBU_LABEL, state.placeNameFor(checkNotNull(state.latest)))
        assertEquals(CEBU_LABEL, state.rowsOf(state.recent).single().placeName)
    }

    @Test
    fun `without a place repository every row keeps its coordinates`() = runTest {
        val vm = AlertViewModel(FakeAlertRepository(history(alert(1, "PANIC"))))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.placeNames.isEmpty())
    }
}
