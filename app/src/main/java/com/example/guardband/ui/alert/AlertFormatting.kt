package com.example.guardband.ui.alert

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.example.guardband.R
import com.example.guardband.data.model.AlertType
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Display helpers for alerts: type label and color, and the timestamp in the
 * phone's time zone. View-layer only (call from the main thread:
 * SimpleDateFormat is not thread-safe).
 */
internal object AlertFormatting {

    /**
     * Accepted wire formats: SCHEMA.md's example has no milliseconds, the
     * debug mock sender writes them. java.time would need API 26 (minSdk is 24).
     */
    private val isoParsers = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
    }

    /** e.g. "Oct 4, 4:45 PM" in local time; the raw value if it can't be parsed. */
    fun formatTimestamp(iso: String): String {
        for (parser in isoParsers) {
            try {
                val date = parser.parse(iso) ?: continue
                return SimpleDateFormat(DISPLAY_PATTERN, Locale.getDefault()).format(date)
            } catch (e: ParseException) {
                // Try the next format.
            }
        }
        return iso
    }

    @StringRes
    fun typeLabel(type: AlertType?): Int =
        when (type) {
            AlertType.PANIC -> R.string.label_alert_type_panic
            AlertType.CHECKIN -> R.string.label_alert_type_checkin
            AlertType.LOW_BATTERY -> R.string.label_alert_type_low_battery
            AlertType.TRACKING_UPDATE -> R.string.label_alert_type_tracking
            null -> R.string.label_alert_type_unknown
        }

    @ColorRes
    fun typeColor(type: AlertType?): Int =
        when (type) {
            AlertType.PANIC -> R.color.alert_panic
            AlertType.CHECKIN -> R.color.alert_checkin
            AlertType.LOW_BATTERY -> R.color.alert_low_battery
            AlertType.TRACKING_UPDATE -> R.color.alert_tracking
            null -> R.color.text_hint
        }

    private const val DISPLAY_PATTERN = "MMM d, h:mm a"
}
