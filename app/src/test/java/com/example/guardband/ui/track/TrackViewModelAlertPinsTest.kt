package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeMapLayerPrefs
import com.example.guardband.testing.MainDispatcherRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Track's alert pins and the label over a tapped one.
 *
 * Which alerts are pinned is [AlertPinSelector]'s, tested on its own; this
 * covers what the ViewModel does with the choice: the list handed over from
 * the sheet, the band's position hiding a pin, and the label opening,
 * moving, aging and closing.
 *
 * Every ViewModel is built inside its test (it launches in `init`, so a field
 * initializer would run before [MainDispatcherRule] swaps Main), and the
 * ticker is an empty or hand-driven flow, never a `delay` loop.
 */
class TrackViewModelAlertPinsTest {

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

    /** About 111 m of latitude per step, so no two spots share a pin. */
    private fun spot(index: Int) = Alert.Location(10.3157 + index * 0.001, 123.8854, 8.5)

    private fun alert(
        sequenceId: Long,
        type: String = "PANIC",
        location: Alert.Location? = spot(sequenceId.toInt()),
        millis: Long = t0 - 5 * 60_000L
    ) = Alert(
        schemaVersion = "1.0",
        deviceId = "guardband-001",
        type = type,
        timestamp = isoAt(millis),
        location = location,
        battery = Alert.Battery(64, false),
        sequenceId = sequenceId
    )

    private fun viewModel(
        repository: FakeAlertRepository = FakeAlertRepository(),
        clock: Clock = FakeClock(t0),
        ticks: Flow<Unit> = emptyFlow()
    ) = TrackViewModel(
        repository,
        FakeAuthRepository(signedIn = true),
        FakeMapLayerPrefs(),
        clock,
        ticks
    )

    private fun TrackViewModel.pinIds() = uiState.value.alertPins.map { it.alert.sequenceId }

    // ── Pin state ─────────────────────────────────────────────────────────────

    @Test
    fun `there are no pins and no label before any alerts arrive`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(emptyList<AlertPin>(), vm.uiState.value.alertPins)
        assertNull(vm.uiState.value.pinLabel)
    }

    @Test
    fun `the sheet's alerts become pins, newest first, without tracking or unknown types`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAlertsChanged(
            listOf(
                alert(5, "FALL_DETECTED"),
                alert(4, "LOW_BATTERY"),
                alert(3, "TRACKING_UPDATE"),
                alert(2, "CHECKIN"),
                alert(1, "PANIC")
            )
        )

        assertEquals(listOf(4L, 2L, 1L), vm.pinIds())
        assertEquals(
            listOf(AlertType.LOW_BATTERY, AlertType.CHECKIN, AlertType.PANIC),
            vm.uiState.value.alertPins.map { it.type }
        )
    }

    @Test
    fun `pins follow each new list, and old ones go`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAlertsChanged((1L..10L).map { alert(it) })
        vm.onAlertsChanged((3L..12L).map { alert(it) })

        assertEquals((12L downTo 3L).toList(), vm.pinIds())

        vm.onAlertsChanged(emptyList())

        assertEquals(emptyList<Long>(), vm.pinIds())
    }

    @Test
    fun `the pins add no history listener of their own`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onAlertsChanged((1L..3L).map { alert(it) })
        advanceUntilIdle()

        assertEquals(0, repository.historySubscriptions)
    }

    @Test
    fun `a pin under the band's marker is hidden until the band moves away`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(2), alert(1)))

        repository.emitLatest(alert(9, "TRACKING_UPDATE", location = spot(2), millis = t0))
        advanceUntilIdle()

        assertEquals(listOf(1L), vm.pinIds())

        repository.emitLatest(alert(10, "TRACKING_UPDATE", location = spot(7), millis = t0))
        advanceUntilIdle()

        assertEquals(listOf(2L, 1L), vm.pinIds())
    }

    @Test
    fun `alerts handed over before the band's first report are re-chosen around it`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        vm.onAlertsChanged(listOf(alert(2), alert(1)))

        repository.emitLatest(alert(9, "TRACKING_UPDATE", location = spot(1), millis = t0))
        advanceUntilIdle()

        assertEquals(listOf(2L), vm.pinIds())
    }

    // ── Label state ───────────────────────────────────────────────────────────

    @Test
    fun `tapping a pin opens its label with the type and how long ago`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(2, "CHECKIN"), alert(1, "PANIC", millis = t0 - 5 * 60_000L)))

        vm.onAlertPinTapped(alert(1, "PANIC", millis = t0 - 5 * 60_000L))

        val label = vm.uiState.value.pinLabel!!
        assertEquals(1L, label.pin.alert.sequenceId)
        assertEquals(AlertType.PANIC, label.pin.type)
        assertEquals(Elapsed.Minutes(5), label.elapsed)
    }

    @Test
    fun `tapping another pin moves the label to it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(2, "CHECKIN"), alert(1)))

        vm.onAlertPinTapped(alert(1))
        vm.onMapTapped()
        vm.onAlertPinTapped(alert(2, "CHECKIN"))

        assertEquals(2L, vm.uiState.value.pinLabel?.pin?.alert?.sequenceId)
    }

    @Test
    fun `tapping the map elsewhere closes the label`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(1)))
        vm.onAlertPinTapped(alert(1))

        vm.onMapTapped()

        assertNull(vm.uiState.value.pinLabel)
    }

    @Test
    fun `tapping the map with no label open changes nothing`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(1)))
        val before = vm.uiState.value

        vm.onMapTapped()

        assertEquals(before, vm.uiState.value)
    }

    @Test
    fun `an alert that is not a pin opens no label`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(1), alert(2, "TRACKING_UPDATE")))

        vm.onAlertPinTapped(alert(2, "TRACKING_UPDATE"))
        vm.onAlertPinTapped(alert(99))

        assertNull(vm.uiState.value.pinLabel)
    }

    @Test
    fun `an open label ages on the ticker`() = runTest {
        val clock = FakeClock(t0)
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val vm = viewModel(clock = clock, ticks = ticks)
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(1, millis = t0 - 5 * 60_000L)))
        vm.onAlertPinTapped(alert(1))

        clock.millis = t0 + 2 * 60 * 60_000L
        ticks.emit(Unit)
        advanceUntilIdle()

        assertEquals(Elapsed.Hours(2), vm.uiState.value.pinLabel?.elapsed)
    }

    @Test
    fun `an unreadable timestamp opens a label with no age, never a crash`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val odd = alert(1).copy(timestamp = "yesterday-ish")
        vm.onAlertsChanged(listOf(odd))

        vm.onAlertPinTapped(odd)

        assertEquals(1L, vm.uiState.value.pinLabel?.pin?.alert?.sequenceId)
        assertNull(vm.uiState.value.pinLabel?.elapsed)
    }

    @Test
    fun `the label stays open while new alerts arrive and its pin remains`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(1)))
        vm.onAlertPinTapped(alert(1))

        vm.onAlertsChanged(listOf(alert(3), alert(2), alert(1)))

        assertEquals(1L, vm.uiState.value.pinLabel?.pin?.alert?.sequenceId)
    }

    @Test
    fun `the label closes when its pin drops out of the newest ten`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAlertsChanged((1L..10L).map { alert(it) })
        vm.onAlertPinTapped(alert(1))

        vm.onAlertsChanged((1L..11L).map { alert(it) })

        assertNull(vm.uiState.value.pinLabel)
    }

    @Test
    fun `the label closes when the band moves onto its pin`() = runTest {
        val repository = FakeAlertRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(2), alert(1)))
        vm.onAlertPinTapped(alert(1))

        repository.emitLatest(alert(9, "TRACKING_UPDATE", location = spot(1), millis = t0))
        advanceUntilIdle()

        assertNull(vm.uiState.value.pinLabel)
        assertEquals(listOf(2L), vm.pinIds())
    }

    @Test
    fun `the pins and the label survive a layer change`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAlertsChanged(listOf(alert(2), alert(1)))
        vm.onAlertPinTapped(alert(2))

        vm.onLayerSelected(MapLayer.SATELLITE)

        assertEquals(listOf(2L, 1L), vm.pinIds())
        assertEquals(2L, vm.uiState.value.pinLabel?.pin?.alert?.sequenceId)
    }
}
