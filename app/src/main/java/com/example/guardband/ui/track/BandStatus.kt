package com.example.guardband.ui.track

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Wall clock, injected so the staleness rules are deterministic in tests.
 *
 * `java.time.Clock` would be the obvious choice but needs API 26, and minSdk
 * is 24.
 */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        /** The real clock; the default everywhere outside tests. */
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

/**
 * How long ago the band last reported, in the largest unit that still reads
 * naturally.
 *
 * A structured value rather than a formatted string, for two reasons: the
 * wording belongs in `strings.xml` (and a `plurals`, since "1 minute" and
 * "2 minutes" differ), and a ViewModel holds no Context to resolve one with.
 * The Fragment turns this into text.
 */
sealed interface Elapsed {
    data class Seconds(val value: Long) : Elapsed
    data class Minutes(val value: Long) : Elapsed
    data class Hours(val value: Long) : Elapsed
    data class Days(val value: Long) : Elapsed
}

/**
 * Whether the band is still reporting, and how long ago it last did.
 *
 * Pure, with no Android imports, so it runs in a plain JVM test without
 * Robolectric and without a live database.
 */
internal object BandStatus {

    /**
     * How long after its last report the band counts as offline.
     *
     * Three times FR-09's 60 s tracking interval, so a single dropped update
     * does not flip the badge. Note what this can and cannot mean: FR-09
     * tracking is *post-alert* and stops after 30 minutes, so an idle band
     * legitimately sends nothing and will read as stale. The badge therefore
     * says "the band is reporting right now", not "the band is alive" - which
     * is why the screen leads with how long ago it last reported.
     */
    const val ONLINE_THRESHOLD_MS = 3 * 60 * 1000L

    /**
     * Accepted wire formats, matching
     * [AlertFormatting][com.example.guardband.ui.alert.AlertFormatting]:
     * SCHEMA.md's example carries no milliseconds, the debug mock sender writes
     * them. Both are valid ISO 8601.
     *
     * Duplicated from the Alert tab deliberately for now - sharing it would
     * mean editing that tab, which this pass is scoped out of. It is the same
     * two patterns, and folding them into one helper is a one-line change
     * whenever the Alert tab is next open.
     *
     * A new parser per call: SimpleDateFormat is not thread-safe, and this is
     * reached from a ViewModel rather than only the main thread.
     */
    private val ISO_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    /** Epoch millis for an ISO 8601 UTC timestamp, or null if it cannot be read. */
    fun parseIsoUtcMillis(iso: String): Long? {
        for (pattern in ISO_PATTERNS) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                return parser.parse(iso)?.time ?: continue
            } catch (e: ParseException) {
                // Try the next format.
            }
        }
        return null
    }

    /**
     * True while the last report is still inside [thresholdMs].
     *
     * A timestamp in the *future* counts as online. The band's clock can run
     * ahead of the phone's, and treating that as "offline" would be a worse
     * answer than treating it as "just reported".
     */
    fun isOnline(
        timestampMillis: Long,
        nowMillis: Long,
        thresholdMs: Long = ONLINE_THRESHOLD_MS
    ): Boolean = nowMillis - timestampMillis < thresholdMs

    /**
     * Time since the last report, in the largest whole unit that fits:
     * seconds below a minute, minutes below an hour, hours below a day, then
     * days. A future timestamp clamps to zero seconds for the reason above.
     */
    fun elapsedSince(timestampMillis: Long, nowMillis: Long): Elapsed {
        val seconds = ((nowMillis - timestampMillis) / 1000L).coerceAtLeast(0)
        if (seconds < SECONDS_PER_MINUTE) return Elapsed.Seconds(seconds)

        val minutes = seconds / SECONDS_PER_MINUTE
        if (minutes < MINUTES_PER_HOUR) return Elapsed.Minutes(minutes)

        val hours = minutes / MINUTES_PER_HOUR
        if (hours < HOURS_PER_DAY) return Elapsed.Hours(hours)

        return Elapsed.Days(hours / HOURS_PER_DAY)
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val HOURS_PER_DAY = 24L
}
