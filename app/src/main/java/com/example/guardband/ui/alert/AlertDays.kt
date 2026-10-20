package com.example.guardband.ui.alert

import com.example.guardband.ui.track.BandStatus
import java.util.Calendar
import java.util.TimeZone

/**
 * The day an alert falls on, as a heading in the Alert history list. A
 * structured value, worded by [AlertFormatting.dayLabel], because the words
 * belong in `strings.xml`.
 */
sealed interface AlertDay {
    object Today : AlertDay
    object Yesterday : AlertDay

    /**
     * An earlier day. [startMillis] is its midnight in the phone's zone;
     * [sameYear] is whether it is this year, which drops the year from the label.
     */
    data class On(val startMillis: Long, val sameYear: Boolean) : AlertDay

    /** The alert's timestamp could not be read. */
    object Unknown : AlertDay
}

/** One item of the Alert history list: a day's heading, or an alert row. */
sealed interface AlertListItem {
    data class Header(val day: AlertDay) : AlertListItem
    data class Row(val row: AlertRow) : AlertListItem
}

/**
 * Splits the Alert history rows under day headings. Pure, with the zone
 * passed in, so it is a plain JVM test.
 */
internal object AlertDays {

    /**
     * [rows] in their order (newest first), with a heading wherever the day
     * changes. Headings follow the rows rather than sorting them: the list is
     * ordered by sequenceId, so a band whose clock jumped can show a day
     * twice, and that is the honest picture.
     */
    fun group(rows: List<AlertRow>, nowMillis: Long, zone: TimeZone): List<AlertListItem> {
        val items = mutableListOf<AlertListItem>()
        var current: AlertDay? = null
        for (row in rows) {
            val day = dayOf(row.alert.timestamp, nowMillis, zone)
            if (day != current) {
                items += AlertListItem.Header(day)
                current = day
            }
            items += AlertListItem.Row(row)
        }
        return items
    }

    /**
     * Which day [iso] falls on, seen from [nowMillis] in [zone]. A timestamp
     * after today counts as today: the band's clock can run ahead of the
     * phone's, as [BandStatus.isOnline] allows for too.
     */
    fun dayOf(iso: String, nowMillis: Long, zone: TimeZone): AlertDay {
        val millis = BandStatus.parseIsoUtcMillis(iso) ?: return AlertDay.Unknown
        val today = startOfDay(nowMillis, zone)
        val day = startOfDay(millis, zone)
        return when {
            day >= today -> AlertDay.Today
            // The millisecond before midnight is yesterday, so this holds
            // across a daylight-saving change too.
            day == startOfDay(today - 1, zone) -> AlertDay.Yesterday
            else -> AlertDay.On(day, yearOf(day, zone) == yearOf(nowMillis, zone))
        }
    }

    private fun startOfDay(millis: Long, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun yearOf(millis: Long, zone: TimeZone): Int =
        Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.YEAR)
}
