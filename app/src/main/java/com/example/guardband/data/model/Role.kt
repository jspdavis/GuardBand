package com.example.guardband.data.model

/**
 * A user's role in a GuardBand pairing: the [DEPENDENT] wears the band and
 * originates alerts; the [GUARDIAN] monitors them.
 *
 * Stored at `users/{uid}/role` as the enum name (`GUARDIAN` / `DEPENDENT`),
 * written in the same atomic commit as the rest of the profile at sign-up and
 * read back to decide which dashboard to show. Like [SignInProvider], the
 * wire value is the enum name; [fromWire] tolerates an unknown value (returns
 * null) so a future role does not break an older build reading the profile.
 */
enum class Role {

    /** Wears the band; their phone sends the panic SMS to their guardian. */
    DEPENDENT,

    /** Watches the dependent; receives the panic SMS and sees the live map. */
    GUARDIAN;

    companion object {
        fun fromWire(value: String?): Role? = entries.find { it.name == value }
    }
}
