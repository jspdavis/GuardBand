package com.example.guardband.ui.track

/**
 * What the Check-in pill says about the wearer's latest "I'm safe"
 * double-press (FR-10). Read-only: the band sends check-ins, the app only
 * shows them.
 *
 * "None" means none in the history window the app reads, not never - see
 * [AlertRepository.observeLatestCheckIn][com.example.guardband.data.repository.AlertRepository.observeLatestCheckIn] -
 * which is why the pill words it as "no recent check-ins".
 */
sealed interface CheckInStatus {

    /** Until the first read of the history window completes. */
    object Loading : CheckInStatus

    /** The window holds no check-in. */
    object None : CheckInStatus

    /** The first read failed, so there is nothing to show yet. */
    object Unavailable : CheckInStatus

    /**
     * A check-in was received.
     *
     * @param elapsed how long ago, recomputed on the staleness ticker; null
     *   when its timestamp could not be read, so the screen can say "checked
     *   in" without inventing a time.
     */
    data class Received(val elapsed: Elapsed?) : CheckInStatus
}
