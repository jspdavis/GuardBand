package com.example.guardband.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.account.AccountActivity
import com.example.guardband.ui.alert.AlertFragment
import com.example.guardband.ui.alert.AlertHistoryFragment
import com.example.guardband.ui.consent.ConsentRequiredActivity
import com.example.guardband.ui.contacts.ContactsFragment
import com.example.guardband.ui.login.LoginActivity
import com.example.guardband.ui.notifications.NotificationsFragment
import com.example.guardband.ui.profile.ProfileFragment
import com.example.guardband.ui.settings.SettingsFragment
import com.example.guardband.ui.track.TrackFragment
import com.example.guardband.ui.verify.VerifyRequiredActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * Post-auth host: four tab Fragments (Track, Contacts, Alert, Profile) under a
 * BottomNavigationView, plus two pushed Fragments (Settings, Notifications)
 * opened from Track's top bar. No Navigation Component.
 *
 * Fragments are added once and then shown/hidden, so every tab keeps its
 * state. A pushed screen hides the bar (it is not a place the tabs lead
 * from); Back (or its back arrow) returns to the last tab and brings the bar
 * back. Back on a tab leaves the app.
 *
 * **Nothing is shown until the account gate opens.** [HomeViewModel.gate]
 * reads the account's record first; until it is [HomeGateState.OPEN] the
 * cover `fl_home_gate` sits over everything, with a spinner while checking
 * and Retry if the read failed, and no tab Fragment is added. On a restore
 * the FragmentManager re-adds the old ones by itself; the cover hides them
 * until the gate opens again.
 *
 * View ids: fragment_container_home, view_home_nav_divider, bottom_nav_home,
 * fl_home_gate, progress_home_gate, ll_home_gate_failed, tv_home_gate_failed,
 * btn_home_gate_retry.
 *
 * Flow: Splash / Loading → HomeActivity → (Log Out in Settings, or no session) → LoginActivity
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navDivider: View
    private lateinit var gateCover: View
    private lateinit var gateProgress: View
    private lateinit var gateFailed: View

    /** True once the gate has opened and the first tab has been shown. */
    private var tabsShown = false

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
        navDivider = findViewById(R.id.view_home_nav_divider)
        gateCover = findViewById(R.id.fl_home_gate)
        gateProgress = findViewById(R.id.progress_home_gate)
        gateFailed = findViewById(R.id.ll_home_gate_failed)

        findViewById<View>(R.id.btn_home_gate_retry).setOnClickListener {
            viewModel.onRetryClicked()
        }

        onBackPressedDispatcher.addCallback(this, backToTab)

        if (savedInstanceState != null) {
            currentTag = savedInstanceState.getString(STATE_CURRENT_TAG, TAG_TRACK)
            lastTabTag = savedInstanceState.getString(STATE_LAST_TAB_TAG, TAG_TRACK)
        }

        bottomNav.setOnItemSelectedListener { item ->
            show(tagFor(item.itemId))
            true
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gate.collect(::renderGate)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    /**
     * Covers everything until the gate opens, then shows the tab it was on.
     *
     * The first [show] waits for [HomeGateState.OPEN]: on a restore the
     * FragmentManager has already re-added the Fragments, and show() then
     * re-applies visibility, the bar's checked state and Back.
     */
    private fun renderGate(state: HomeGateState) {
        val open = state == HomeGateState.OPEN
        gateCover.isVisible = !open
        gateProgress.isVisible = state == HomeGateState.CHECKING
        gateFailed.isVisible = state == HomeGateState.FAILED

        if (open && !tabsShown) {
            tabsShown = true
            show(currentTag)
        } else if (!open) {
            bottomNav.isVisible = false
            navDivider.isVisible = false
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
            // Through the bar rather than show(TAG_PROFILE), so the Profile item
            // is highlighted too: selecting it runs the same listener a tap does.
            HomeEvent.ShowProfile -> bottomNav.selectedItemId = R.id.nav_profile
            HomeEvent.ShowAlertHistory -> show(TAG_ALERT_HISTORY)

            // The task is cleared so the tabs cannot be reached with Back:
            // there is no account behind them any more, only a sign-in to
            // finish deleting. Back from Account information leaves the app,
            // and the next launch comes straight back here.
            HomeEvent.ResumeDeletion -> leaveFor(AccountActivity::class.java)

            // Cleared for the same reason: Back from the gate must not land
            // on tabs the account is not allowed to use yet.
            HomeEvent.RequireEmailVerification -> leaveFor(VerifyRequiredActivity::class.java)

            // Cleared too: Back from the consent screen must not reach tabs
            // the account has not agreed to use.
            HomeEvent.RequireConsent -> leaveFor(ConsentRequiredActivity::class.java)
        }
    }

    /** Replaces the whole task with [target], so Back cannot return here. */
    private fun leaveFor(target: Class<out AppCompatActivity>) {
        startActivity(
            Intent(this, target).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
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
        // Settings and Notifications are reached from a tab, not from the bar,
        // so the bar is not offered while they are showing.
        bottomNav.isVisible = isTab
        navDivider.isVisible = isTab
    }

    private fun createFragment(tag: String): Fragment =
        when (tag) {
            TAG_TRACK -> TrackFragment()
            TAG_CONTACTS -> ContactsFragment()
            TAG_ALERT -> AlertFragment()
            TAG_PROFILE -> ProfileFragment()
            TAG_SETTINGS -> SettingsFragment()
            TAG_NOTIFICATIONS -> NotificationsFragment()
            TAG_ALERT_HISTORY -> AlertHistoryFragment()
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
        const val TAG_ALERT_HISTORY = "home_alert_history"

        val TAB_TAGS = setOf(TAG_TRACK, TAG_CONTACTS, TAG_ALERT, TAG_PROFILE)

        const val STATE_CURRENT_TAG = "state_current_tag"
        const val STATE_LAST_TAB_TAG = "state_last_tab_tag"
    }
}
