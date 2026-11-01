package com.example.guardband.ui.home

import com.example.guardband.data.model.Role

/**
 * The Home host's destinations, by Fragment tag, and how a saved tag is read
 * back after a restore. Pure, so the restore rule is a plain JVM test;
 * [HomeActivity] owns everything that touches a view or a menu id.
 */
internal object HomeDestinations {

    const val TRACK = "home_track"
    const val CONTACTS = "home_contacts"
    /** The band and the account behind it. */
    const val DEVICE = "home_device"

    /** Guardian's Dependent status tab. */
    const val DEPENDENT = "home_dependent"
    /** Dependent's assigned Guardian info tab. */
    const val GUARDIAN = "home_guardian"

    const val SETTINGS = "home_settings"
    const val NOTIFICATIONS = "home_notifications"
    const val ALERT_HISTORY = "home_alert_history"

    /** One panic's Incident view (2026-10-27). */
    const val INCIDENT = "home_incident"

    /** The default bottom bar destinations. */
    val TABS: List<String> = listOf(TRACK, CONTACTS, DEVICE)

    val GUARDIAN_TABS: List<String> = listOf(TRACK, DEPENDENT, CONTACTS)
    val DEPENDENT_TABS: List<String> = listOf(DEVICE, GUARDIAN, CONTACTS)

    fun tabsFor(role: Role?): List<String> = when (role) {
        Role.DEPENDENT -> DEPENDENT_TABS
        Role.GUARDIAN -> GUARDIAN_TABS
        null -> TABS
    }

    val ALL_TABS: Set<String> = (TABS + GUARDIAN_TABS + DEPENDENT_TABS).toSet()

    /**
     * Reached from a tab, never from the bar; Back returns to the last tab,
     * except from [INCIDENT], which returns to whatever opened it.
     */
    val PUSHED: Set<String> = setOf(SETTINGS, NOTIFICATIONS, ALERT_HISTORY, INCIDENT)

    /** Where an Incident view can be opened from, and so where its Back returns. */
    private val INCIDENT_OPENERS: Set<String> = ALL_TABS + ALERT_HISTORY

    fun isTab(tag: String): Boolean = tag in ALL_TABS

    /**
     * The screen to show for a saved `currentTag`.
     */
    fun restoreCurrent(saved: String?): String =
        saved?.takeIf { it in ALL_TABS || it in PUSHED } ?: TRACK

    /**
     * The tab Back returns to, from a saved `lastTabTag`.
     */
    fun restoreLastTab(saved: String?): String =
        saved?.takeIf { it in ALL_TABS } ?: TRACK

    /**
     * The screen Back from an Incident view returns to: [current] when it is
     * one that opens incidents (a tab or Alert history), else Track.
     */
    fun restoreIncidentReturn(current: String?): String =
        current?.takeIf { it in INCIDENT_OPENERS } ?: TRACK
}
