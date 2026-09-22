package com.example.guardband.data.model

/**
 * Alert data model matching the SCHEMA.md structure
 * Used for receiving alerts from Firebase Realtime Database
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
     * Helper to get alert type as enum
     */
    fun getAlertType(): AlertType {
        return when (type) {
            "PANIC" -> AlertType.PANIC
            "CHECKIN" -> AlertType.CHECKIN
            "LOW_BATTERY" -> AlertType.LOW_BATTERY
            "TRACKING_UPDATE" -> AlertType.TRACKING_UPDATE
            else -> AlertType.PANIC
        }
    }

    /**
     * Format timestamp for display (simplified)
     */
    fun getFormattedTimestamp(): String {
        // In production, use proper date formatting
        // For now, just return the ISO string
        return timestamp
    }
}

enum class AlertType {
    PANIC,
    CHECKIN,
    LOW_BATTERY,
    TRACKING_UPDATE
}
