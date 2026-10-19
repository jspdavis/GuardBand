package com.example.guardband.data.repository

import com.example.guardband.data.model.AlertType
import com.example.guardband.data.model.CheckIn

/**
 * Picks the latest check-in out of a parsed history window. Pure, so it is
 * unit-testable without a database.
 *
 * **Not a second parser.** A check-in is a `CHECKIN` alert (SCHEMA.md), so the
 * wire map has already been read by [AlertParser], the one reader of that
 * payload; reading it twice would give two places that could disagree about
 * what a valid entry is. A malformed row has therefore already been skipped
 * and counted before it gets here, and cannot hide an older good one.
 *
 * The highest `sequenceId` wins, whatever order the window arrives in, and
 * only an exact `CHECKIN` counts: an unrecognised or differently-cased type is
 * not a check-in.
 */
internal object CheckInSelector {

    fun latestFrom(history: AlertHistory): CheckIn? =
        history.alerts
            .filter { it.alertType() == AlertType.CHECKIN }
            .maxByOrNull { it.sequenceId }
            ?.let { CheckIn(sequenceId = it.sequenceId, timestamp = it.timestamp) }
}
