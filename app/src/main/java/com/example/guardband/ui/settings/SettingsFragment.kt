package com.example.guardband.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.account.AccountActivity
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.LoginNavigation
import com.example.guardband.ui.changelog.ChangelogActivity
import com.example.guardband.ui.notifications.AlertWatch
import com.google.android.material.materialswitch.MaterialSwitch
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
 * **Alert notifications** is the one switch ([SettingsUiState]). This
 * Fragment owns what needs a Context: whether the phone allows the app's
 * notifications, the permission prompt, starting and stopping the watch, and
 * the two system pages. The switch is re-read every time the screen comes to
 * the front, since notifications can be turned off in system settings.
 *
 * View ids: ib_settings_back, tv_settings_title, tv_settings_account,
 * tv_settings_change_password, sw_settings_alert_notifications,
 * tv_settings_alert_notifications_help, btn_settings_notification_settings,
 * tv_settings_alert_battery, tv_settings_privacy_notice,
 * tv_settings_changelog, btn_settings_logout.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(requireActivity()) }

    private val askNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermissionResult(granted)
        }

    private lateinit var alertSwitch: MaterialSwitch
    private lateinit var alertHelp: TextView
    private lateinit var notificationSettingsButton: Button
    private lateinit var batteryRow: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_settings_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<TextView>(R.id.tv_settings_account)
            .setOnClickListener { viewModel.onAccountClicked() }

        view.findViewById<TextView>(R.id.tv_settings_change_password)
            .setOnClickListener { viewModel.onPlaceholderRowClicked() }

        alertSwitch = view.findViewById(R.id.sw_settings_alert_notifications)
        alertHelp = view.findViewById(R.id.tv_settings_alert_notifications_help)
        notificationSettingsButton = view.findViewById(R.id.btn_settings_notification_settings)
        batteryRow = view.findViewById(R.id.tv_settings_alert_battery)

        // A click, not a checked-change listener: render() sets isChecked
        // too, and that must not count as the user flipping it. The tap has
        // already moved the switch, so render the decided state at once: a
        // state that did not change (still off, waiting for the prompt) emits
        // nothing and would leave it showing "on".
        alertSwitch.setOnClickListener {
            viewModel.onAlertNotificationsToggled(
                on = alertSwitch.isChecked,
                notificationsAllowed = notificationsAllowed(),
                canAskPermission = canAskPermission()
            )
            render(viewModel.uiState.value)
        }
        notificationSettingsButton.setOnClickListener { viewModel.onOpenNotificationSettingsClicked() }
        batteryRow.setOnClickListener { viewModel.onBatterySettingsClicked() }

        view.findViewById<TextView>(R.id.tv_settings_privacy_notice)
            .setOnClickListener { viewModel.onPrivacyNoticeClicked() }

        view.findViewById<TextView>(R.id.tv_settings_changelog)
            .setOnClickListener { viewModel.onChangelogClicked() }

        view.findViewById<Button>(R.id.btn_settings_logout)
            .setOnClickListener { viewModel.onLogoutClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenShown(notificationsAllowed())
    }

    private fun render(state: SettingsUiState) {
        alertSwitch.isChecked = state.alertNotificationsOn
        alertHelp.setText(
            if (state.notificationsBlocked) R.string.label_settings_alert_notifications_blocked
            else R.string.label_settings_alert_notifications_help
        )
        notificationSettingsButton.isVisible = state.notificationsBlocked
        batteryRow.isVisible = state.alertNotificationsOn
    }

    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()

            SettingsEvent.NavigateToLogin -> viewLifecycleOwner.lifecycleScope.launch {
                LoginNavigation.clearCredentialsThenGoToLogin(requireContext(), googleIdTokenProvider)
            }

            is SettingsEvent.OpenUrl -> openUrl(event.url)

            SettingsEvent.OpenAccount ->
                startActivity(Intent(requireContext(), AccountActivity::class.java))

            SettingsEvent.OpenChangelog ->
                startActivity(Intent(requireContext(), ChangelogActivity::class.java))

            SettingsEvent.RequestNotificationPermission ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

            SettingsEvent.StartAlertWatch -> AlertWatch.start(requireContext())

            SettingsEvent.StopAlertWatch -> AlertWatch.stop(requireContext())

            SettingsEvent.OpenNotificationSettings -> openSystemPage(notificationSettingsIntent())

            SettingsEvent.OpenBatterySettings ->
                openSystemPage(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()

    /** The runtime prompt exists (API 33+) and has not been granted. */
    private fun canAskPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

    /** This app's notification page; the app's details page before API 26. */
    private fun notificationSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", requireContext().packageName, null))
        }

    private fun openSystemPage(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            viewModel.onSystemSettingsUnavailable()
        }
    }

    /**
     * Opens a hosted page in whatever browser the phone has.
     *
     * A plain `ACTION_VIEW`, so it needs no permission and no extra
     * dependency. A phone with nothing registered for `https` throws
     * [ActivityNotFoundException], which goes back through the ViewModel so
     * the wording stays with every other user-facing message.
     */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            viewModel.onBrowserUnavailable()
        }
    }
}
