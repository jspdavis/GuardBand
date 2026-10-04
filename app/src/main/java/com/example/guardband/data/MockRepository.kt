package com.example.guardband.data

import android.os.Handler
import android.os.Looper
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.User
import com.example.guardband.data.repository.InMemoryStore

/**
 * Simulates a backend repository using hardcoded data and delayed callbacks.
 * Replace each function body with real network/Firebase calls when ready.
 *
 * All callbacks are dispatched on the main thread via a Handler so callers
 * never need to worry about thread-switching during the mock phase.
 *
 * Temporary MVP facade: data lives in [InMemoryStore], shared with the MVVM
 * repositories. This object only maps to/from [UserModel]/[ContactModel].
 * Delete once every screen has been migrated.
 */
object MockRepository {

    // ── Simulated delay in milliseconds ──────────────────────────────────────
    private const val MOCK_DELAY_MS = 1200L

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
            val match = InMemoryStore.findByCredentials(email, password)
            if (match != null) {
                InMemoryStore.setSession(match.id)
                onSuccess(match.toUserModel(password))
            } else {
                onError("Invalid email or password.")
            }
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
            if (InMemoryStore.emailExists(user.email)) {
                onError("An account with that email already exists.")
            } else {
                val newUser = InMemoryStore.addUser(user.toUser(), user.password)
                InMemoryStore.setSession(newUser.id)
                onSuccess(newUser.toUserModel(user.password))
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
            if (InMemoryStore.emailExists(email)) {
                InMemoryStore.pendingResetCode = "123456" // fixed mock code
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
            if (code.trim() == InMemoryStore.pendingResetCode) onSuccess()
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
            if (InMemoryStore.updatePassword(email, newPassword)) {
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
        delayed { onSuccess(InMemoryStore.getContacts().map { it.toContactModel() }) }
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
            InMemoryStore.addContact(contact.toEmergencyContact())
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

    // ── Model mapping (MVP models ↔ data/model) ───────────────────────────────

    private fun User.toUserModel(password: String) =
        UserModel(id = id, name = name, email = email, location = location, password = password)

    private fun UserModel.toUser() =
        User(id = id, name = name, email = email, location = location)

    private fun EmergencyContact.toContactModel() =
        ContactModel(id = id, name = name, phoneNumber = phoneNumber, relationship = relationship)

    private fun ContactModel.toEmergencyContact() =
        EmergencyContact(id = id, name = name, phoneNumber = phoneNumber, relationship = relationship)
}
