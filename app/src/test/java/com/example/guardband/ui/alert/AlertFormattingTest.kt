package com.example.guardband.ui.alert

import com.example.guardband.R
import com.example.guardband.data.model.AlertType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * Pins [AlertFormatting.formatTimestamp]: which wire formats it reads, that it
 * reads them as UTC and shows them in the phone's zone, and that anything else
 * comes back as the raw value.
 *
 * The display side follows the default zone and locale, so each test fixes
 * both and puts them back afterwards.
 */
class AlertFormattingTest {

    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale

    @Before
    fun fixZoneAndLocale() {
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreZoneAndLocale() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    /** Newer JDKs put a narrow no-break space before AM/PM; compare words only. */
    private fun format(iso: String) =
        AlertFormatting.formatTimestamp(iso).replace(' ', ' ')

    @Test
    fun `it reads SCHEMA-md's format, without milliseconds`() {
        assertEquals("Oct 4, 8:45 AM", format("2026-10-04T08:45:00Z"))
    }

    @Test
    fun `it reads the mock sender's format, with milliseconds`() {
        assertEquals("Oct 4, 8:45 AM", format("2026-10-04T08:45:00.123Z"))
    }

    @Test
    fun `it reads UTC and shows the phone's zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))

        assertEquals("Oct 4, 4:45 PM", format("2026-10-04T08:45:00Z"))
    }

    @Test
    fun `an unreadable timestamp comes back as the raw value`() {
        assertEquals("", format(""))
        assertEquals("not a timestamp", format("not a timestamp"))
        assertEquals("2026-10-04 08:45:00", format("2026-10-04 08:45:00"))
    }

    @Test
    fun `an out of range date is shown raw rather than rolled over`() {
        // isLenient = false, so month 13 is not quietly January of next year.
        assertEquals("2026-13-04T08:45:00Z", format("2026-13-04T08:45:00Z"))
    }

    // ── Type symbols ──────────────────────────────────────────────────────────

    @Test
    fun `panic is the red alert octagon, low battery the orange warning triangle`() {
        assertEquals(R.drawable.ic_alert_panic, AlertFormatting.typeIcon(AlertType.PANIC))
        assertEquals(R.color.alert_panic, AlertFormatting.typeColor(AlertType.PANIC))
        assertEquals(R.drawable.ic_warning, AlertFormatting.typeIcon(AlertType.LOW_BATTERY))
        assertEquals(R.color.alert_low_battery, AlertFormatting.typeColor(AlertType.LOW_BATTERY))
    }

    @Test
    fun `a check-in is the green tick, as on the Check-in pill`() {
        assertEquals(R.drawable.ic_check_circle, AlertFormatting.typeIcon(AlertType.CHECKIN))
        assertEquals(R.color.alert_checkin, AlertFormatting.typeColor(AlertType.CHECKIN))
    }

    @Test
    fun `an unrecognised type gets its own question-mark symbol, not another type's`() {
        assertEquals(R.drawable.ic_alert_unknown, AlertFormatting.typeIcon(null))
    }

    @Test
    fun `every shown type has a different symbol`() {
        val shown = listOf(AlertType.PANIC, AlertType.CHECKIN, AlertType.LOW_BATTERY, null)

        assertEquals(shown.size, shown.map(AlertFormatting::typeIcon).toSet().size)
    }
}
