package com.example.guardband.ui.alert

import android.content.Context
import com.example.guardband.R
import com.example.guardband.data.model.Alert
import com.example.guardband.testing.FakeAlertRepository.Companion.alert
import com.example.guardband.ui.track.BandStatus
import com.example.guardband.ui.track.Elapsed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import java.util.TimeZone

/**
 * What an alert card says: its time in both styles, the day headings, and
 * the battery's symbol and colour. Robolectric for the strings and plurals;
 * the zone and locale are fixed and put back.
 */
@RunWith(RobolectricTestRunner::class)
class AlertCardTextTest {

    private lateinit var context: Context
    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    /** Newer JDKs put a narrow no-break space before AM/PM; compare words only. */
    private fun String.words() = replace(' ', ' ')

    private fun relative(elapsed: Elapsed?, timestamp: String = "2026-10-04T08:45:00Z") =
        AlertFormatting.timeText(context, AlertRow(alert(1, "PANIC", timestamp = timestamp), elapsed = elapsed), AlertTimeStyle.RELATIVE).words()

    // ── Times ─────────────────────────────────────────────────────────────────

    @Test
    fun `under a minute is just now`() {
        assertEquals("Just now", relative(Elapsed.Seconds(40)))
    }

    @Test
    fun `minutes and hours are short and relative`() {
        assertEquals("1 min ago", relative(Elapsed.Minutes(1)))
        assertEquals("45 min ago", relative(Elapsed.Minutes(45)))
        assertEquals("3 hr ago", relative(Elapsed.Hours(3)))
    }

    @Test
    fun `from a day on it is the date and time`() {
        assertEquals("Oct 4, 8:45 AM", relative(Elapsed.Days(2)))
    }

    @Test
    fun `an unreadable time shows raw`() {
        assertEquals("soon", relative(null, timestamp = "soon"))
    }

    @Test
    fun `under a day's heading a row shows the clock time only`() {
        val row = AlertRow(alert(1, "PANIC"), elapsed = Elapsed.Minutes(5))

        assertEquals("8:45 AM", AlertFormatting.timeText(context, row, AlertTimeStyle.CLOCK).words())
    }

    // ── Day headings ──────────────────────────────────────────────────────────

    @Test
    fun `headings say today, yesterday, a weekday and date, or the year too`() {
        val oct18 = checkNotNull(BandStatus.parseIsoUtcMillis("2026-10-18T00:00:00Z"))
        val dec31 = checkNotNull(BandStatus.parseIsoUtcMillis("2025-12-31T00:00:00Z"))

        assertEquals("Today", AlertFormatting.dayLabel(context, AlertDay.Today))
        assertEquals("Yesterday", AlertFormatting.dayLabel(context, AlertDay.Yesterday))
        assertEquals("Sun, Oct 18", AlertFormatting.dayLabel(context, AlertDay.On(oct18, sameYear = true)))
        assertEquals("Dec 31, 2025", AlertFormatting.dayLabel(context, AlertDay.On(dec31, sameYear = false)))
        assertEquals("Date unknown", AlertFormatting.dayLabel(context, AlertDay.Unknown))
    }

    // ── Battery ───────────────────────────────────────────────────────────────

    private fun icon(percent: Int, charging: Boolean = false) =
        AlertFormatting.batteryIcon(Alert.Battery(percent, charging))

    private fun colour(percent: Int, charging: Boolean = false) =
        AlertFormatting.batteryColor(Alert.Battery(percent, charging))

    @Test
    fun `the battery's symbol follows its level`() {
        assertEquals(R.drawable.ic_battery_full, icon(100))
        assertEquals(R.drawable.ic_battery_full, icon(81))
        assertEquals(R.drawable.ic_battery_mid, icon(80))
        assertEquals(R.drawable.ic_battery_mid, icon(51))
        assertEquals(R.drawable.ic_battery_low, icon(50))
        assertEquals(R.drawable.ic_battery_low, icon(21))
        assertEquals(R.drawable.ic_battery_critical, icon(20))
        assertEquals(R.drawable.ic_battery_critical, icon(0))
    }

    @Test
    fun `at FR-11's 20 percent it turns orange, unless it is charging`() {
        assertEquals(R.color.text_secondary, colour(21))
        assertEquals(R.color.alert_low_battery, colour(AlertFormatting.LOW_BATTERY_PERCENT))
        assertEquals(R.color.success_green, colour(10, charging = true))
    }

    @Test
    fun `charging shows the bolt at any level`() {
        assertEquals(R.drawable.ic_battery_charging, icon(5, charging = true))
        assertEquals(R.drawable.ic_battery_charging, icon(100, charging = true))
    }
}
