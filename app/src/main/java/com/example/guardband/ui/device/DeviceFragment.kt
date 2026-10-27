package com.example.guardband.ui.device

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.alert.AlertFormatting
import com.example.guardband.ui.alert.AlertRow
import com.example.guardband.ui.alert.AlertViewModel
import com.example.guardband.ui.avatar.AvatarImage
import com.example.guardband.ui.contacts.ContactsViewModel
import com.example.guardband.ui.home.HomeViewModel
import com.example.guardband.ui.track.Elapsed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Device tab (the Profile tab until 2026-10-27): the account header, the
 * band card, the connected account, the pairing placeholder and how the band
 * works. See [DeviceViewModel] for where each value comes from.
 *
 * **It adds no listener.** It hands [DeviceViewModel] the state of the two
 * host-scoped ViewModels that already read the data: [AlertViewModel] (the
 * history window) and [ContactsViewModel] (the Contacts tab's list). It never
 * collects their events, which belong to their own screens.
 *
 * Nothing on this screen shows a coordinate: positions are place names, or
 * a pointer to the map.
 *
 * View ids: ib_device_settings, ib_device_notifications, ll_device_header,
 * iv_device_avatar, tv_device_avatar_initials, tv_device_name,
 * tv_device_email, card_device_band, tv_device_band_name, tv_device_band_id,
 * tv_device_band_status, progress_device_band, tv_device_band_message,
 * ll_device_band_details, ll_device_battery, iv_device_battery_icon,
 * tv_device_battery, tv_device_no_alerts, ll_device_last_alert,
 * iv_device_last_alert_icon, tv_device_last_alert_type,
 * tv_device_last_alert_time, tv_device_last_alert_place, tv_device_position,
 * card_device_account, tv_device_sign_in_method, tv_device_verification,
 * row_device_contacts, row_device_pair, card_device_how.
 */
class DeviceFragment : Fragment(R.layout.fragment_device) {

    private lateinit var avatarImage: AvatarImage
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvBandId: TextView
    private lateinit var tvBandStatus: TextView
    private lateinit var progressBand: ProgressBar
    private lateinit var tvBandMessage: TextView
    private lateinit var llBandDetails: View
    private lateinit var ivBatteryIcon: ImageView
    private lateinit var tvBattery: TextView
    private lateinit var tvNoAlerts: TextView
    private lateinit var llLastAlert: View
    private lateinit var ivLastAlertIcon: ImageView
    private lateinit var tvLastAlertType: TextView
    private lateinit var tvLastAlertTime: TextView
    private lateinit var tvLastAlertPlace: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvSignInMethod: TextView
    private lateinit var tvVerification: TextView
    private lateinit var rowContacts: TextView

    private val viewModel: DeviceViewModel by viewModels { DeviceViewModel.Factory }
    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }
    private val alertViewModel: AlertViewModel by activityViewModels { AlertViewModel.Factory }
    private val contactsViewModel: ContactsViewModel by activityViewModels { ContactsViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_device_settings)
            .setOnClickListener { homeViewModel.onSettingsClicked() }
        view.findViewById<ImageButton>(R.id.ib_device_notifications)
            .setOnClickListener { homeViewModel.onNotificationsClicked() }

        avatarImage = AvatarImage(
            view.findViewById(R.id.iv_device_avatar),
            view.findViewById(R.id.tv_device_avatar_initials)
        )
        tvName = view.findViewById(R.id.tv_device_name)
        tvEmail = view.findViewById(R.id.tv_device_email)
        tvBandId = view.findViewById(R.id.tv_device_band_id)
        tvBandStatus = view.findViewById(R.id.tv_device_band_status)
        progressBand = view.findViewById(R.id.progress_device_band)
        tvBandMessage = view.findViewById(R.id.tv_device_band_message)
        llBandDetails = view.findViewById(R.id.ll_device_band_details)
        ivBatteryIcon = view.findViewById(R.id.iv_device_battery_icon)
        tvBattery = view.findViewById(R.id.tv_device_battery)
        tvNoAlerts = view.findViewById(R.id.tv_device_no_alerts)
        llLastAlert = view.findViewById(R.id.ll_device_last_alert)
        ivLastAlertIcon = view.findViewById(R.id.iv_device_last_alert_icon)
        tvLastAlertType = view.findViewById(R.id.tv_device_last_alert_type)
        tvLastAlertTime = view.findViewById(R.id.tv_device_last_alert_time)
        tvLastAlertPlace = view.findViewById(R.id.tv_device_last_alert_place)
        tvPosition = view.findViewById(R.id.tv_device_position)
        tvSignInMethod = view.findViewById(R.id.tv_device_sign_in_method)
        tvVerification = view.findViewById(R.id.tv_device_verification)
        rowContacts = view.findViewById(R.id.row_device_contacts)

        rowContacts.setOnClickListener { homeViewModel.onContactsSummaryClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        // Its own collector, so a band update does not re-request the photo:
        // only a different face reaches the avatar.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.map { it.header.face }.distinctUntilChanged()
                    .collect { face -> avatarImage.show(face.photoUrl, face.initials) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                alertViewModel.uiState.collect(viewModel::onAlertsChanged)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                contactsViewModel.uiState.collect(viewModel::onContactsChanged)
            }
        }
    }

    override fun onDestroyView() {
        avatarImage.dispose()
        super.onDestroyView()
    }

    private fun render(state: DeviceUiState) {
        tvName.text = state.header.name
        tvEmail.text = state.header.email

        when (val pairing = state.pairing) {
            // Unreachable until pairing exists; the card still says something.
            DevicePairing.Unpaired -> renderUnpaired()
            is DevicePairing.Paired -> renderBand(pairing)
        }

        tvSignInMethod.setText(
            when (state.account.signInMethod) {
                SignInMethod.EMAIL -> R.string.label_device_sign_in_email
                SignInMethod.GOOGLE -> R.string.label_device_sign_in_google
                SignInMethod.EMAIL_AND_GOOGLE -> R.string.label_device_sign_in_both
                SignInMethod.UNKNOWN -> R.string.label_device_sign_in_unknown
            }
        )
        tvVerification.setText(
            if (state.account.emailVerified) R.string.label_account_verified
            else R.string.label_account_not_verified
        )

        rowContacts.text = when (val contacts = state.contacts) {
            ContactsSummary.Loading -> getString(R.string.label_device_contacts_loading)
            ContactsSummary.Unavailable -> getString(R.string.label_device_contacts_unavailable)
            is ContactsSummary.Counted ->
                getString(R.string.label_device_contacts, contacts.total, contacts.starred)
        }
    }

    private fun renderUnpaired() {
        tvBandId.text = ""
        tvBandStatus.isVisible = false
        progressBand.isVisible = false
        llBandDetails.isVisible = false
        tvBandMessage.isVisible = true
        tvBandMessage.setText(R.string.label_device_coming_soon)
    }

    private fun renderBand(pairing: DevicePairing.Paired) {
        val band = pairing.band
        tvBandId.text = getString(R.string.label_device_band_id, pairing.bandId)

        progressBand.isVisible = band.isLoading
        val message = when {
            band.errorMessage != null -> band.errorMessage
            band.showsNoData -> getString(R.string.label_device_no_data)
            else -> null
        }
        tvBandMessage.isVisible = message != null
        tvBandMessage.text = message
        llBandDetails.isVisible = band.hasReported

        tvBandStatus.isVisible = band.hasReported
        if (band.hasReported) renderStatus(band)
        if (!band.hasReported) return

        renderBattery(band)
        renderLastAlert(band.lastAlert)
        tvPosition.text = when (val position = band.position) {
            BandPosition.None -> getString(R.string.label_device_position_none)
            is BandPosition.Named -> position.name
            BandPosition.Unnamed -> getString(R.string.label_device_place_unnamed)
            BandPosition.Moving -> getString(R.string.label_device_position_moving)
        }
    }

    /** "Reporting" in green, else "Last seen 5 min ago" in grey. DESIGN: Jul */
    private fun renderStatus(band: BandCard) {
        val context = requireContext()
        if (band.isReporting) {
            tvBandStatus.setText(R.string.label_device_status_reporting)
            tvBandStatus.setTextColor(context.getColor(R.color.success_green))
            return
        }
        tvBandStatus.setTextColor(context.getColor(R.color.text_secondary))
        tvBandStatus.text = when (val elapsed = band.lastSeen) {
            null -> getString(R.string.label_device_status_last_seen_unknown)
            else -> getString(R.string.label_device_status_last_seen, agoText(elapsed))
        }
    }

    /** The alert rows' battery symbol and colour: orange at 20% or below. */
    private fun renderBattery(band: BandCard) {
        val battery = band.battery
        ivBatteryIcon.isVisible = battery != null
        if (battery == null) {
            tvBattery.setText(R.string.label_device_battery_unknown)
            tvBattery.setTextColor(requireContext().getColor(R.color.text_primary))
            tvBattery.contentDescription = null
            return
        }
        val context = requireContext()
        ivBatteryIcon.setImageResource(AlertFormatting.batteryIcon(battery))
        ivBatteryIcon.imageTintList =
            ColorStateList.valueOf(context.getColor(AlertFormatting.batteryColor(battery)))
        tvBattery.text = getString(
            if (battery.isCharging) R.string.label_alert_battery_level_charging
            else R.string.label_alert_battery_level,
            battery.percent
        )
        tvBattery.setTextColor(
            context.getColor(if (band.isBatteryLow) R.color.alert_low_battery else R.color.text_primary)
        )
        tvBattery.contentDescription = getString(
            if (battery.isCharging) R.string.label_alert_battery_charging
            else R.string.label_alert_battery,
            battery.percent
        )
    }

    /**
     * The alert rows' type symbol, colour and wording, and "5 min ago". The
     * place is the name the alert screens found, never coordinates.
     */
    private fun renderLastAlert(lastAlert: LastAlert?) {
        tvNoAlerts.isVisible = lastAlert == null
        llLastAlert.isVisible = lastAlert != null
        if (lastAlert == null) return

        val context = requireContext()
        val type = lastAlert.alert.alertType()
        val color = context.getColor(AlertFormatting.typeColor(type))
        ivLastAlertIcon.setImageResource(AlertFormatting.typeIcon(type))
        ivLastAlertIcon.imageTintList = ColorStateList.valueOf(color)
        ivLastAlertIcon.backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, BADGE_ALPHA))
        tvLastAlertType.setText(AlertFormatting.typeLabel(type))
        tvLastAlertType.setTextColor(color)
        tvLastAlertTime.text = AlertFormatting.relativeTime(
            context, AlertRow(lastAlert.alert, elapsed = lastAlert.elapsed)
        )

        tvLastAlertPlace.isVisible = lastAlert.alert.location != null
        tvLastAlertPlace.text =
            lastAlert.placeName ?: getString(R.string.label_device_place_unnamed)
    }

    /** "just now", "5 min ago", "3 hr ago", "2 days ago". */
    private fun agoText(elapsed: Elapsed): String = when (elapsed) {
        is Elapsed.Seconds -> getString(R.string.label_device_just_now)
        is Elapsed.Minutes -> quantity(R.plurals.label_alert_minutes_ago, elapsed.value)
        is Elapsed.Hours -> quantity(R.plurals.label_alert_hours_ago, elapsed.value)
        is Elapsed.Days -> quantity(R.plurals.label_device_days_ago, elapsed.value)
    }

    private fun quantity(resId: Int, value: Long): String =
        resources.getQuantityString(resId, value.toInt(), value.toInt())

    private companion object {
        /** The badge's shade, as on the alert rows (AlertCardViews). DESIGN: Jul */
        const val BADGE_ALPHA = 0x29
    }
}
