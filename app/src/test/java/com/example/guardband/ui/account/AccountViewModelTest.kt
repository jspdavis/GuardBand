package com.example.guardband.ui.account

import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Account information: the session's details, and its own auth gate. */
class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the details come from the session`() {
        val state = AccountViewModel(FakeAuthRepository(signedIn = true)).uiState.value

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, state.name)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, state.email)
    }

    @Test
    fun `no session goes to Login instead of showing an empty account`() = runTest {
        val vm = AccountViewModel(FakeAuthRepository(signedIn = false))

        assertEquals(AccountEvent.NavigateToLogin, vm.events.first())
        assertEquals(AccountUiState(), vm.uiState.value)
    }
}
