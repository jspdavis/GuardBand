package com.example.guardband.ui.alert

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.Alert

/**
 * Incident-history rows for the Alert tab (`item_alert.xml`), keyed by
 * sequenceId. Adapted from the checkpoint's AlertHistoryAdapter.
 */
class AlertHistoryAdapter : ListAdapter<Alert, AlertHistoryAdapter.AlertViewHolder>(AlertDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Binds one `item_alert.xml` view. Also used directly by [AlertFragment]
     * for the latest-alert card, which includes the same layout.
     */
    class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewDot: View = itemView.findViewById(R.id.view_item_alert_dot)
        private val tvType: TextView = itemView.findViewById(R.id.tv_item_alert_type)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_item_alert_time)
        private val tvLocation: TextView = itemView.findViewById(R.id.tv_item_alert_location)
        private val tvBattery: TextView = itemView.findViewById(R.id.tv_item_alert_battery)

        fun bind(alert: Alert) {
            val context = itemView.context
            val type = alert.alertType()

            tvType.setText(AlertFormatting.typeLabel(type))
            viewDot.backgroundTintList =
                ColorStateList.valueOf(context.getColor(AlertFormatting.typeColor(type)))
            tvTime.text = AlertFormatting.formatTimestamp(alert.timestamp)

            val location = alert.location
            tvLocation.visibility = if (location == null) View.GONE else View.VISIBLE
            if (location != null) {
                tvLocation.text =
                    context.getString(R.string.label_alert_location, location.lat, location.lng)
            }

            val battery = alert.battery
            tvBattery.visibility = if (battery == null) View.GONE else View.VISIBLE
            if (battery != null) {
                tvBattery.text = context.getString(
                    if (battery.isCharging) R.string.label_alert_battery_charging
                    else R.string.label_alert_battery,
                    battery.percent
                )
            }
        }
    }

    private object AlertDiff : DiffUtil.ItemCallback<Alert>() {
        override fun areItemsTheSame(oldItem: Alert, newItem: Alert) =
            oldItem.sequenceId == newItem.sequenceId

        override fun areContentsTheSame(oldItem: Alert, newItem: Alert) =
            oldItem == newItem
    }
}
