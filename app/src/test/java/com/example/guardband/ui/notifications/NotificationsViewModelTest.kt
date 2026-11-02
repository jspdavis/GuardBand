package com.example.guardband.ui.notifications

import com.example.guardband.data.repository.AlertError
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The inbox's wording for a failed read, one case per [AlertError]. The
 * ViewModel is built inside each test (handoff §9), with no ticker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `each alert error gets its own wording`() = runTest {
        val cases = mapOf(
            AlertError.Network to NotificationsViewModel.MSG_NETWORK,
            AlertError.PermissionDenied to NotificationsViewModel.MSG_PERMISSION_DENIED,
            AlertError.NotSignedIn to NotificationsViewModel.MSG_NOT_SIGNED_IN,
            AlertError.ParseFailure to NotificationsViewModel.MSG_UNREADABLE,
            AlertError.NoBand to NotificationsViewModel.MSG_NO_BAND,
            AlertError.Unknown(-9) to NotificationsViewModel.MSG_LOAD_FAILED
        )

        for ((error, expected) in cases) {
            val repository = FakeAlertRepository()
            repository.failHistory(error)
            val vm = NotificationsViewModel(repository, ticks = emptyFlow())

            val state = vm.uiState.first { it.errorMessage != null }

            assertEquals("wording for $error", expected, state.errorMessage)
        }
    }
}
