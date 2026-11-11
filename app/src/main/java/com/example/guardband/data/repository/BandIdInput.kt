package com.example.guardband.data.repository

/**
 * Reads a band id out of what the user gave the app. **The one place that
 * decides what a band id looks like**, pure so it is a plain JVM test.
 *
 * Today the input is typed (or pasted): the id the mock sender shows, or the
 * one the firmware's `STATUS` prints. A band id is a Firebase anonymous uid
 * (28 letters and digits) or the shared `guardband-001`. Later the same text
 * may come from scanning a QR code the band shows; that payload is parsed
 * here too, so a scanner is a new way in, not a second parser.
 *
 * Case is kept: Firebase uids are case-sensitive.
 */
object BandIdInput {

    /** Letters, digits, `-` and `_`: safe as an RTDB key, and every id a band has. */
    private val BAND_ID = Regex("^[A-Za-z0-9_-]{4,64}$")

    /** The band id in [text], or null when it cannot be one. */
    fun deviceIdOf(text: String): String? =
        text.trim().takeIf { BAND_ID.matches(it) }
}
