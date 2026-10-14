package com.example.guardband.data.model

/**
 * What is stored at `users/{uid}`, as far as signing in is concerned.
 *
 * Three answers rather than "exists or not", because a deletion that removed
 * the data but not the sign-in leaves a **marker** behind, and that must never
 * be mistaken for either a live profile or an empty slot. See
 * [UserProfileRepository.markDeletionPending][com.example.guardband.data.repository.UserProfileRepository.markDeletionPending].
 */
enum class ProfileState {

    /** Nothing stored. A sign-in may create a minimal record. */
    ABSENT,

    /** A real profile. Left alone. */
    PRESENT,

    /**
     * The user deleted their account and the data is gone, but the sign-in
     * survived. Never re-provision; finish the deletion instead.
     */
    DELETION_PENDING
}
