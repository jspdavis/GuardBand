package com.example.guardband.ui.device

import com.example.guardband.data.repository.WearerConnectionError
import com.example.guardband.testing.FakeWearerConnectionRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InviteCodeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repo: FakeWearerConnectionRepository = FakeWearerConnectionRepository()) =
        InviteCodeViewModel(repo)

    @Test
    fun `generate success updates state with code`() = runTest {
        val repo = FakeWearerConnectionRepository().apply {
            generateResult = Result.success("ABC123")
        }
        val vm = viewModel(repo)

        vm.generate()
        advanceUntilIdle()

        assertEquals("ABC123", vm.uiState.value.generatedCode)
        assertEquals(InviteCodeViewModel.MSG_CODE_GENERATED, vm.uiState.value.statusMessage)
    }

    @Test
    fun `generate failure shows network message`() = runTest {
        val repo = FakeWearerConnectionRepository().apply {
            generateResult = Result.failure(WearerConnectionError.Network(null))
        }
        val vm = viewModel(repo)

        vm.generate()
        advanceUntilIdle()

        assertNull(vm.uiState.value.generatedCode)
        assertEquals(InviteCodeViewModel.MSG_NETWORK_ERROR, vm.uiState.value.statusMessage)
    }

    @Test
    fun `generate permission denied shows permission message`() = runTest {
        val repo = FakeWearerConnectionRepository().apply {
            generateResult = Result.failure(WearerConnectionError.PermissionDenied)
        }
        val vm = viewModel(repo)

        vm.generate()
        advanceUntilIdle()

        assertNull(vm.uiState.value.generatedCode)
        assertEquals(InviteCodeViewModel.MSG_PERMISSION_DENIED, vm.uiState.value.statusMessage)
    }

    @Test
    fun `redeem valid code sets redeemed true`() = runTest {
        val vm = viewModel()

        vm.redeem("ABCDEF")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.redeemed)
    }

    @Test
    fun `redeem short code shows format message`() = runTest {
        val vm = viewModel()

        vm.redeem("AB")
        // No coroutine launched for a format error — no advanceUntilIdle needed.

        assertEquals(InviteCodeViewModel.MSG_INVALID_FORMAT, vm.uiState.value.statusMessage)
    }

    @Test
    fun `redeem expired code shows expired message`() = runTest {
        val repo = FakeWearerConnectionRepository().apply {
            redeemResult = Result.failure(WearerConnectionError.CodeExpired)
        }
        val vm = viewModel(repo)

        vm.redeem("ABCDEF")
        advanceUntilIdle()

        assertEquals(InviteCodeViewModel.MSG_CODE_EXPIRED, vm.uiState.value.statusMessage)
    }

    @Test
    fun `redeem not found shows not found message`() = runTest {
        val repo = FakeWearerConnectionRepository().apply {
            redeemResult = Result.failure(WearerConnectionError.CodeNotFound)
        }
        val vm = viewModel(repo)

        vm.redeem("ABCDEF")
        advanceUntilIdle()

        assertEquals(InviteCodeViewModel.MSG_CODE_NOT_FOUND, vm.uiState.value.statusMessage)
    }
}
