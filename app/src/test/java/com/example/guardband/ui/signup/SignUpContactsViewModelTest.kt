package com.example.guardband.ui.signup

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
 * Sign-Up Step 3: staging the emergency contacts.
 *
 * Nothing here writes — the commit and its retry moved to
 * [SignUpConsentViewModelTest] when Consent became the last screen. This
 * ViewModel takes no repositories at all.
 *
 * No Robolectric: the contact rules are pure Kotlin.
 */
class SignUpContactsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = SignUpContactsViewModel()

    private fun add(
        name: String = "Jordan Lee",
        phone: String = "09171234567",
        relationship: String = "Friend"
    ) = viewModel.onEditorSubmitted("", name, phone, relationship)

    private suspend fun firstMessage() =
        (viewModel.events.first() as SignUpContactsEvent.ShowMessage).text

    // ── Staging ───────────────────────────────────────────────────────────────

    @Test
    fun `an added contact appears in the list`() {
        add()

        assertEquals(listOf("Jordan Lee"), viewModel.uiState.value.contacts.map { it.name })
    }

    @Test
    fun `the phone is staged as typed, for the repository to normalize`() {
        add(phone = "0917 123 4567")

        // Normalising here would mean two layers deciding the stored form.
        assertEquals("0917 123 4567", viewModel.uiState.value.contacts.single().phone)
    }

    @Test
    fun `staged contacts get a draft id, which never reaches the database`() {
        add()

        assertTrue(viewModel.uiState.value.contacts.single().id.startsWith("draft-"))
    }

    @Test
    fun `editing replaces rather than appends`() {
        add()
        val staged = viewModel.uiState.value.contacts.single()

        viewModel.onEditorSubmitted(staged.id, "Jordan Cruz", "09181234568", "Family")

        val contacts = viewModel.uiState.value.contacts
        assertEquals(1, contacts.size)
        assertEquals("Jordan Cruz", contacts.single().name)
        assertEquals(staged.id, contacts.single().id)
    }

    @Test
    fun `deleting removes the contact`() {
        add()
        val staged = viewModel.uiState.value.contacts.single()

        viewModel.onDeleteClicked(staged.id)

        assertTrue(viewModel.uiState.value.contacts.isEmpty())
    }

    @Test
    fun `deleting the last one is allowed, because nothing has been written`() {
        add()
        viewModel.onDeleteClicked(viewModel.uiState.value.contacts.single().id)

        // The repository's minimum protects a stored record; there is none yet.
        assertTrue(viewModel.uiState.value.contacts.isEmpty())
        assertFalse(viewModel.uiState.value.canContinue)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `a blank name is refused`() = runTest {
        add(name = "   ")

        assertEquals(SignUpContactsViewModel.MSG_CONTACT_NAME_INVALID, firstMessage())
        assertTrue(viewModel.uiState.value.contacts.isEmpty())
    }

    @Test
    fun `an invalid phone is refused`() = runTest {
        add(phone = "12345")

        assertEquals(SignUpContactsViewModel.MSG_CONTACT_PHONE_INVALID, firstMessage())
        assertTrue(viewModel.uiState.value.contacts.isEmpty())
    }

    @Test
    fun `a too-long relationship is refused`() = runTest {
        add(relationship = "x".repeat(InputValidator.MAX_RELATIONSHIP_LENGTH + 1))

        assertEquals(SignUpContactsViewModel.MSG_CONTACT_RELATIONSHIP_INVALID, firstMessage())
    }

    @Test
    fun `a blank relationship is fine, because it is optional`() {
        add(relationship = "")

        assertEquals("", viewModel.uiState.value.contacts.single().relationship)
    }

    @Test
    fun `the cap is enforced while staging`() = runTest {
        repeat(InputValidator.MAX_CONTACTS) { add(name = "Contact $it") }

        add(name = "One too many")

        assertEquals(SignUpContactsViewModel.MSG_MAXIMUM_REACHED, firstMessage())
        assertEquals(InputValidator.MAX_CONTACTS, viewModel.uiState.value.contacts.size)
    }

    // ── The minimum, and the banner ───────────────────────────────────────────

    @Test
    fun `continue is blocked with no contacts`() = runTest {
        viewModel.onContinueClicked("Alex Rivera", "alex@guardband.com", "password123")

        assertEquals(SignUpContactsViewModel.MSG_ONE_CONTACT_REQUIRED, firstMessage())
    }

    @Test
    fun `one contact is enough to continue`() {
        add()

        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun `the banner keeps asking until three, though one is enough`() {
        assertTrue(viewModel.uiState.value.showMinimumNotice)

        add(name = "One")
        assertTrue(viewModel.uiState.value.showMinimumNotice)
        assertTrue(viewModel.uiState.value.canContinue)

        add(name = "Two")
        assertTrue(viewModel.uiState.value.showMinimumNotice)

        add(name = "Three")
        assertEquals(InputValidator.MIN_CONTACTS, viewModel.uiState.value.contacts.size)
        assertFalse(viewModel.uiState.value.showMinimumNotice)
    }

    @Test
    fun `the banner comes back if the user deletes down below three`() {
        repeat(InputValidator.MIN_CONTACTS) { add(name = "Contact $it") }
        assertFalse(viewModel.uiState.value.showMinimumNotice)

        viewModel.onDeleteClicked(viewModel.uiState.value.contacts.first().id)

        assertTrue(viewModel.uiState.value.showMinimumNotice)
    }

    // ── Handing over ──────────────────────────────────────────────────────────

    @Test
    fun `continue carries the whole wizard to Consent`() = runTest {
        add()
        viewModel.onContinueClicked("Alex Rivera", "alex@guardband.com", "password123")

        val event = viewModel.events.first() as SignUpContactsEvent.NavigateToConsent

        assertEquals("Alex Rivera", event.name)
        assertEquals("alex@guardband.com", event.email)
        assertEquals("password123", event.password)
        assertEquals(listOf("Jordan Lee"), event.contacts.map { it.name })
        assertFalse(event.completeProfile)
    }

    @Test
    fun `the mode flag rides along so Consent knows not to create an account`() = runTest {
        viewModel.setCompleteProfileMode(true)
        add()
        viewModel.onContinueClicked("Alex Rivera", "alex@guardband.com", "")

        val event = viewModel.events.first() as SignUpContactsEvent.NavigateToConsent

        assertTrue(event.completeProfile)
        assertTrue(viewModel.uiState.value.completeProfile)
    }
}
