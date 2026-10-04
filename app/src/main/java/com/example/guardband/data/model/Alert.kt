package com.example.guardband.data.model

/**
 * One alert written by the band (or the mock sender), as defined in SCHEMA.md
 * (v1.0). Stored at /devices/{deviceId}/latest and
 * /devices/{deviceId}/history/{sequenceId}.
 *
 * Field names match the wire format exactly; every field has a default so a
 * future Firebase reader can deserialize it. Display formatting (timestamp,
 * labels, colors) lives in the UI layer, not here.
 *
 * @param schemaVersion Always "1.0" for now (a string, per SCHEMA.md).
 * @param type          Raw wire value; see [alertType] for the typed form.
 * @param timestamp     ISO 8601 UTC, e.g. "2026-09-09T00:00:00Z".
 * @param sequenceId    Incrementing integer per device; also the history key.
 */
data class Alert(
    val schemaVersion: String = "",
    val deviceId: String = "",
    val type: String = "",
    val timestamp: String = "",
    val location: Location? = null,
    val battery: Battery? = null,
    val sequenceId: Long = 0
) {
    data class Location(
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val accuracyMeters: Double = 0.0
    )

    data class Battery(
        val percent: Int = 0,
        val isCharging: Boolean = false
    )

    /**
     * [type] as an [AlertType], or null for an unknown value.
     * A function rather than a property, so a Firebase mapper won't treat it as a field.
     */
    fun alertType(): AlertType? = AlertType.fromWire(type)
}

/** The four alert types allowed by SCHEMA.md. */
enum class AlertType {
    PANIC,
    CHECKIN,
    LOW_BATTERY,
    TRACKING_UPDATE;

    companion object {
        fun fromWire(value: String): AlertType? = entries.find { it.name == value }
    }
}
