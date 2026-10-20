package com.example.guardband.ui.alert

import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.PlaceRepository
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAlertRepository.Companion.alert
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.track.BandStatus
import com.example.guardband.ui.track.Clock
import com.example.guardband.ui.track.Elapsed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The rows' "5 min ago": measured from the clock at each read, and moved on
 * by the ticker while the band is quiet, with no new read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertRowTimesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Five minutes after FakeAlertRepository.alert's default timestamp. */
    private var now = checkNotNull(BandStatus.parseIsoUtcMillis("2026-10-04T08:50:00Z"))
    private val ticks = MutableSharedFlow<Unit>()

    private fun viewModel(repository: FakeAlertRepository) =
        AlertViewModel(repository, PlaceRepository.NONE, Clock { now }, ticks)

    private fun history(vararg alerts: com.example.guardband.data.model.Alert) =
        AlertHistory(alerts.sortedByDescending { it.sequenceId }, alerts.size, 0)

    private fun AlertViewModel.onlyRow() = uiState.value.rowsOf(uiState.value.history).single()

    @Test
    fun `a row's time is measured from the read`() = runTest {
        val vm = viewModel(FakeAlertRepository(history(alert(1, "PANIC"))))
        advanceUntilIdle()

        assertEquals(Elapsed.Minutes(5), vm.onlyRow().elapsed)
    }

    @Test
    fun `a tick moves it on without a new read`() = runTest {
        val repository = FakeAlertRepository(history(alert(1, "PANIC")))
        val vm = viewModel(repository)
        advanceUntilIdle()

        now += 2 * 60 * 60 * 1000L
        ticks.emit(Unit)
        advanceUntilIdle()

        assertEquals(Elapsed.Hours(2), vm.onlyRow().elapsed)
        assertEquals(1, repository.historySubscriptions)
    }

    @Test
    fun `an unreadable timestamp has no time`() = runTest {
        val vm = viewModel(FakeAlertRepository(history(alert(1, "PANIC", timestamp = "soon"))))
        advanceUntilIdle()

        assertNull(vm.onlyRow().elapsed)
    }
}
