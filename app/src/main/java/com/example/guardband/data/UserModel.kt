package com.example.guardband.data

/**
 * Represents a registered GuardBand user.
 *
 * @param id       Unique user identifier (mock UUID for now).
 * @param name     Full display name entered during sign-up.
 * @param email    Email address used for login/forgot-password.
 * @param location City or region the user set during sign-up.
 * @param password Plain-text placeholder — replace with hashed credential when backend is wired.
 */
data class UserModel(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val location: String = "",
    val password: String = ""
)
