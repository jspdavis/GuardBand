package com.example.guardband.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.about.AboutActivity
import com.example.guardband.ui.account.AccountActivity
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.LoginNavigation
import com.example.guardband.ui.changelog.ChangelogActivity
import com.example.guardband.ui.notifications.AlertNotificationsActivity
import kotlinx.coroutines.launch

/**
 * Settings, pushed inside the Home host (the host hides the bottom bar while
 * it shows). The back arrow goes through the Activity's Back handling, which
 * returns to the last tab.
 *
 * Account information opens [AccountActivity], which is where Delete account
 * lives now - one level deeper than this list, so the destructive action is
 * not a stray tap away.
 *
 * **A chevron means another screen of the app**, so every row here has
 * one; a row that opens a web or system page uses `SettingsRow.Action`
 * instead (About's Privacy Notice). Alert notifications' switch lives on its
 * own screen, [AlertNotificationsActivity], and the Privacy Notice inside
 * [AboutActivity], since 2026-10-20. Display and Device information are
 * placeholders.
 *
 * View ids: ib_settings_back, tv_settings_title, tv_settings_account,
 * tv_settings_change_password, tv_settings_alert_notifications,
 * tv_settings_display, tv_settings_device_info, tv_settings_changelog,
 * tv_settings_about, btn_settings_logout.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(requireActivity()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_settings_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<TextView>(R.id.tv_settings_account)
            .setOnClickListener { viewModel.onAccountClicked() }

        view.findViewById<TextView>(R.id.tv_settings_change_password)
            .setOnClickListener { viewModel.onPlaceholderRowClicked() }

        view.findViewById<TextView>(R.id.tv_settings_alert_notifications)
            .setOnClickListener { viewModel.onAlertNotificationsClicked() }

        view.findViewById<TextView>(R.id.tv_settings_display)
            .setOnClickListener { viewModel.onDisplayClicked() }

        view.findViewById<TextView>(R.id.tv_settings_device_info)
            .setOnClickListener { viewModel.onPlaceholderRowClicked() }

        view.findViewById<TextView>(R.id.tv_settings_about)
            .setOnClickListener { viewModel.onAboutClicked() }

        view.findViewById<TextView>(R.id.tv_settings_changelog)
            .setOnClickListener { viewModel.onChangelogClicked() }

        view.findViewById<Button>(R.id.btn_settings_logout)
            .setOnClickListener { viewModel.onLogoutClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()

            SettingsEvent.NavigateToLogin -> viewLifecycleOwner.lifecycleScope.launch {
                LoginNavigation.clearCredentialsThenGoToLogin(requireContext(), googleIdTokenProvider)
            }

            SettingsEvent.OpenAccount ->
                startActivity(Intent(requireContext(), AccountActivity::class.java))

            SettingsEvent.OpenChangelog ->
                startActivity(Intent(requireContext(), ChangelogActivity::class.java))

            SettingsEvent.OpenAlertNotifications ->
                startActivity(Intent(requireContext(), AlertNotificationsActivity::class.java))

            SettingsEvent.OpenAbout ->
                startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
    }
}
