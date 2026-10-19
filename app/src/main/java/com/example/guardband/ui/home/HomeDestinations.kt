package com.example.guardband.ui.home

/**
 * The Home host's destinations, by Fragment tag, and how a saved tag is read
 * back after a restore. Pure, so the restore rule is a plain JVM test;
 * [HomeActivity] owns everything that touches a view or a menu id.
 *
 * Three tabs on the bottom bar - Track, Contacts, Profile - and three pushed
 * screens that hide the bar: Settings, Notifications and Alert history. The
 * Alert tab was removed on 2026-10-19; its screen is now [ALERT_HISTORY],
 * opened from Track's recent-alerts panel.
 */
internal object HomeDestinations {

    const val TRACK = "home_track"
    const val CONTACTS = "home_contacts"
    const val PROFILE = "home_profile"

    const val SETTINGS = "home_settings"
    const val NOTIFICATIONS = "home_notifications"
    const val ALERT_HISTORY = "home_alert_history"

    /** The bottom bar's destinations, in bar order. */
    val TABS: List<String> = listOf(TRACK, CONTACTS, PROFILE)

    /** Reached from a tab, never from the bar; Back returns to the last tab. */
    val PUSHED: Set<String> = setOf(SETTINGS, NOTIFICATIONS, ALERT_HISTORY)

    fun isTab(tag: String): Boolean = tag in TABS

    /**
     * The screen to show for a saved `currentTag`. Anything this build does
     * not host - the removed Alert tab's `home_alert`, or nothing saved at
     * all - is Track, never a crash.
     */
    fun restoreCurrent(saved: String?): String =
        saved?.takeIf { it in TABS || it in PUSHED } ?: TRACK

    /**
     * The tab Back returns to, from a saved `lastTabTag`. Only a tab will do,
     * so a pushed screen or the removed Alert tab is Track.
     */
    fun restoreLastTab(saved: String?): String =
        saved?.takeIf { it in TABS } ?: TRACK
}
