package com.example.guardband.ui.alert

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
}
