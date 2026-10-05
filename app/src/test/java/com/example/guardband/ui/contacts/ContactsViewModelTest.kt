package com.example.guardband.ui.contacts

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.repository.ContactError
import com.example.guardband.testing.FakeContactRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Contacts tab logic.
 *
 * No Robolectric: nothing here reaches `android.util.Patterns`, because the
 * contact rules are pure Kotlin.
 */
class ContactsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val valid = EmergencyContact(
        name = "Jordan Lee",
        phone = "09171234567",
        relationship = "Friend"
    )

    private fun viewModel(repository: FakeContactRepository) = ContactsViewModel(repository)

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Test
    fun `the observed list reaches the state and clears loading`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        val vm = viewModel(repository)

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(InputValidator.MIN_CONTACTS, state.contacts.size)
        assertFalse(state.loadFailed)
    }

    @Test
    fun `a failed read keeps the last good list on screen`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        repository.observeResult = Result.failure(ContactError.Network)
        // Nudge the flow so the failure is emitted to the collector.
        repository.addContact(valid)

        val state = vm.uiState.first { it.loadFailed }
        // Stale contacts are more use in an emergency than none.
        assertEquals(InputValidator.MIN_CONTACTS, state.contacts.size)
    }

    @Test
    fun `a failed read is reported with the mapped message, never the exception text`() = runTest {
        val repository = FakeContactRepository(emptyList())
            .also { it.observeResult = Result.failure(ContactError.PermissionDenied) }

        val vm = viewModel(repository)

        assertEquals(ContactsViewModel.MSG_REFUSED, firstMessage(vm))
    }

    @Test
    fun `the minimum notice shows below the minimum and hides at it`() = runTest {
        val below = viewModel(FakeContactRepository(listOf(valid.copy(id = "a"))))
        assertTrue(below.uiState.first { !it.isLoading }.showMinimumNotice)

        val atMinimum = viewModel(FakeContactRepository(FakeContactRepository.atMinimum()))
        assertFalse(atMinimum.uiState.first { !it.isLoading }.showMinimumNotice)
    }

    @Test
    fun `the empty state is not shown while the first read is still running`() = runTest {
        val vm = viewModel(FakeContactRepository(emptyList()))

        assertFalse(vm.uiState.value.showEmpty)
        assertTrue(vm.uiState.first { !it.isLoading }.showEmpty)
    }

    // ── Add ───────────────────────────────────────────────────────────────────

    @Test
    fun `the add button opens the editor with no contact to edit`() = runTest {
        val vm = viewModel(FakeContactRepository(FakeContactRepository.atMinimum()))
        vm.uiState.first { !it.isLoading }

        vm.onAddClicked()

        assertEquals(ContactsEvent.ShowContactEditor(null), vm.events.first())
    }

    @Test
    fun `the add button refuses at the cap instead of opening the editor`() = runTest {
        val vm = viewModel(FakeContactRepository(FakeContactRepository.atCap()))
        vm.uiState.first { !it.isLoading }

        vm.onAddClicked()

        assertEquals(ContactsViewModel.MSG_AT_CAP, firstMessage(vm))
    }

    @Test
    fun `submitting a new contact adds it and confirms`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onEditorSubmitted("", "Jordan Lee", "09171234567", "Friend")

        assertEquals(ContactsViewModel.MSG_CONTACT_ADDED, firstMessage(vm))
        assertEquals(1, repository.added.size)
        assertEquals(0, repository.updated.size)
    }

    @Test
    fun `the phone is handed to the repository as typed`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onEditorSubmitted("", "Jordan Lee", "0917 123 4567", "Friend")
        firstMessage(vm)

        // Normalising is the repository's job and only its job.
        assertEquals("0917 123 4567", repository.added.single().phone)
    }

    @Test
    fun `an invalid phone is refused before the repository is called`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onEditorSubmitted("", "Jordan Lee", "0912", "Friend")

        assertEquals(ContactsViewModel.MSG_PHONE_INVALID, firstMessage(vm))
        assertEquals(0, repository.added.size)
    }

    @Test
    fun `a blank name is refused before the repository is called`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onEditorSubmitted("", "   ", "09171234567", "Friend")

        assertEquals(ContactsViewModel.MSG_NAME_INVALID, firstMessage(vm))
        assertEquals(0, repository.added.size)
    }

    @Test
    fun `an over-long relationship is refused`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onEditorSubmitted(
            "", "Jordan Lee", "09171234567",
            "a".repeat(InputValidator.MAX_RELATIONSHIP_LENGTH + 1)
        )

        assertEquals(ContactsViewModel.MSG_RELATIONSHIP_INVALID, firstMessage(vm))
        assertEquals(0, repository.added.size)
    }

    @Test
    fun `the repository's cap verdict is reported even if the screen let it through`() = runTest {
        // The screen's own count can be stale; the repository is authoritative.
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        repository.addResult = Result.failure(ContactError.MaximumContacts)
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onEditorSubmitted("", "Jordan Lee", "09171234567", "Friend")

        assertEquals(ContactsViewModel.MSG_AT_CAP, firstMessage(vm))
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    @Test
    fun `the edit button opens the editor on that contact`() = runTest {
        val stored = valid.copy(id = "c-1")
        val vm = viewModel(FakeContactRepository(listOf(stored)))
        vm.uiState.first { !it.isLoading }

        vm.onEditClicked(stored)

        assertEquals(ContactsEvent.ShowContactEditor(stored), vm.events.first())
    }

    @Test
    fun `submitting with an id updates instead of adding`() = runTest {
        val repository = FakeContactRepository(listOf(valid.copy(id = "c-1")))
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onEditorSubmitted("c-1", "Morgan Smith", "09181234568", "Family")

        assertEquals(ContactsViewModel.MSG_CONTACT_SAVED, firstMessage(vm))
        assertEquals(0, repository.added.size)
        assertEquals("Morgan Smith", repository.updated.single().name)
    }

    @Test
    fun `editing a contact that is gone says so`() = runTest {
        val repository = FakeContactRepository(listOf(valid.copy(id = "c-1")))
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onEditorSubmitted("c-gone", "Morgan Smith", "09181234568", "Family")

        assertEquals(ContactsViewModel.MSG_CONTACT_GONE, firstMessage(vm))
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun `deleting above the minimum works and confirms`() = runTest {
        val repository = FakeContactRepository(
            FakeContactRepository.atMinimum() + valid.copy(id = "extra")
        )
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onDeleteConfirmed("extra")

        assertEquals(ContactsViewModel.MSG_CONTACT_DELETED, firstMessage(vm))
        assertEquals(InputValidator.MIN_CONTACTS, repository.stored.size)
    }

    @Test
    fun `deleting at the minimum is refused and says what to do instead`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onDeleteConfirmed("seed-0")

        assertEquals(ContactsViewModel.MSG_AT_MINIMUM, firstMessage(vm))
        assertEquals(InputValidator.MIN_CONTACTS, repository.stored.size)
    }

    @Test
    fun `the refused delete message names the minimum`() {
        assertTrue(
            ContactsViewModel.MSG_AT_MINIMUM.contains(InputValidator.MIN_CONTACTS.toString())
        )
        assertTrue(ContactsViewModel.MSG_AT_CAP.contains(InputValidator.MAX_CONTACTS.toString()))
    }

    @Test
    fun `a delete failure is mapped, never shown as exception text`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        repository.deleteResult = Result.failure(ContactError.Network)
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onDeleteConfirmed("seed-0")

        assertEquals(ContactsViewModel.MSG_NO_CONNECTION, firstMessage(vm))
    }

    @Test
    fun `an unmapped failure falls back to the generic message, never the code`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        repository.deleteResult = Result.failure(ContactError.Unknown(-99))
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onDeleteConfirmed("seed-0")

        assertEquals(ContactsViewModel.MSG_GENERIC_FAILURE, firstMessage(vm))
    }

    @Test
    fun `a lost session is reported as such`() = runTest {
        val repository = FakeContactRepository(FakeContactRepository.atMinimum())
        repository.deleteResult = Result.failure(ContactError.NotSignedIn)
        val vm = viewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.onDeleteConfirmed("seed-0")

        assertEquals(ContactsViewModel.MSG_SESSION_EXPIRED, firstMessage(vm))
    }

    private suspend fun firstMessage(vm: ContactsViewModel): String =
        (vm.events.first() as ContactsEvent.ShowMessage).text
}
