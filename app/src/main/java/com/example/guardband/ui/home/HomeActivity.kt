package com.example.guardband.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.alert.AlertFragment
import com.example.guardband.ui.contacts.ContactsFragment
import com.example.guardband.ui.login.LoginActivity
import com.example.guardband.ui.notifications.NotificationsFragment
import com.example.guardband.ui.profile.ProfileFragment
import com.example.guardband.ui.settings.SettingsFragment
import com.example.guardband.ui.track.TrackFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * Post-auth host: four tab Fragments (Track, Contacts, Alert, Profile) under a
 * BottomNavigationView, plus two pushed Fragments (Settings, Notifications)
 * opened from Track's top bar. No Navigation Component.
 *
 * Fragments are added once and then shown/hidden, so every tab keeps its
 * state. A pushed screen keeps the bar visible with no tab selected; Back (or
 * its back arrow) returns to the last tab. Back on a tab leaves the app.
 *
 * Flow: Splash / Loading → HomeActivity → (Log Out in Settings, or no session) → LoginActivity
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }

    /** Tag of the Fragment on screen: a tab or a pushed screen. */
    private var currentTag = TAG_TRACK

    /** Last tab shown; Back from a pushed screen returns here. */
    private var lastTabTag = TAG_TRACK

    /** Enabled only while a pushed screen is showing. */
    private val backToTab = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = show(lastTabTag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        bottomNav = findViewById(R.id.bottom_nav_home)

        onBackPressedDispatcher.addCallback(this, backToTab)

        if (savedInstanceState != null) {
            currentTag = savedInstanceState.getString(STATE_CURRENT_TAG, TAG_TRACK)
            lastTabTag = savedInstanceState.getString(STATE_LAST_TAB_TAG, TAG_TRACK)
        }
        // On restore the FragmentManager has already re-added the Fragments;
        // show() re-applies visibility, the bar's checked state and Back.
        show(currentTag)

        bottomNav.setOnItemSelectedListener { item ->
            show(tagFor(item.itemId))
            true
        }
        // Tapping the already-checked tab while a pushed screen is open must still go back to it.
        bottomNav.setOnItemReselectedListener { item -> show(tagFor(item.itemId)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENT_TAG, currentTag)
        outState.putString(STATE_LAST_TAB_TAG, lastTabTag)
    }

    private fun handleEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.NavigateToLogin ->
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )

            HomeEvent.ShowSettings -> show(TAG_SETTINGS)
            HomeEvent.ShowNotifications -> show(TAG_NOTIFICATIONS)
        }
    }

    // ── Fragment hosting ──────────────────────────────────────────────────────

    /** Shows the Fragment for [tag], creating it on first use, and hides the rest. */
    private fun show(tag: String) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)

        fragmentManager.fragments
            .filter { it.tag != tag && !it.isHidden }
            .forEach { transaction.hide(it) }

        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.fragment_container_home, createFragment(tag), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commit()

        currentTag = tag
        val isTab = tag in TAB_TAGS
        if (isTab) lastTabTag = tag
        backToTab.isEnabled = !isTab
        // A non-checkable group renders with no tab selected; re-enabling restores the checked tab.
        bottomNav.menu.setGroupCheckable(0, isTab, true)
    }

    private fun createFragment(tag: String): Fragment =
        when (tag) {
            TAG_TRACK -> TrackFragment()
            TAG_CONTACTS -> ContactsFragment()
            TAG_ALERT -> AlertFragment()
            TAG_PROFILE -> ProfileFragment()
            TAG_SETTINGS -> SettingsFragment()
            TAG_NOTIFICATIONS -> NotificationsFragment()
            else -> throw IllegalArgumentException("Unknown Home destination: $tag")
        }

    private fun tagFor(menuItemId: Int): String =
        when (menuItemId) {
            R.id.nav_contacts -> TAG_CONTACTS
            R.id.nav_alert -> TAG_ALERT
            R.id.nav_profile -> TAG_PROFILE
            else -> TAG_TRACK
        }

    private companion object {
        const val TAG_TRACK = "home_track"
        const val TAG_CONTACTS = "home_contacts"
        const val TAG_ALERT = "home_alert"
        const val TAG_PROFILE = "home_profile"
        const val TAG_SETTINGS = "home_settings"
        const val TAG_NOTIFICATIONS = "home_notifications"

        val TAB_TAGS = setOf(TAG_TRACK, TAG_CONTACTS, TAG_ALERT, TAG_PROFILE)

        const val STATE_CURRENT_TAG = "state_current_tag"
        const val STATE_LAST_TAB_TAG = "state_last_tab_tag"
    }
}
