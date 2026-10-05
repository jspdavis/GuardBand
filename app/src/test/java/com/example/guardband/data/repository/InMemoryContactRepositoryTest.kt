package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRUD and the D2 bounds, against the in-memory double.
 *
 * Stands in for [FirebaseContactRepository], which cannot be unit-tested: it
 * needs a real [com.google.firebase.database.FirebaseDatabase] and the project
 * declares no mocking library. The two enforce the same rules in the same
 * order, so these tests cover the rules; what they cannot cover is the
 * transaction that makes those rules hold under a race.
 *
 * [InMemoryStore] is a process-wide object, so each test puts it back the way
 * it found it.
 */
class InMemoryContactRepositoryTest {

    private val repository = InMemoryContactRepository()

    private fun resetStore(to: List<EmergencyContact>) {
        InMemoryStore.replaceContacts(to)
    }

    private fun seed(count: Int): List<EmergencyContact> = List(count) { index ->
        EmergencyContact(
            id = "seed-$index",
            name = "Contact $index",
            phone = "+63917123456$index",
            relationship = "Friend"
        )
    }

    private val valid = EmergencyContact(
        name = "Jordan Lee",
        phone = "09171234567",
        relationship = "Friend"
    )

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Test
    fun `observeContacts emits the stored list`() = runTest {
        resetStore(seed(3))

        val emitted = repository.observeContacts().first()

        assertEquals(3, emitted.getOrThrow().size)
    }

    // ── Add ───────────────────────────────────────────────────────────────────

    @Test
    fun `adding stores the contact with the phone normalized to E164`() = runTest {
        resetStore(seed(3))

        val saved = repository.addContact(valid).getOrThrow()

        assertEquals("+639171234567", saved.phone)
        assertTrue(saved.id.isNotBlank())
        assertEquals(4, InMemoryStore.getContacts().size)
    }

    @Test
    fun `adding trims the name and the relationship`() = runTest {
        resetStore(emptyList())

        val saved = repository
            .addContact(valid.copy(name = "  Jordan Lee  ", relationship = "  Friend  "))
            .getOrThrow()

        assertEquals("Jordan Lee", saved.name)
        assertEquals("Friend", saved.relationship)
    }

    @Test
    fun `an invalid phone is refused as a validation failure`() = runTest {
        resetStore(emptyList())

        val result = repository.addContact(valid.copy(phone = "0912"))

        assertEquals(ContactError.Validation, result.exceptionOrNull())
        assertEquals(0, InMemoryStore.getContacts().size)
    }

    @Test
    fun `a blank name is refused as a validation failure`() = runTest {
        resetStore(emptyList())

        val result = repository.addContact(valid.copy(name = "   "))

        assertEquals(ContactError.Validation, result.exceptionOrNull())
    }

    @Test
    fun `an over-long relationship is refused`() = runTest {
        resetStore(emptyList())

        val result = repository.addContact(
            valid.copy(relationship = "a".repeat(InputValidator.MAX_RELATIONSHIP_LENGTH + 1))
        )

        assertEquals(ContactError.Validation, result.exceptionOrNull())
    }

    @Test
    fun `adding at the cap is refused by the repository, not only the UI`() = runTest {
        resetStore(seed(InputValidator.MAX_CONTACTS))

        val result = repository.addContact(valid)

        assertEquals(ContactError.MaximumContacts, result.exceptionOrNull())
        assertEquals(InputValidator.MAX_CONTACTS, InMemoryStore.getContacts().size)
    }

    @Test
    fun `adding one below the cap still works`() = runTest {
        resetStore(seed(InputValidator.MAX_CONTACTS - 1))

        assertTrue(repository.addContact(valid).isSuccess)
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    fun `updating replaces the stored fields`() = runTest {
        resetStore(seed(3))

        val result = repository.updateContact(
            EmergencyContact(
                id = "seed-1",
                name = "Morgan Smith",
                phone = "639181234568",
                relationship = ""
            )
        )

        assertTrue(result.isSuccess)
        val stored = InMemoryStore.getContacts().single { it.id == "seed-1" }
        assertEquals("Morgan Smith", stored.name)
        assertEquals("+639181234568", stored.phone)
        // A cleared relationship is cleared, not left behind.
        assertEquals("", stored.relationship)
    }

    @Test
    fun `updating an unknown id reports NotFound`() = runTest {
        resetStore(seed(3))

        val result = repository.updateContact(valid.copy(id = "no-such-contact"))

        assertEquals(ContactError.NotFound, result.exceptionOrNull())
    }

    @Test
    fun `updating with no id reports NotFound rather than inserting`() = runTest {
        resetStore(seed(3))

        val result = repository.updateContact(valid)

        assertEquals(ContactError.NotFound, result.exceptionOrNull())
        assertEquals(3, InMemoryStore.getContacts().size)
    }

    @Test
    fun `updating validates the phone too`() = runTest {
        resetStore(seed(3))

        val result = repository.updateContact(valid.copy(id = "seed-1", phone = "nope"))

        assertEquals(ContactError.Validation, result.exceptionOrNull())
        assertEquals("+639171234561", InMemoryStore.getContacts().single { it.id == "seed-1" }.phone)
    }

    @Test
    fun `updating is allowed at the minimum, which is the way out of a wrong number`() = runTest {
        resetStore(seed(InputValidator.MIN_CONTACTS))

        assertTrue(repository.updateContact(valid.copy(id = "seed-0")).isSuccess)
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun `deleting above the minimum works`() = runTest {
        resetStore(seed(InputValidator.MIN_CONTACTS + 1))

        assertTrue(repository.deleteContact("seed-0").isSuccess)
        assertEquals(InputValidator.MIN_CONTACTS, InMemoryStore.getContacts().size)
    }

    @Test
    fun `deleting at the minimum is blocked by the repository, not only the UI`() = runTest {
        resetStore(seed(InputValidator.MIN_CONTACTS))

        val result = repository.deleteContact("seed-0")

        assertEquals(ContactError.MinimumContacts, result.exceptionOrNull())
        assertEquals(InputValidator.MIN_CONTACTS, InMemoryStore.getContacts().size)
    }

    @Test
    fun `deleting below the minimum stays blocked`() = runTest {
        resetStore(seed(1))

        assertEquals(
            ContactError.MinimumContacts,
            repository.deleteContact("seed-0").exceptionOrNull()
        )
    }

    @Test
    fun `deleting an unknown id reports NotFound, not the minimum`() = runTest {
        // Order matters: the user is told what is actually wrong rather than
        // about a bound they have not hit.
        resetStore(seed(InputValidator.MIN_CONTACTS))

        assertEquals(
            ContactError.NotFound,
            repository.deleteContact("no-such-contact").exceptionOrNull()
        )
    }

    @Test
    fun `deleting with a blank id reports NotFound`() = runTest {
        resetStore(seed(InputValidator.MIN_CONTACTS + 1))

        assertEquals(ContactError.NotFound, repository.deleteContact("").exceptionOrNull())
    }
}
