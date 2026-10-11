package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert

/**
 * Turns one Realtime Database node into an [Alert], or null when the node is
 * not one.
 *
 * Takes a plain [Map] rather than a `DataSnapshot` on purpose: that is the
 * whole of the parsing logic made unit-testable without a device, a live
 * database or a mocking library (the project declares none). The repository
 * hands it `snapshot.value`, which the SDK has already turned into maps,
 * numbers, strings and booleans.
 *
 * **What makes an entry malformed.** `sequenceId`, `type` and `timestamp` are
 * required: without a sequenceId the row cannot be keyed, ordered or diffed,
 * and without a type or a timestamp there is nothing meaningful to show. The
 * rest is salvaged - a missing `schemaVersion`, `deviceId`, `battery` or
 * `location` yields a default or null rather than dropping an otherwise
 * displayable alert. That is deliberately more forgiving than SCHEMA.md's
 * receiver-validation list, which also requires `schemaVersion`, `deviceId`
 * and `battery`: this is a *reader*, and refusing to show a panic alert
 * because its battery node was missing would be the wrong failure.
 *
 * An unrecognised `type` is **not** malformed. It is kept as the raw wire
 * string and [Alert.alertType] returns null for it, so a type added by a
 * future firmware still appears in the list instead of vanishing from it.
 *
 * `schemaVersion` is read with `toString()`, so both the string `"1.0"` and a
 * bare number `1` parse (D5).
 */
internal object AlertParser {

    /**
     * Parses a whole history window into newest-first order, skipping and
     * counting the entries [parse] rejects.
     *
     * Sorting here rather than trusting the query's order means the list is
     * right even if a key is not integer-like and RTDB therefore returned it
     * in string order (see [FirebaseAlertRepository]).
     *
     * @param rawEntries each child's `DataSnapshot.getValue()`, in any order.
     */
    fun parseHistory(rawEntries: List<Any?>): AlertHistory {
        val parsed = rawEntries.mapNotNull(::parse)
        return AlertHistory(
            alerts = parsed.sortedByDescending { it.sequenceId },
            rawCount = rawEntries.size,
            malformedCount = rawEntries.size - parsed.size
        )
    }

    /** @param raw a `DataSnapshot.getValue()`, normally a `Map<String, Any?>`. */
    fun parse(raw: Any?): Alert? {
        val map = raw as? Map<*, *> ?: return null

        val sequenceId = (map[FIELD_SEQUENCE_ID] as? Number)?.toLong() ?: return null
        val type = map[FIELD_TYPE].asNonBlankString() ?: return null
        val timestamp = map[FIELD_TIMESTAMP].asNonBlankString() ?: return null

        return Alert(
            schemaVersion = map[FIELD_SCHEMA_VERSION]?.toString().orEmpty(),
            deviceId = map[FIELD_DEVICE_ID].asNonBlankString().orEmpty(),
            type = type,
            timestamp = timestamp,
            location = parseLocation(map[FIELD_LOCATION]),
            battery = parseBattery(map[FIELD_BATTERY]),
            sequenceId = sequenceId
        )
    }

    /** Null unless both coordinates are present; `accuracyMeters` defaults to 0. */
    private fun parseLocation(raw: Any?): Alert.Location? {
        val map = raw as? Map<*, *> ?: return null
        val lat = (map["lat"] as? Number)?.toDouble() ?: return null
        val lng = (map["lng"] as? Number)?.toDouble() ?: return null
        return Alert.Location(
            lat = lat,
            lng = lng,
            accuracyMeters = (map["accuracyMeters"] as? Number)?.toDouble() ?: 0.0
        )
    }

    /** Null unless `percent` is present; `isCharging` defaults to false. */
    private fun parseBattery(raw: Any?): Alert.Battery? {
        val map = raw as? Map<*, *> ?: return null
        val percent = (map["percent"] as? Number)?.toInt() ?: return null
        return Alert.Battery(
            percent = percent,
            isCharging = map["isCharging"] as? Boolean ?: false
        )
    }

    private fun Any?.asNonBlankString(): String? = (this as? String)?.takeIf { it.isNotBlank() }

    private const val FIELD_SCHEMA_VERSION = "schemaVersion"
    private const val FIELD_DEVICE_ID = "deviceId"
    private const val FIELD_TYPE = "type"
    private const val FIELD_TIMESTAMP = "timestamp"
    private const val FIELD_LOCATION = "location"
    private const val FIELD_BATTERY = "battery"
    private const val FIELD_SEQUENCE_ID = "sequenceId"
}
