package com.example.guardband.ui.profile

/** Render state for [ProfileFragment]. */
data class ProfileUiState(
    /**
     * True until the `users/{uid}` read lands.
     *
     * The other fields are already populated from the session while this is
     * true, so the screen is never blank; the read only replaces them with the
     * stored record, which is authoritative.
     */
    val isLoading: Boolean = false,

    /** Welcome-header name (already falls back to "User"). */
    val userName: String = "",
    val email: String = "",

    /**
     * The Google profile photo to load into the avatar, already vetted by
     * [AvatarPhoto][com.example.guardband.ui.avatar.AvatarPhoto]. Null keeps
     * the default icon and makes no request. From the session only: the
     * `users/{uid}` record holds no photo.
     */
    val avatarUrl: String? = null
)
