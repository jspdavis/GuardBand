package com.example.guardband.ui.home

/**
 * Where [HomeViewModel]'s account checks stand. The tabs are shown only at
 * [OPEN]; until then [HomeActivity] covers them.
 *
 * Leaving for another screen (Login, the verification gate, a pending
 * deletion, the consent screen) is an event, not a state, so the cover stays
 * at [CHECKING] while that navigation happens and nothing flashes.
 */
enum class HomeGateState {

    /** Reading the account's record. The loading cover is up. */
    CHECKING,

    /**
     * The record could not be read: offline, refused or too slow. The retry
     * cover is up. **Never** treated as "no consent" and never as "let in".
     */
    FAILED,

    /** Every check passed. The tabs are on screen. */
    OPEN
}
