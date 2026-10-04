package com.example.guardband.data.model

/**
 * A registered GuardBand user as seen by the UI layer.
 *
 * Has no password field: credentials never leave the data layer.
 *
 * @param id       Unique user identifier.
 * @param name     Full display name entered during sign-up.
 * @param email    Email address used for login/forgot-password.
 * @param location City or region the user set during sign-up.
 */
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val location: String = ""
)
