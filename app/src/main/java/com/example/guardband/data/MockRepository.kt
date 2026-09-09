package com.example.guardband.data

import android.os.Handler
import android.os.Looper

/**
 * Simulates a backend repository using hardcoded data and delayed callbacks.
 * Replace each function body with real network/Firebase calls when ready.
 *
 * All callbacks are dispatched on the main thread via a Handler so callers
 * never need to worry about thread-switching during the mock phase.
 */
object MockRepository {

    // ── Simulated delay in milliseconds ──────────────────────────────────────
    private const val MOCK_DELAY_MS = 1200L

    // ── Seed data ─────────────────────────────────────────────────────────────
    private val seedUser = UserModel(
        id = "mock-user-001",
        name = "Alex Rivera",
        email = "alex@guardband.com",
        location = "San Francisco, CA",
        password = "password123"
    )

    private val seedContacts = mutableListOf(
        ContactModel("c-01", "Jordan Lee",   "+1-555-0101", "Friend"),
        ContactModel("c-02", "Morgan Smith", "+1-555-0202", "Family")
    )

    // ── Registered users store (in-memory, mock only) ─────────────────────────
    private val registeredUsers = mutableListOf(seedUser)

    // ── Pending reset code (mock only) ────────────────────────────────────────
    private var pendingResetCode: String = "123456"

    // ─────────────────────────────────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simulates login. Succeeds when email + password match a registered user.
     *
     * @param onSuccess Receives the matched [UserModel].
     * @param onError   Receives a human-readable error string.
     */
    fun login(
        email: String,
        password: String,
        onSuccess: (UserModel) -> Unit,
        onError: (String) -> Unit
    ) {
        delayed {
            val match = registeredUsers.find {
                it.email.equals(email.trim(), ignoreCase = true) && it.password == password
            }
            if (match != null) onSuccess(match)
            else onError("Invalid email or password.")
        }
    }

    /**
     * Simulates user registration. Always succeeds for mock purposes.
     */
    fun register(
        user: UserModel,
        onSuccess: (UserModel) -> Unit,
        onError: (String) -> Unit
    ) {
        delayed {
            val exists = registeredUsers.any {
                it.email.equals(user.email.trim(), ignoreCase = true)
            }
            if (exists) {
                onError("An account with that email already exists.")
            } else {
                val newUser = user.copy(id = "mock-user-${System.currentTimeMillis()}")
                registeredUsers.add(newUser)
                onSuccess(newUser)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Forgot Password
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simulates sending a password-reset email.
     * Always succeeds after the mock delay.
     */
    fun requestPasswordReset(
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        delayed {
            val exists = registeredUsers.any {
                it.email.equals(email.trim(), ignoreCase = true)
            }
            if (exists) {
                pendingResetCode = "123456" // fixed mock code
                onSuccess()
            } else {
                onError("No account found for that email.")
            }
        }
    }

    /**
     * Simulates verifying the reset code entered by the user.
     * Mock code is always "123456".
     */
    fun verifyResetCode(
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        delayed {
            if (code.trim() == pendingResetCode) onSuccess()
            else onError("Incorrect verification code. Try again.")
        }
    }

    /**
     * Simulates saving a new password for the user.
     * Always succeeds in mock mode.
     */
    fun resetPassword(
        email: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        delayed {
            val index = registeredUsers.indexOfFirst {
                it.email.equals(email.trim(), ignoreCase = true)
            }
            if (index >= 0) {
                registeredUsers[index] = registeredUsers[index].copy(password = newPassword)
                onSuccess()
            } else {
                onError("Session expired. Please restart the reset flow.")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Contacts
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the current list of mock contacts for the active user. */
    fun getContacts(
        onSuccess: (List<ContactModel>) -> Unit
    ) {
        delayed { onSuccess(seedContacts.toList()) }
    }

    /**
     * Adds a contact to the in-memory list.
     * Always succeeds in mock mode.
     */
    fun addContact(
        contact: ContactModel,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        delayed {
            seedContacts.add(contact.copy(id = "c-${System.currentTimeMillis()}"))
            onSuccess()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Posts [block] to the main thread after [MOCK_DELAY_MS]. */
    private fun delayed(block: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed(block, MOCK_DELAY_MS)
    }
}
