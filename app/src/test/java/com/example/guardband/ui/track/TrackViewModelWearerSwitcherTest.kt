package com.example.guardband.ui.track

import com.example.guardband.data.model.ConnectedWearer
import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeBandSelectionStore
import com.example.guardband.testing.FakeMapLayerPrefs
import com.example.guardband.testing.FakeWearerConnectionRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Track's wearer switcher: which wearer it picks, and what it does with one
 * who has not claimed a band (2026-11-11: listed, never picked).
 */
class TrackViewModelWearerSwitcherTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ana = ConnectedWearer("uidAna", "Ana", deviceId = "bandAna")
    private val ben = ConnectedWearer("uidBen", "Ben", deviceId = null)

    private val connections = FakeWearerConnectionRepository()
    private val store = FakeBandSelectionStore()

    private fun viewModel() = TrackViewModel(
        FakeAlertRepository(),
        FakeAuthRepository(signedIn = true),
        FakeMapLayerPrefs(),
        clock = object : Clock { override fun nowMillis() = 0L },
        staleTicks = emptyFlow(),
        wearerConnectionRepository = connections,
        bandSelectionStore = store
    )

    @Test
    fun `on first load it switches to the first wearer with a band, skipping one without`() = runTest {
        val vm = viewModel()

        connections.wearers.value = Result.success(listOf(ben, ana))
        advanceUntilIdle()

        assertEquals("bandAna", store.selected.value)
        assertEquals("uidAna", vm.uiState.value.selectedWearerUid)
        assertEquals(listOf(ben, ana), vm.uiState.value.connectedWearers)
    }

    @Test
    fun `a wearer with no band is never picked by itself, so My Band stays`() = runTest {
        val vm = viewModel()

        connections.wearers.value = Result.success(listOf(ben))
        advanceUntilIdle()

        assertNull(store.selected.value)
        assertNull(vm.uiState.value.selectedWearerUid)
    }

    @Test
    fun `picking a wearer with no band says why and changes nothing`() = runTest {
        val vm = viewModel()
        connections.wearers.value = Result.success(listOf(ben))
        advanceUntilIdle()

        vm.onWearerSelected("uidBen")

        assertEquals(
            TrackViewModel.MSG_WEARER_NO_BAND,
            (vm.events.first() as TrackEvent.ShowMessage).text
        )
        assertNull(store.selected.value)
        assertNull(vm.uiState.value.selectedWearerUid)
    }

    @Test
    fun `picking a wearer with a band shows that band, and My Band goes back`() = runTest {
        val vm = viewModel()
        connections.wearers.value = Result.success(listOf(ben))
        advanceUntilIdle()
        connections.wearers.value = Result.success(listOf(ben, ana))
        advanceUntilIdle()

        vm.onWearerSelected("uidAna")
        assertEquals("bandAna", store.selected.value)
        assertEquals("uidAna", vm.uiState.value.selectedWearerUid)

        vm.onWearerSelected(null)
        assertNull(store.selected.value)
        assertNull(vm.uiState.value.selectedWearerUid)
    }
}
