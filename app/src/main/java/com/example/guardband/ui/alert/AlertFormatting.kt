package com.example.guardband.ui.alert

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.example.guardband.R
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.ui.track.Elapsed
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Display helpers for alerts: type label, colour and symbol, the battery's
 * symbol, and the times and day headings in the phone's time zone. View-layer only (call from the main thread:
 * SimpleDateFormat is not thread-safe).
 */
internal object AlertFormatting {

    /**
     * Accepted wire formats: SCHEMA.md's example has no milliseconds, the
     * debug mock sender writes them. java.time would need API 26 (minSdk is 24).
     *
     * The one copy in the app: [BandStatus][com.example.guardband.ui.track.BandStatus]
     * reads the same two through [newIsoUtcParser].
     */
    val ISO_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    /**
     * A strict UTC parser for one of [ISO_PATTERNS]. A new instance on every
     * call, so a caller off the main thread can own its parser outright.
     */
    fun newIsoUtcParser(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

    /** Built once and reused: this object is only called from the main thread. */
    private val isoParsers = ISO_PATTERNS.map(::newIsoUtcParser)

    /** e.g. "Oct 4, 4:45 PM" in local time; the raw value if it can't be parsed. */
    fun formatTimestamp(iso: String): String = format(iso, DISPLAY_PATTERN)

    /** e.g. "4:45 PM" in local time, for a row under its day's heading; the raw value if it can't be parsed. */
    fun clockTime(iso: String): String = format(iso, CLOCK_PATTERN)

    private fun format(iso: String, pattern: String): String {
        for (parser in isoParsers) {
            try {
                val date = parser.parse(iso) ?: continue
                return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
            } catch (e: ParseException) {
                // Try the next format.
            }
        }
        return iso
    }

    /** A card's time, in [style]. */
    fun timeText(context: Context, row: AlertRow, style: AlertTimeStyle): String =
        when (style) {
            AlertTimeStyle.RELATIVE -> relativeTime(context, row)
            AlertTimeStyle.CLOCK -> clockTime(row.alert.timestamp)
        }

    /**
     * "Just now", "5 min ago", "3 hr ago"; from a day on, the date and time,
     * since "4 days ago" makes the reader work out which day. An unreadable
     * timestamp shows raw, as [formatTimestamp] does.
     */
    fun relativeTime(context: Context, row: AlertRow): String =
        when (val elapsed = row.elapsed) {
            is Elapsed.Seconds -> context.getString(R.string.label_alert_just_now)
            is Elapsed.Minutes -> quantity(context, R.plurals.label_alert_minutes_ago, elapsed.value)
            is Elapsed.Hours -> quantity(context, R.plurals.label_alert_hours_ago, elapsed.value)
            is Elapsed.Days, null -> formatTimestamp(row.alert.timestamp)
        }

    private fun quantity(context: Context, @PluralsRes resId: Int, value: Long): String =
        context.resources.getQuantityString(resId, value.toInt(), value.toInt())

    /** A day's heading: "Today", "Yesterday", "Sat, Oct 18", or "Oct 18, 2025" in another year. */
    fun dayLabel(context: Context, day: AlertDay): String =
        when (day) {
            AlertDay.Today -> context.getString(R.string.label_alert_day_today)
            AlertDay.Yesterday -> context.getString(R.string.label_alert_day_yesterday)
            AlertDay.Unknown -> context.getString(R.string.label_alert_day_unknown)
            is AlertDay.On -> SimpleDateFormat(
                if (day.sameYear) DAY_PATTERN else DAY_WITH_YEAR_PATTERN,
                Locale.getDefault()
            ).format(Date(day.startMillis))
        }

    /**
     * FR-11's low-battery level. At or below it the battery shows nearly
     * empty and orange, whatever the alert's type.
     */
    const val LOW_BATTERY_PERCENT = 20

    /** The battery's symbol: a bolt while charging, else a third, two thirds or full, or nearly empty at [LOW_BATTERY_PERCENT]. */
    @DrawableRes
    fun batteryIcon(battery: Alert.Battery): Int =
        when {
            battery.isCharging -> R.drawable.ic_battery_charging
            battery.percent <= LOW_BATTERY_PERCENT -> R.drawable.ic_battery_critical
            battery.percent <= 50 -> R.drawable.ic_battery_low
            battery.percent <= 80 -> R.drawable.ic_battery_mid
            else -> R.drawable.ic_battery_full
        }

    /** Orange when low and not charging, green while charging, grey otherwise. DESIGN: Jul */
    @ColorRes
    fun batteryColor(battery: Alert.Battery): Int =
        when {
            battery.isCharging -> R.color.success_green
            battery.percent <= LOW_BATTERY_PERCENT -> R.color.alert_low_battery
            else -> R.color.text_secondary
        }

    /**
     * An alert's location line: its place name once known
     * ([AlertUiState.placeNames]), else its coordinates, which is also what
     * shows while the name loads and when none can be found.
     */
    fun locationText(context: Context, location: Alert.Location, placeName: String?): String =
        placeName ?: context.getString(R.string.label_alert_location, location.lat, location.lng)

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

    /**
     * Each type's symbol, tinted with [typeColor]. The shapes differ as well
     * as the colours (octagon, triangle, circle with a tick, circle with a
     * question mark), so the types stay apart for someone who cannot tell
     * red from orange or green, and in bright sun. Decorative for TalkBack:
     * the label beside it already says the type.
     */
    @DrawableRes
    fun typeIcon(type: AlertType?): Int =
        when (type) {
            AlertType.PANIC -> R.drawable.ic_alert_panic
            AlertType.CHECKIN -> R.drawable.ic_check_circle
            AlertType.LOW_BATTERY -> R.drawable.ic_warning
            AlertType.TRACKING_UPDATE -> R.drawable.ic_info
            null -> R.drawable.ic_alert_unknown
        }

    private const val DISPLAY_PATTERN = "MMM d, h:mm a"
    private const val CLOCK_PATTERN = "h:mm a"
    private const val DAY_PATTERN = "EEE, MMM d"
    private const val DAY_WITH_YEAR_PATTERN = "MMM d, yyyy"
}
