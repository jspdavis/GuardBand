package com.example.guardband.ui.signup

import com.example.guardband.data.model.EmergencyContact

/**
 * Render state for [SignUpContactsActivity].
 *
 * Nothing here has been written yet. The contacts are **staged in memory**
 * until the Consent screen submits them, which is what lets the whole sign-up
 * commit in one atomic write.
 */
data class SignUpContactsUiState(
    /** Staged contacts, in the order they were added. */
    val contacts: List<EmergencyContact> = emptyList(),

    /**
     * True when a Google sign-in already created the account.
     *
     * Carried through to Consent, which then finishes the profile instead of
     * creating anything.
     */
    val completeProfile: Boolean = false,

    /** Continue is enabled once at least [SignUpContactsViewModel.MIN_AT_SIGNUP] is staged. */
    val canContinue: Boolean = false,

    /**
     * True while the user is below the recommended
     * [MIN_CONTACTS][com.example.guardband.utils.InputValidator.MIN_CONTACTS],
     * which shows the "n of 3 minimum" banner.
     *
     * A nudge, not a gate: sign-up only requires one. The band can be in use
     * with fewer than three, and refusing to finish would be worse than
     * starting with one.
     */
    val showMinimumNotice: Boolean = true
)
