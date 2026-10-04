package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.User

/**
 * Single in-memory source of truth for users, contacts and the session.
 *
 * Shared by [com.example.guardband.data.MockRepository] (MVP screens) and the
 * InMemory* repositories (MVVM screens) so both see the same data during the
 * migration. Passwords are held in [StoredUser] and never leave this file:
 * every read returns a [User] or a copy of the contact list.
 *
 * Not thread-safe by design — all callers touch it from the main thread
 * (MockRepository via its main-looper Handler, repositories via viewModelScope).
 */
internal object InMemoryStore {

    /** Simulated backend latency; matches MockRepository's MOCK_DELAY_MS. */
    const val SIMULATED_DELAY_MS = 1200L

    /** User record including the credential; private to the data layer. */
    private data class StoredUser(val user: User, val password: String)

    // ── Seed data (identical to the original MockRepository seeds) ────────────

    private val users = mutableListOf(
        StoredUser(
            user = User(
                id = "mock-user-001",
                name = "Alex Rivera",
                email = "alex@guardband.com",
                location = "San Francisco, CA"
            ),
            password = "password123"
        )
    )

    private val contacts = mutableListOf(
        EmergencyContact("c-01", "Jordan Lee",   "+1-555-0101", "Friend"),
        EmergencyContact("c-02", "Morgan Smith", "+1-555-0202", "Family")
    )

    /** Fixed mock verification code for the forgot-password flow. */
    var pendingResetCode: String = "123456"

    private var currentUserId: String? = null

    // ── Users ─────────────────────────────────────────────────────────────────

    /** Returns the user whose email (trimmed, case-insensitive) and password match. */
    fun findByCredentials(email: String, password: String): User? =
        users.find {
            it.user.email.equals(email.trim(), ignoreCase = true) && it.password == password
        }?.user

    fun emailExists(email: String): Boolean =
        users.any { it.user.email.equals(email.trim(), ignoreCase = true) }

    /**
     * Stores a new user with a generated id ("mock-user-<millis>") and returns it.
     * Callers are responsible for checking [emailExists] first.
     */
    fun addUser(user: User, password: String): User {
        val newUser = user.copy(id = "mock-user-${System.currentTimeMillis()}")
        users.add(StoredUser(newUser, password))
        return newUser
    }

    /** Replaces the password of the user with [email]. Returns false if no such user. */
    fun updatePassword(email: String, newPassword: String): Boolean {
        val index = users.indexOfFirst {
            it.user.email.equals(email.trim(), ignoreCase = true)
        }
        if (index < 0) return false
        users[index] = users[index].copy(password = newPassword)
        return true
    }

    // ── Session ───────────────────────────────────────────────────────────────

    fun setSession(userId: String) {
        currentUserId = userId
    }

    fun clearSession() {
        currentUserId = null
    }

    fun currentUser(): User? =
        currentUserId?.let { id -> users.find { it.user.id == id }?.user }

    // ── Contacts ──────────────────────────────────────────────────────────────

    fun getContacts(): List<EmergencyContact> = contacts.toList()

    /** Stores [contact] with a generated id ("c-<millis>") and returns the stored copy. */
    fun addContact(contact: EmergencyContact): EmergencyContact {
        val saved = contact.copy(id = "c-${System.currentTimeMillis()}")
        contacts.add(saved)
        return saved
    }
}
