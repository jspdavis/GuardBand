package com.example.guardband.data.model

/**
 * The wearer's most recent "I'm safe" double-press (FR-10), as the Track tab
 * shows it.
 *
 * Not a payload of its own: SCHEMA.md defines a check-in as an [Alert] whose
 * `type` is `CHECKIN`, and this is the part of one that the check-in status
 * needs. It is derived, read-only, from alerts the band wrote; the app never
 * creates one.
 *
 * @param sequenceId the band's sequence number for it, which orders check-ins.
 * @param timestamp  ISO 8601 UTC, exactly as the band sent it.
 */
data class CheckIn(
    val sequenceId: Long,
    val timestamp: String
)
