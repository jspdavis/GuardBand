package com.example.guardband.data.model

/**
 * The resolution state of an alert on the history timeline.
 *
 * The band never writes a status: every band-originated alert starts
 * [ACTIVE] and stays so until a guardian acts. [ACKNOWLEDGED] and [RESOLVED]
 * are written by the guardian's app to `devices/{deviceId}/status/{seq}` and
 * merged onto the row at read time, so the band's append-only `history` is
 * never mutated and the status survives reloads.
 *
 * [fromWire] tolerates an unknown/absent value: existing history with no stored
 * status defaults to [ACTIVE], so every row still renders.
 */
enum class AlertStatus {

    /** No action taken yet. The default for any alert without a stored status. */
    ACTIVE,

    /** A guardian has seen it; the emergency is being handled. */
    ACKNOWLEDGED,

    /** Closed out; the emergency is over. */
    RESOLVED;

    companion object {
        /** The wire value is the enum name; an unknown or null value is [ACTIVE]. */
        fun fromWire(value: String?): AlertStatus = entries.find { it.name == value } ?: ACTIVE
    }
}
