package com.example.guardband.ui.account

import com.example.guardband.data.model.SignInProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which face Account information's Password section shows. */
class PasswordOptionTest {

    @Test
    fun `a password account can change its password`() {
        assertEquals(PasswordOption.CHANGE, PasswordOption.of(setOf(SignInProvider.PASSWORD)))
    }

    @Test
    fun `a google account gets the empty state`() {
        assertEquals(
            PasswordOption.MANAGED_BY_GOOGLE,
            PasswordOption.of(setOf(SignInProvider.GOOGLE))
        )
    }

    /** The password is still there to change, as Delete account's tie-break says. */
    @Test
    fun `a mixed account can change its password`() {
        assertEquals(
            PasswordOption.CHANGE,
            PasswordOption.of(setOf(SignInProvider.GOOGLE, SignInProvider.PASSWORD))
        )
    }

    @Test
    fun `an account with neither has no section`() {
        assertEquals(PasswordOption.NONE, PasswordOption.of(emptySet()))
    }
}
