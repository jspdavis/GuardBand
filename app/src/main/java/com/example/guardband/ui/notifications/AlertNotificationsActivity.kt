package com.example.guardband.ui.notifications

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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

/**
 * Alert notifications: the switch that starts and stops the alert watch,
 * opened from its row in Settings (and from the watch's own ongoing
 * notification, through the Home gate and Settings).
 *
 * This Activity owns what needs a Context: whether the phone allows the
 * app's notifications, the permission prompt, starting and stopping the
 * watch, and the two system pages. The switch is re-read every time the
 * screen comes to the front, since notifications can be turned off in system
 * settings.
 *
 * An Activity rather than a Fragment pushed over the Home host, for the
 * reason Account information and Changelog are: the host's Back always
 * returns to a tab, and Back from here has to return to Settings. It sits
 * outside the host's gates like those two; it shows nothing about the user
 * or the band, and the watch it starts checks the session itself
 * ([AlertWatch.shouldRun]).
 *
 * View ids: ib_alert_notifications_back, tv_alert_notifications_title,
 * sw_alert_notifications, tv_alert_notifications_help,
 * btn_alert_notifications_system_settings, ll_alert_notifications_battery,
 * tv_alert_notifications_battery.
 *
 * Flow: Settings → AlertNotificationsActivity → (Back) Settings
 */
class AlertNotificationsActivity : AppCompatActivity() {

    private val viewModel: AlertNotificationsViewModel by viewModels { AlertNotificationsViewModel.Factory }

    private val askNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermissionResult(granted)
        }

    private lateinit var alertSwitch: MaterialSwitch
    private lateinit var alertHelp: TextView
    private lateinit var notificationSettingsButton: Button
    private lateinit var batterySection: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_notifications)

        findViewById<ImageButton>(R.id.ib_alert_notifications_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        alertSwitch = findViewById(R.id.sw_alert_notifications)
        alertHelp = findViewById(R.id.tv_alert_notifications_help)
        notificationSettingsButton = findViewById(R.id.btn_alert_notifications_system_settings)
        batterySection = findViewById(R.id.ll_alert_notifications_battery)

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
        findViewById<TextView>(R.id.tv_alert_notifications_battery)
            .setOnClickListener { viewModel.onBatterySettingsClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenShown(notificationsAllowed())
    }

    private fun render(state: AlertNotificationsUiState) {
        alertSwitch.isChecked = state.alertNotificationsOn
        alertHelp.setText(
            if (state.notificationsBlocked) R.string.label_settings_alert_notifications_blocked
            else R.string.label_settings_alert_notifications_help
        )
        notificationSettingsButton.isVisible = state.notificationsBlocked
        batterySection.isVisible = state.alertNotificationsOn
    }

    private fun handleEvent(event: AlertNotificationsEvent) {
        when (event) {
            is AlertNotificationsEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            AlertNotificationsEvent.RequestNotificationPermission ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

            AlertNotificationsEvent.StartAlertWatch -> AlertWatch.start(this)

            AlertNotificationsEvent.StopAlertWatch -> AlertWatch.stop(this)

            AlertNotificationsEvent.OpenNotificationSettings -> openSystemPage(notificationSettingsIntent())

            AlertNotificationsEvent.OpenBatterySettings ->
                openSystemPage(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    /** The runtime prompt exists (API 33+) and has not been granted. */
    private fun canAskPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

    /** This app's notification page; the app's details page before API 26. */
    private fun notificationSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        }

    private fun openSystemPage(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            viewModel.onSystemSettingsUnavailable()
        }
    }
}
