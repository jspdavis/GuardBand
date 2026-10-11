package com.example.guardband.ui.alert

import com.example.guardband.data.repository.AlertError
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAlertRepository.Companion.alert
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Alert tab logic: D1 (tracking hidden), D2 (latest card from history) and D3
 * (window and cap), plus the error and retry paths.
 *
 * No Robolectric: nothing here reaches `android.util.Patterns`, and the
 * timestamp formatting that would need a device lives in [AlertFormatting],
 * not in the ViewModel.
 */
class AlertViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun history(vararg alerts: com.example.guardband.data.model.Alert) =
        AlertHistory(
            alerts = alerts.sortedByDescending { it.sequenceId },
            rawCount = alerts.size,
            malformedCount = 0
        )

    private fun viewModel(repository: FakeAlertRepository) = AlertViewModel(repository)

    // ── Loading to content ────────────────────────────────────────────────────

    @Test
    fun `starts in the loading state`() = runTest {
        val vm = viewModel(FakeAlertRepository())

        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `the observed history reaches the state and clears loading`() = runTest {
        val repository = FakeAlertRepository(history(alert(1, "PANIC"), alert(2, "CHECKIN")))
        val vm = viewModel(repository)

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(listOf(2L, 1L), state.history.map { it.sequenceId })
        assertNull(state.errorMessage)
        assertNull(state.emptyMessage)
    }

    @Test
    fun `it reads the D3 window`() = runTest {
        val repository = FakeAlertRepository(history(alert(1, "PANIC")))
        viewModel(repository).uiState.first { !it.isLoading }

        assertEquals(listOf(AlertRepository.DEFAULT_HISTORY_WINDOW), repository.requestedLimits)
    }

    // ── D1: tracking updates are hidden ───────────────────────────────────────

    @Test
    fun `tracking updates are hidden from the history list`() = runTest {
        val repository = FakeAlertRepository(
            history(
                alert(1, "PANIC"),
                alert(2, "TRACKING_UPDATE"),
                alert(3, "CHECKIN"),
                alert(4, "TRACKING_UPDATE")
            )
        )
        val state = viewModel(repository).uiState.first { !it.isLoading }

        assertEquals(listOf(3L, 1L), state.history.map { it.sequenceId })
        assertTrue(state.history.none { it.type == "TRACKING_UPDATE" })
    }

    @Test
    fun `an unknown type is shown rather than hidden`() = runTest {
        // Only TRACKING_UPDATE is filtered; a type from newer firmware stays.
        val repository = FakeAlertRepository(history(alert(1, "FALL_DETECTED")))
        val state = viewModel(repository).uiState.first { !it.isLoading }

        assertEquals(listOf(1L), state.history.map { it.sequenceId })
    }

    // ── D2: the latest card comes from history ────────────────────────────────

    @Test
    fun `the latest card is the newest non-tracking alert`() = runTest {
        val repository = FakeAlertRepository(
            history(alert(1, "PANIC"), alert(2, "CHECKIN"), alert(3, "TRACKING_UPDATE"))
        )
        val state = viewModel(repository).uiState.first { !it.isLoading }

        // Not sequenceId 3: that is the tracking update `latest` would hold.
        assertEquals(2L, state.latest?.sequenceId)
        assertEquals("CHECKIN", state.latest?.type)
    }

    @Test
    fun `a history of nothing but tracking updates leaves no latest card`() = runTest {
        val repository = FakeAlertRepository(
            history(alert(1, "TRACKING_UPDATE"), alert(2, "TRACKING_UPDATE"))
        )
        val state = viewModel(repository).uiState.first { !it.isLoading }

        assertNull(state.latest)
    }

    @Test
    fun `the latest alert repository is never subscribed to`() = runTest {
        val repository = FakeAlertRepository(history(alert(1, "PANIC")))
        viewModel(repository).uiState.first { !it.isLoading }

        // D2: the card is derived from history, so the extra listener on
        // devices/{id}/latest is never opened.
        assertEquals(0, repository.latestSubscriptions)
    }

    // ── D3: the display cap ───────────────────────────────────────────────────

    @Test
    fun `the history list is capped`() = runTest {
        val many = (1..80L).map { alert(it, "PANIC") }.toTypedArray()
        val repository = FakeAlertRepository(history(*many))

        val state = viewModel(repository).uiState.first { !it.isLoading }

        assertEquals(AlertViewModel.MAX_HISTORY, state.history.size)
    }

    @Test
    fun `the cap keeps the newest alerts`() = runTest {
        val many = (1..80L).map { alert(it, "PANIC") }.toTypedArray()
        val repository = FakeAlertRepository(history(*many))

        val state = viewModel(repository).uiState.first { !it.isLoading }

        assertEquals(80L, state.history.first().sequenceId)
        assertEquals(80L - AlertViewModel.MAX_HISTORY + 1, state.history.last().sequenceId)
    }

    @Test
    fun `the cap counts displayable alerts, not raw entries`() = runTest {
        // 60 raw, half of them tracking: 30 survive, so the cap never bites.
        val mixed = (1..60L)
            .map { alert(it, if (it % 2 == 0L) "TRACKING_UPDATE" else "PANIC") }
            .toTypedArray()
        val repository = FakeAlertRepository(history(*mixed))

        val state = viewModel(repository).uiState.first { !it.isLoading }

        assertEquals(30, state.history.size)
    }

    // ── Empty states say which kind of empty ──────────────────────────────────

    @Test
    fun `an empty history says the band has sent nothing`() = runTest {
        val state = viewModel(FakeAlertRepository()).uiState.first { !it.isLoading }

        assertEquals(AlertViewModel.MSG_EMPTY_NO_ALERTS, state.emptyMessage)
        assertTrue(state.history.isEmpty())
        assertNull(state.latest)
    }

    @Test
    fun `a window of only tracking updates says so instead of looking silent`() = runTest {
        val repository = FakeAlertRepository(
            history(alert(1, "TRACKING_UPDATE"), alert(2, "TRACKING_UPDATE"))
        )
        val state = viewModel(repository).uiState.first { !it.isLoading }

        assertEquals(AlertViewModel.MSG_EMPTY_ONLY_TRACKING, state.emptyMessage)
    }

    @Test
    fun `a window that was entirely unreadable says so`() = runTest {
        val repository = FakeAlertRepository(
            AlertHistory(alerts = emptyList(), rawCount = 4, malformedCount = 4)
        )
        val state = viewModel(repository).uiState.first { !it.isLoading }

        assertEquals(AlertViewModel.MSG_EMPTY_UNREADABLE, state.emptyMessage)
    }

    // ── Errors ────────────────────────────────────────────────────────────────

    @Test
    fun `a failure with nothing on screen becomes the error state`() = runTest {
        val repository = FakeAlertRepository()
        repository.failHistory(AlertError.Network)
        val vm = viewModel(repository)

        val state = vm.uiState.first { it.errorMessage != null }

        assertEquals(AlertViewModel.MSG_NETWORK, state.errorMessage)
        assertTrue(!state.isLoading)
    }

    @Test
    fun `each alert error gets its own wording`() = runTest {
        val cases = mapOf(
            AlertError.Network to AlertViewModel.MSG_NETWORK,
            AlertError.PermissionDenied to AlertViewModel.MSG_PERMISSION_DENIED,
            AlertError.NotSignedIn to AlertViewModel.MSG_NOT_SIGNED_IN,
            AlertError.ParseFailure to AlertViewModel.MSG_UNREADABLE,
            AlertError.Unknown(-9) to AlertViewModel.MSG_LOAD_FAILED
        )

        for ((error, expected) in cases) {
            val repository = FakeAlertRepository()
            repository.failHistory(error)

            val state = viewModel(repository).uiState.first { it.errorMessage != null }

            assertEquals(expected, state.errorMessage)
        }
    }

    @Test
    fun `a failure with content on screen keeps the list and reports an event`() = runTest {
        val repository = FakeAlertRepository(history(alert(1, "PANIC"), alert(2, "CHECKIN")))
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        repository.failHistory(AlertError.Network)

        // Stale incidents are more use than a blank screen, so the list stays
        // and the warning goes out as a one-shot event instead.
        val event = vm.events.first()
        assertEquals(AlertEvent.ShowMessage(AlertViewModel.MSG_NETWORK), event)
        assertEquals(2, vm.uiState.value.history.size)
        assertNull(vm.uiState.value.errorMessage)
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    @Test
    fun `retry clears the error and re-subscribes`() = runTest {
        val repository = FakeAlertRepository()
        repository.failHistory(AlertError.Network)
        val vm = viewModel(repository)
        vm.uiState.first { it.errorMessage != null }
        assertEquals(1, repository.historySubscriptions)

        // Retry first, so the good data can only arrive through the *new*
        // subscription rather than through the one that already failed.
        vm.onRetryClicked()
        repository.emitHistory(history(alert(1, "PANIC")))

        val state = vm.uiState.first { !it.isLoading && it.history.isNotEmpty() }

        assertNull(state.errorMessage)
        assertEquals(listOf(1L), state.history.map { it.sequenceId })
        // A second subscription, not a second listener stacked on the first:
        // the old job is cancelled before the new one starts.
        assertEquals(2, repository.historySubscriptions)
    }

    @Test
    fun `retry puts the screen back into loading`() = runTest {
        val repository = FakeAlertRepository()
        repository.failHistory(AlertError.Network)
        val vm = viewModel(repository)
        vm.uiState.first { it.errorMessage != null }

        vm.onRetryClicked()

        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
    }
}
