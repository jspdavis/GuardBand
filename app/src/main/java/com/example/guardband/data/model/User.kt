package com.example.guardband.data.model

/**
 * A registered GuardBand user as seen by the UI layer.
 *
 * Has no password field: credentials never leave the data layer.
 *
 * @param id       Unique user identifier.
 * @param name     Full display name entered during sign-up.
 * @param email    Email address used for login/forgot-password.
 * @param photoUrl The session's profile photo link as Firebase Auth reports
 *   it, or null. Display only: it is never written to the database, and
 *   whether it is shown at all is the Track tab's `AvatarPhoto` decision.
 */
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null
)
