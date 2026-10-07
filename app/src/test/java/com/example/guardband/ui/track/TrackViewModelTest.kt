package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert
import com.example.guardband.data.repository.AlertError
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Track tab: the user chip, the band's last known position, and the staleness
 * rules.
 *
 * The clock and the staleness ticker are both injected. The ticker defaults to
 * [emptyFlow] here so nothing fires in the background and each test drives the
 * recompute itself - the production default is an endless `delay` loop, which
 * a test must never be left holding.
 */
class TrackViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 2025-10-09T08:53:20Z. Only the differences from it matter. */
    private val t0 = 1_760_000_000_000L

    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis(): Long = millis
    }

    private fun isoAt(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    /** An alert with SCHEMA.md's shape, stamped at [millis]. */
    private fun reportAt(
        millis: Long,
        type: String = "TRACKING_UPDATE",
        location: Alert.Location? = Alert.Location(10.3157, 123.8854, 8.5),
        battery: Alert.Battery? = Alert.Battery(64, false)
    ) = Alert(
        schemaVersion = "1.0",
        deviceId = "guardband-001",
        type = type,
        timestamp = isoAt(millis),
        location = location,
        battery = battery,
        sequenceId = 1
    )

    private fun viewModel(
        repository: FakeAlertRepository = FakeAlertRepository(),
        clock: Clock = FakeClock(t0),
        ticks: Flow<Unit> = emptyFlow(),
        signedIn: Boolean = true
    ) = TrackViewModel(FakeAuthRepository(signedIn = signedIn), repository, clock, ticks)

    // ── The user chip ─────────────────────────────────────────────────────────

    @Test
    fun `the chip shows the session's name`() {
        val vm = viewModel()

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, vm.uiState.value.userName)
    }

    @Test
    fun `no session falls back to the default name`() {
        val vm = viewModel(signedIn = false)

        assertEquals(TrackViewModel.DEFAULT_USER_NAME, vm.uiState.value.userName)
    }

    // ── The no-fix state ──────────────────────────────────────────────────────

    @Test
    fun `it starts in the loading state`() {
        val vm = viewModel()

        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `a band that has never reported is the no-fix state`() = runTest {
        val vm = viewModel(FakeAlertRepository(latest = null))

        val state = vm.uiState.first { !it.isLoading }

        assertFalse(state.hasReported)
        assertFalse(state.hasFix)
        assertNull(state.location)
        assertNull(state.elapsed)
        assertFalse(state.isOnline)
        assertNull(state.errorMessage)
    }

    @Test
    fun `a report with no location is still no fix, but it has reported`() = runTest {
        // The band is talking and cannot see the sky. That reads differently
        // from a band that has never said anything, which is why hasReported
        // is separate from hasFix.
        val vm = viewModel(FakeAlertRepository(latest = reportAt(t0, location = null)))

        val state = vm.uiState.first { !it.isLoading }

        assertTrue(state.hasReported)
        assertFalse(state.hasFix)
        assertTrue(state.isOnline)
    }

    // ── Position and retention (D3) ───────────────────────────────────────────

    @Test
    fun `the latest position reaches the state and clears loading`() = runTest {
        val vm = viewModel(FakeAlertRepository(latest = reportAt(t0)))

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(Alert.Location(10.3157, 123.8854, 8.5), state.location)
        assertEquals(Alert.Battery(64, false), state.battery)
        assertTrue(state.hasFix)
    }

    @Test
    fun `a later report without a location keeps the last known one`() = runTest {
        val repository = FakeAlertRepository(latest = reportAt(t0))
        val vm = viewModel(repository)

        val first = vm.uiState.first { it.location != null }
        assertEquals(10.3157, first.location!!.lat, 0.0)

        // Stamped 30 s back so the new emission is tellable from the first,
        // whose elapsed was Seconds(0).
        repository.emitLatest(reportAt(t0 - 30_000L, location = null))

        val second = vm.uiState.first { it.elapsed == Elapsed.Seconds(30) }
        assertEquals(10.3157, second.location!!.lat, 0.0)
        assertTrue(second.hasFix)
    }

    @Test
    fun `a later report without a battery keeps the last known one`() = runTest {
        val repository = FakeAlertRepository(latest = reportAt(t0))
        val vm = viewModel(repository)

        vm.uiState.first { it.battery != null }
        repository.emitLatest(reportAt(t0 - 30_000L, battery = null))

        val state = vm.uiState.first { it.elapsed == Elapsed.Seconds(30) }
        assertEquals(Alert.Battery(64, false), state.battery)
    }

    @Test
    fun `a new position replaces the old one`() = runTest {
        val repository = FakeAlertRepository(latest = reportAt(t0))
        val vm = viewModel(repository)

        vm.uiState.first { it.location != null }
        repository.emitLatest(reportAt(t0, location = Alert.Location(1.0, 2.0, 3.0)))

        val state = vm.uiState.first { it.location?.lat == 1.0 }
        assertEquals(Alert.Location(1.0, 2.0, 3.0), state.location)
    }

    @Test
    fun `a deleted latest node clears the position rather than keeping a stale one`() = runTest {
        val repository = FakeAlertRepository(latest = reportAt(t0))
        val vm = viewModel(repository)

        vm.uiState.first { it.location != null }
        repository.emitLatest(null)

        val state = vm.uiState.first { it.location == null }
        assertFalse(state.hasReported)
        assertFalse(state.hasFix)
    }

    // ── Online and offline (D2) ───────────────────────────────────────────────

    @Test
    fun `a fresh report reads as online, with the elapsed time`() = runTest {
        val vm = viewModel(FakeAlertRepository(latest = reportAt(t0 - 30_000L)))

        val state = vm.uiState.first { !it.isLoading }

        assertTrue(state.isOnline)
        assertEquals(Elapsed.Seconds(30), state.elapsed)
    }

    @Test
    fun `a report older than the threshold reads as offline`() = runTest {
        val stale = t0 - BandStatus.ONLINE_THRESHOLD_MS - 1000L
        val vm = viewModel(FakeAlertRepository(latest = reportAt(stale)))

        val state = vm.uiState.first { !it.isLoading }

        assertFalse(state.isOnline)
        assertEquals(Elapsed.Minutes(3), state.elapsed)
    }

    @Test
    fun `the badge goes offline on a tick, with no new data at all`() = runTest {
        // The point of the ticker: a band going quiet emits nothing, so without
        // this the badge could never leave "online".
        val clock = FakeClock(t0)
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val vm = viewModel(FakeAlertRepository(latest = reportAt(t0)), clock, ticks)

        assertTrue(vm.uiState.first { !it.isLoading }.isOnline)

        clock.millis = t0 + BandStatus.ONLINE_THRESHOLD_MS
        ticks.emit(Unit)

        val state = vm.uiState.first { !it.isOnline }
        assertFalse(state.isOnline)
        assertEquals(Elapsed.Minutes(3), state.elapsed)
    }

    @Test
    fun `an unreadable timestamp leaves the staleness unknown rather than guessed`() = runTest {
        val alert = reportAt(t0).copy(timestamp = "not a timestamp")
        val vm = viewModel(FakeAlertRepository(latest = alert))

        val state = vm.uiState.first { !it.isLoading }

        // The position is still worth showing; how fresh it is, we cannot say.
        assertTrue(state.hasFix)
        assertNull(state.elapsed)
        assertFalse(state.isOnline)
    }

    // ── Navigate (D6) ─────────────────────────────────────────────────────────

    @Test
    fun `navigate is disabled until there is a fix`() = runTest {
        val vm = viewModel(FakeAlertRepository(latest = null))

        assertFalse(vm.uiState.first { !it.isLoading }.navigateEnabled)
    }

    @Test
    fun `navigate is enabled once there is a fix`() = runTest {
        val vm = viewModel(FakeAlertRepository(latest = reportAt(t0)))

        assertTrue(vm.uiState.first { it.hasFix }.navigateEnabled)
    }

    @Test
    fun `navigate hands out the maps URL for the last known position`() = runTest {
        val vm = viewModel(FakeAlertRepository(latest = reportAt(t0)))
        vm.uiState.first { it.hasFix }

        vm.onNavigateClicked()

        assertEquals(
            TrackEvent.OpenNavigation(
                "https://www.google.com/maps/search/?api=1&query=10.3157,123.8854"
            ),
            vm.events.first()
        )
    }

    @Test
    fun `a device with no maps app is told, not crashed`() = runTest {
        val vm = viewModel()

        vm.onNavigationUnavailable()

        assertEquals(
            TrackViewModel.MSG_NAVIGATION_UNAVAILABLE,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
    }

    // ── UI-only endpoints (D7) ────────────────────────────────────────────────

    @Test
    fun `check-in explains that the band sends it, rather than sending one`() = runTest {
        val repository = FakeAlertRepository(latest = reportAt(t0))
        val vm = viewModel(repository)

        vm.onCheckInClicked()

        assertEquals(
            TrackViewModel.MSG_CHECK_IN_ON_BAND,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
        // D7: the stub must not write anything.
        assertEquals(0, repository.requestedLimits.size)
    }

    @Test
    fun `recenter says the map is not available yet, until Phase 3 adds one`() = runTest {
        val vm = viewModel()

        vm.onRecenterClicked()

        assertEquals(
            TrackViewModel.MSG_MAP_UNAVAILABLE,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
    }

    @Test
    fun `the layers control says it is not available yet`() = runTest {
        val vm = viewModel()

        vm.onLayersClicked()

        assertEquals(
            TrackViewModel.MSG_MAP_LAYERS_UNAVAILABLE,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
    }

    // ── Failures ──────────────────────────────────────────────────────────────

    @Test
    fun `a failure with nothing on screen becomes the error state`() = runTest {
        val repository = FakeAlertRepository()
        repository.failLatest(AlertError.Network)
        val vm = viewModel(repository)

        val state = vm.uiState.first { it.errorMessage != null }

        assertEquals(TrackViewModel.MSG_NETWORK, state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `each alert error gets its own wording`() = runTest {
        val cases = listOf(
            AlertError.Network to TrackViewModel.MSG_NETWORK,
            AlertError.PermissionDenied to TrackViewModel.MSG_PERMISSION_DENIED,
            AlertError.NotSignedIn to TrackViewModel.MSG_NOT_SIGNED_IN,
            AlertError.ParseFailure to TrackViewModel.MSG_UNREADABLE,
            AlertError.Unknown(code = null) to TrackViewModel.MSG_LOAD_FAILED
        )

        for ((error, expected) in cases) {
            val repository = FakeAlertRepository()
            repository.failLatest(error)
            val vm = viewModel(repository)

            assertEquals(expected, vm.uiState.first { it.errorMessage != null }.errorMessage)
        }
    }

    @Test
    fun `a failure with a position on screen keeps it and reports an event`() = runTest {
        val repository = FakeAlertRepository(latest = reportAt(t0))
        val vm = viewModel(repository)
        vm.uiState.first { it.hasFix }

        repository.failLatest(AlertError.Network)

        assertEquals(
            TrackViewModel.MSG_NETWORK,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
        // The last known position outlives the failed read.
        assertTrue(vm.uiState.value.hasFix)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `retry puts the screen back into loading and re-subscribes`() = runTest {
        val repository = FakeAlertRepository()
        repository.failLatest(AlertError.Network)
        val vm = viewModel(repository)
        vm.uiState.first { it.errorMessage != null }

        vm.onRetryClicked()

        // Synchronous: the state flips before the new subscription is made.
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)

        // The replaying fake re-delivers the failure to the new collector,
        // which is how we know the re-subscribe actually happened.
        vm.uiState.first { it.errorMessage != null }
        assertEquals(2, repository.latestSubscriptions)
    }
}
