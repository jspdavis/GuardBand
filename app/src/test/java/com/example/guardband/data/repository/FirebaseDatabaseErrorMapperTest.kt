package com.example.guardband.data.repository

import com.google.firebase.database.DatabaseError
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FirebaseDatabaseErrorMapper.fromCode].
 *
 * The codes are the Realtime Database's own constants rather than literals, so
 * this stays a test of the mapping and not of numbers copied out of the SDK.
 */
class FirebaseDatabaseErrorMapperTest {

    @Test
    fun `permission denied maps to its own error`() {
        assertEquals(
            ContactError.PermissionDenied,
            FirebaseDatabaseErrorMapper.fromCode(DatabaseError.PERMISSION_DENIED)
        )
    }

    @Test
    fun `every form of not reaching the server maps to Network`() {
        listOf(
            DatabaseError.NETWORK_ERROR,
            DatabaseError.DISCONNECTED,
            DatabaseError.UNAVAILABLE
        ).forEach { code ->
            assertEquals(ContactError.Network, FirebaseDatabaseErrorMapper.fromCode(code))
        }
    }

    @Test
    fun `a dead token maps to NotSignedIn, not to PermissionDenied`() {
        // The rules evaluate auth != null, so this is "not you" rather than a
        // problem with the data.
        assertEquals(
            ContactError.NotSignedIn,
            FirebaseDatabaseErrorMapper.fromCode(DatabaseError.EXPIRED_TOKEN)
        )
        assertEquals(
            ContactError.NotSignedIn,
            FirebaseDatabaseErrorMapper.fromCode(DatabaseError.INVALID_TOKEN)
        )
    }

    @Test
    fun `an unrecognised code keeps the code without surfacing it`() {
        val mapped = FirebaseDatabaseErrorMapper.fromCode(DatabaseError.MAX_RETRIES)

        assertEquals(ContactError.Unknown(DatabaseError.MAX_RETRIES), mapped)
        // Nothing to leak into a Toast or a log.
        assertEquals(null, mapped.message)
    }

    @Test
    fun `a ContactError passes straight through rather than becoming Unknown`() {
        assertEquals(
            ContactError.MinimumContacts,
            FirebaseDatabaseErrorMapper.map(ContactError.MinimumContacts as Throwable)
        )
    }

    @Test
    fun `an unrelated throwable becomes Unknown with no code`() {
        assertEquals(
            ContactError.Unknown(null),
            FirebaseDatabaseErrorMapper.map(IllegalStateException("boom"))
        )
    }

    @Test
    fun `no ContactError carries a message or a stack trace`() {
        val errors = listOf(
            ContactError.NotSignedIn,
            ContactError.NotFound,
            ContactError.Validation,
            ContactError.MinimumContacts,
            ContactError.MaximumContacts,
            ContactError.PermissionDenied,
            ContactError.Network,
            ContactError.Unknown(-7)
        )

        errors.forEach { error ->
            assertEquals(null, error.message)
            assertEquals(0, error.stackTrace.size)
        }
    }

    // -- Alerts -------------------------------------------------------------

    @Test
    fun `permission denied maps to its own alert error`() {
        assertEquals(
            AlertError.PermissionDenied,
            FirebaseDatabaseErrorMapper.fromCodeToAlert(DatabaseError.PERMISSION_DENIED)
        )
    }

    @Test
    fun `every form of not reaching the server maps to an alert Network error`() {
        listOf(
            DatabaseError.NETWORK_ERROR,
            DatabaseError.DISCONNECTED,
            DatabaseError.UNAVAILABLE
        ).forEach { code ->
            assertEquals(AlertError.Network, FirebaseDatabaseErrorMapper.fromCodeToAlert(code))
        }
    }

    @Test
    fun `a dead token reads as not signed in for alerts too`() {
        listOf(
            DatabaseError.EXPIRED_TOKEN,
            DatabaseError.INVALID_TOKEN
        ).forEach { code ->
            assertEquals(AlertError.NotSignedIn, FirebaseDatabaseErrorMapper.fromCodeToAlert(code))
        }
    }

    @Test
    fun `an unrecognised code becomes an alert Unknown carrying it`() {
        assertEquals(
            AlertError.Unknown(-99),
            FirebaseDatabaseErrorMapper.fromCodeToAlert(-99)
        )
    }

    @Test
    fun `no AlertError carries a message or a stack trace`() {
        val errors = listOf(
            AlertError.Network,
            AlertError.PermissionDenied,
            AlertError.NotSignedIn,
            AlertError.ParseFailure,
            AlertError.Unknown(-7)
        )

        errors.forEach { error ->
            assertEquals(null, error.message)
            assertEquals(0, error.stackTrace.size)
        }
    }
}
