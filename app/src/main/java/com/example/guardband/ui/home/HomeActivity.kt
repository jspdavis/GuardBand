package com.example.guardband.ui.home

import android.content.Context
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
import com.example.guardband.ui.alert.AlertHistoryFragment
import com.example.guardband.ui.incident.IncidentFragment
import com.example.guardband.ui.consent.ConsentRequiredActivity
import com.example.guardband.ui.contacts.ContactsFragment
import com.example.guardband.ui.login.LoginActivity
import com.example.guardband.ui.notifications.AlertNotificationsActivity
import com.example.guardband.ui.notifications.AlertWatch
import com.example.guardband.ui.notifications.NotificationsFragment
import com.example.guardband.ui.profile.ProfileFragment
import com.example.guardband.ui.settings.SettingsFragment
import com.example.guardband.ui.track.TrackFragment
import com.example.guardband.ui.verify.VerifyRequiredActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * Post-auth host: three tab Fragments (Track, Contacts, Profile) under a
 * BottomNavigationView, plus three pushed Fragments: Settings and
 * Notifications from a tab's top bar, and Alert history from "See all" on
 * Track's recent-alerts panel. No Navigation Component. The destinations and
 * the restore rule are [HomeDestinations]; the Alert tab was removed on
 * 2026-10-19.
 *
 * Fragments are added once and then shown/hidden, so every tab keeps its
 * state. A pushed screen hides the bar (it is not a place the tabs lead
 * from); Back (or its back arrow) returns to the last tab and brings the bar
 * back. Back on a tab leaves the app.
 *
 * **The Incident view is the one exception** (2026-10-27): one panic per
 * screen, so a new Fragment is added each time one opens
 * ([HomeEvent.ShowIncident], [showIncident]) and it is removed, not hidden,
 * when anything else is shown, so its live read ends with it. Back from it
 * returns to the screen that opened it, Track or Alert history
 * ([incidentReturnTag]), not to the last tab. Still no back stack and no
 * `replace()`.
 *
 * **Nothing is shown until the account gate opens.** [HomeViewModel.gate]
 * reads the account's record first; until it is [HomeGateState.OPEN] the
 * cover `fl_home_gate` sits over everything, with a spinner while checking
 * and Retry if the read failed, and no tab Fragment is added. On a restore
 * the FragmentManager re-adds the old ones by itself; the cover hides them
 * until the gate opens again.
 *
 * **Notifications land here** ([launchIntent]): an alert opens Alert history,
 * the watch's ongoing notification opens Alert notifications over Settings,
 * both through the gate
 * ([HomeViewModel.onLaunchTargetRequested]). Once the gate opens, the alert
 * watch is started if the user turned it on ([AlertWatch.startIfEnabled]).
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
    private var currentTag = HomeDestinations.TRACK

    /** Last tab shown; Back from a pushed screen returns here. */
    private var lastTabTag = HomeDestinations.TRACK

    /**
     * Where Back from an Incident view returns: the screen that opened it,
     * Track (sheet, pin or bubble) or Alert history.
     */
    private var incidentReturnTag = HomeDestinations.TRACK

    /** Enabled only while a pushed screen is showing. */
    private val backToTab = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() =
            show(if (currentTag == HomeDestinations.INCIDENT) incidentReturnTag else lastTabTag)
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
            // A tag this build does not host (the removed Alert tab) is Track.
            currentTag = HomeDestinations.restoreCurrent(
                savedInstanceState.getString(STATE_CURRENT_TAG)
            )
            lastTabTag = HomeDestinations.restoreLastTab(
                savedInstanceState.getString(STATE_LAST_TAB_TAG)
            )
            incidentReturnTag = HomeDestinations.restoreIncidentReturn(
                savedInstanceState.getString(STATE_INCIDENT_RETURN_TAG)
            )
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

        // Not on a restore: the Intent still carries the extra, and the
        // screen the user was on is restored instead.
        if (savedInstanceState == null) handleLaunchTarget(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchTarget(intent)
    }

    /** Forwards a notification's target once, then forgets it. */
    private fun handleLaunchTarget(intent: Intent) {
        val name = intent.getStringExtra(EXTRA_LAUNCH_TARGET) ?: return
        intent.removeExtra(EXTRA_LAUNCH_TARGET)
        val target = HomeLaunchTarget.entries.find { it.name == name } ?: return
        viewModel.onLaunchTargetRequested(target)
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
            AlertWatch.startIfEnabled(this)
        } else if (!open) {
            bottomNav.isVisible = false
            navDivider.isVisible = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENT_TAG, currentTag)
        outState.putString(STATE_LAST_TAB_TAG, lastTabTag)
        outState.putString(STATE_INCIDENT_RETURN_TAG, incidentReturnTag)
    }

    private fun handleEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.NavigateToLogin -> {
                // A session that ended elsewhere: nothing may keep watching for it.
                AlertWatch.stop(this)
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }

            HomeEvent.ShowSettings -> show(HomeDestinations.SETTINGS)
            HomeEvent.ShowAlertNotifications -> {
                show(HomeDestinations.SETTINGS)
                startActivity(Intent(this, AlertNotificationsActivity::class.java))
            }
            HomeEvent.ShowNotifications -> show(HomeDestinations.NOTIFICATIONS)
            HomeEvent.ShowAlertHistory -> show(HomeDestinations.ALERT_HISTORY)
            is HomeEvent.ShowIncident -> showIncident(event.panicSequenceId)

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

    /**
     * Shows the Fragment for [tag], creating it on first use, and hides the
     * rest. An open Incident view is **removed** rather than hidden, so its
     * live read ends with it; it can only be shown again by [showIncident],
     * which knows the panic. Asked for one that is gone (a restore that lost
     * it), this shows the screen it would have returned to.
     */
    private fun show(tag: String) {
        val fragmentManager = supportFragmentManager
        val incident = fragmentManager.findFragmentByTag(HomeDestinations.INCIDENT)
        if (tag == HomeDestinations.INCIDENT && incident == null) {
            show(incidentReturnTag)
            return
        }
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)

        fragmentManager.fragments
            .filter { it.tag != tag && !it.isHidden && it !== incident }
            .forEach { transaction.hide(it) }
        if (incident != null && tag != HomeDestinations.INCIDENT) transaction.remove(incident)

        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.fragment_container_home, createFragment(tag), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commit()

        showChrome(tag)
    }

    /**
     * Opens the Incident view of [panicSequenceId]: a new Fragment each time,
     * the panic in its arguments, over whatever opened it. An incident already
     * open is removed first, and Back still returns to the screen before it.
     */
    private fun showIncident(panicSequenceId: Long) {
        if (currentTag != HomeDestinations.INCIDENT) {
            incidentReturnTag = HomeDestinations.restoreIncidentReturn(currentTag)
        }
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
        fragmentManager.fragments.forEach { fragment ->
            if (fragment.tag == HomeDestinations.INCIDENT) transaction.remove(fragment)
            else if (!fragment.isHidden) transaction.hide(fragment)
        }
        transaction.add(
            R.id.fragment_container_home,
            IncidentFragment.newInstance(panicSequenceId),
            HomeDestinations.INCIDENT
        )
        transaction.commit()

        showChrome(HomeDestinations.INCIDENT)
    }

    /** The bar, its selection and Back, for the screen [tag] now showing. */
    private fun showChrome(tag: String) {
        currentTag = tag
        val isTab = HomeDestinations.isTab(tag)
        if (isTab) {
            lastTabTag = tag
            // The bar restores its own selection, but a selection it cannot
            // restore (the removed Alert tab's item) would leave it guessing.
            bottomNav.menu.findItem(menuItemIdFor(tag))?.isChecked = true
        }
        backToTab.isEnabled = !isTab
        // Pushed screens are reached from a tab, not from the bar, so the bar
        // is not offered while they are showing.
        bottomNav.isVisible = isTab
        navDivider.isVisible = isTab
    }

    private fun createFragment(tag: String): Fragment =
        when (tag) {
            HomeDestinations.TRACK -> TrackFragment()
            HomeDestinations.CONTACTS -> ContactsFragment()
            HomeDestinations.PROFILE -> ProfileFragment()
            HomeDestinations.SETTINGS -> SettingsFragment()
            HomeDestinations.NOTIFICATIONS -> NotificationsFragment()
            HomeDestinations.ALERT_HISTORY -> AlertHistoryFragment()
            // INCIDENT needs its panic: only showIncident creates it.
            else -> throw IllegalArgumentException("Unknown Home destination: $tag")
        }

    private fun tagFor(menuItemId: Int): String =
        when (menuItemId) {
            R.id.nav_contacts -> HomeDestinations.CONTACTS
            R.id.nav_profile -> HomeDestinations.PROFILE
            else -> HomeDestinations.TRACK
        }

    private fun menuItemIdFor(tabTag: String): Int =
        when (tabTag) {
            HomeDestinations.CONTACTS -> R.id.nav_contacts
            HomeDestinations.PROFILE -> R.id.nav_profile
            else -> R.id.nav_track
        }

    companion object {
        private const val STATE_CURRENT_TAG = "state_current_tag"
        private const val STATE_LAST_TAB_TAG = "state_last_tab_tag"
        private const val STATE_INCIDENT_RETURN_TAG = "state_incident_return_tag"
        private const val EXTRA_LAUNCH_TARGET = "com.example.guardband.extra.LAUNCH_TARGET"

        /**
         * Opens the host at [target], for a notification's tap. Brings an
         * existing host forward (clearing anything above it) rather than
         * stacking a second one; a fresh one still meets the gate first.
         */
        fun launchIntent(context: Context, target: HomeLaunchTarget): Intent =
            Intent(context, HomeActivity::class.java)
                .putExtra(EXTRA_LAUNCH_TARGET, target.name)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
    }
}
