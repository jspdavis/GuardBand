package com.example.guardband.ui.alert

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.example.guardband.R
import com.example.guardband.data.model.AlertStatus
import com.example.guardband.data.model.AlertType
import com.google.android.material.card.MaterialCardView

/** How a card says when: "5 min ago", or the clock time under a day's heading. */
enum class AlertTimeStyle { RELATIVE, CLOCK }

/**
 * Binds one alert card: an `item_alert.xml` row ([ofItem]), or Alert
 * history's latest card, which has the same shape under its own ids. One
 * binder, so the card and the rows cannot drift apart.
 *
 * - The type's symbol sits on a circle of a pale shade of its colour.
 * - A panic's card is pale red with a red outline; every other card is grey.
 *   A recycled card is reset each time, since a row can stop being a panic.
 * - A panic displays its resolution status badge (Active, Acknowledged, Resolved).
 *   Non-panic alerts hide the status badge.
 * - The place shows with a pin, the battery with a symbol for its level
 *   ([AlertFormatting.batteryIcon]). TalkBack reads "Battery 64%" rather
 *   than the bare "64%" beside the symbol.
 */
internal class AlertCardViews(
    private val card: MaterialCardView,
    private val icon: ImageView,
    private val type: TextView,
    private val time: TextView,
    private val placeRow: View,
    private val place: TextView,
    private val batteryRow: View,
    private val batteryIcon: ImageView,
    private val battery: TextView,
    private val statusBadge: TextView? = null
) {

    fun bind(row: AlertRow, style: AlertTimeStyle) {
        val context = card.context
        val alert = row.alert
        val alertType = alert.alertType()
        val color = context.getColor(AlertFormatting.typeColor(alertType))

        type.setText(AlertFormatting.typeLabel(alertType))
        icon.setImageResource(AlertFormatting.typeIcon(alertType))
        icon.imageTintList = ColorStateList.valueOf(color)
        icon.backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, BADGE_ALPHA))
        time.text = AlertFormatting.timeText(context, row, style)

        if (statusBadge != null) {
            val status = row.status
            if (alertType == AlertType.PANIC && status != null) {
                statusBadge.visibility = View.VISIBLE
                when (status) {
                    AlertStatus.ACTIVE -> {
                        statusBadge.setText(R.string.label_alert_status_active)
                        statusBadge.setTextColor(context.getColor(R.color.alert_panic))
                        statusBadge.backgroundTintList =
                            ColorStateList.valueOf(ColorUtils.setAlphaComponent(context.getColor(R.color.alert_panic), BADGE_ALPHA))
                    }
                    AlertStatus.ACKNOWLEDGED -> {
                        statusBadge.setText(R.string.label_alert_status_acknowledged)
                        statusBadge.setTextColor(context.getColor(R.color.alert_low_battery))
                        statusBadge.backgroundTintList =
                            ColorStateList.valueOf(ColorUtils.setAlphaComponent(context.getColor(R.color.alert_low_battery), BADGE_ALPHA))
                    }
                    AlertStatus.RESOLVED -> {
                        statusBadge.setText(R.string.label_alert_status_resolved)
                        statusBadge.setTextColor(context.getColor(R.color.brand_primary))
                        statusBadge.backgroundTintList =
                            ColorStateList.valueOf(ColorUtils.setAlphaComponent(context.getColor(R.color.brand_primary), BADGE_ALPHA))
                    }
                }
            } else {
                statusBadge.visibility = View.GONE
            }
        }

        val panic = alertType == AlertType.PANIC
        card.setCardBackgroundColor(
            context.getColor(if (panic) R.color.alert_panic_container else R.color.surface_card)
        )
        card.strokeColor = context.getColor(R.color.alert_panic_outline)
        card.strokeWidth =
            if (panic) context.resources.getDimensionPixelSize(R.dimen.alert_card_panic_stroke) else 0

        val location = alert.location
        placeRow.visibility = if (location == null) View.GONE else View.VISIBLE
        if (location != null) {
            place.text = AlertFormatting.locationText(context, location, row.placeName)
        }

        val level = alert.battery
        batteryRow.visibility = if (level == null) View.GONE else View.VISIBLE
        if (level != null) {
            batteryIcon.setImageResource(AlertFormatting.batteryIcon(level))
            batteryIcon.imageTintList =
                ColorStateList.valueOf(context.getColor(AlertFormatting.batteryColor(level)))
            battery.text = context.getString(
                if (level.isCharging) R.string.label_alert_battery_level_charging
                else R.string.label_alert_battery_level,
                level.percent
            )
            battery.contentDescription = context.getString(
                if (level.isCharging) R.string.label_alert_battery_charging
                else R.string.label_alert_battery,
                level.percent
            )
        }
    }

    companion object {
        /** The badge's shade: the type's colour at about 16%. DESIGN: Jul */
        private const val BADGE_ALPHA = 0x29

        /** The binder for an `item_alert.xml` row. */
        fun ofItem(view: View) = AlertCardViews(
            card = view as MaterialCardView,
            icon = view.findViewById(R.id.iv_item_alert_icon),
            type = view.findViewById(R.id.tv_item_alert_type),
            time = view.findViewById(R.id.tv_item_alert_time),
            placeRow = view.findViewById(R.id.ll_item_alert_place),
            place = view.findViewById(R.id.tv_item_alert_location),
            batteryRow = view.findViewById(R.id.ll_item_alert_battery),
            batteryIcon = view.findViewById(R.id.iv_item_alert_battery_icon),
            battery = view.findViewById(R.id.tv_item_alert_battery),
            statusBadge = view.findViewById(R.id.tv_item_alert_status)
        )
    }
}
