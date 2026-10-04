package com.example.guardband.data.repository

import com.example.guardband.data.DeviceConstants
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single in-memory source of truth for users, contacts, alerts and the session,
 * shared by the InMemory* repositories.
 *
 * Passwords are held in [StoredUser] and never leave this file: every read
 * returns a [User] or a copy of the contact list. Nothing is persisted, so the
 * session is lost when the process dies.
 *
 * Not thread-safe by design — all callers touch it from the main thread
 * (repositories via viewModelScope).
 */
internal object InMemoryStore {

    /** Simulated backend latency for every repository call. */
    const val SIMULATED_DELAY_MS = 1200L

    /** User record including the credential; private to the data layer. */
    private data class StoredUser(val user: User, val password: String)

    // ── Seed data ─────────────────────────────────────────────────────────────

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

    /** Removes the contact with [contactId]. Returns false if no such contact. */
    fun removeContact(contactId: String): Boolean =
        contacts.removeAll { it.id == contactId }

    // ── Alerts ────────────────────────────────────────────────────────────────

    /**
     * Seeded alerts for the default device, as the band would write them
     * (SCHEMA.md v1.0). Observable so a repository can follow changes live.
     */
    private val _alerts = MutableStateFlow(
        listOf(
            seedAlert(1, "PANIC", "2026-10-03T13:05:12Z", battery = 64),
            seedAlert(2, "TRACKING_UPDATE", "2026-10-03T13:06:12Z", battery = 64),
            seedAlert(3, "CHECKIN", "2026-10-04T02:30:00Z", battery = 41),
            seedAlert(4, "LOW_BATTERY", "2026-10-04T08:45:00Z", battery = 20)
        )
    )
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private fun seedAlert(sequenceId: Long, type: String, timestamp: String, battery: Int) =
        Alert(
            schemaVersion = "1.0",
            deviceId = DeviceConstants.DEFAULT_DEVICE_ID,
            type = type,
            timestamp = timestamp,
            location = Alert.Location(lat = 10.3157, lng = 123.8854, accuracyMeters = 8.5),
            battery = Alert.Battery(percent = battery, isCharging = false),
            sequenceId = sequenceId
        )
}
