package com.example.guardband.ui.receiver

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
 * RecyclerView adapter for displaying alert history
 */
class AlertHistoryAdapter : ListAdapter<Alert, AlertHistoryAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert_history, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvType: TextView = itemView.findViewById(R.id.tvAlertType)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvAlertTimestamp)
        private val tvSequence: TextView = itemView.findViewById(R.id.tvAlertSequence)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvAlertLocation)
        private val tvBattery: TextView = itemView.findViewById(R.id.tvAlertBattery)

        fun bind(alert: Alert) {
            tvType.text = alert.type
            tvTimestamp.text = alert.getFormattedTimestamp()
            tvSequence.text = "Seq: ${alert.sequenceId}"

            // Location
            alert.location?.let { loc ->
                tvLocation.text = "${loc.lat}, ${loc.lng}"
                tvLocation.visibility = View.VISIBLE
            } ?: run {
                tvLocation.visibility = View.GONE
            }

            // Battery
            alert.battery?.let { bat ->
                tvBattery.text = "${bat.percent}%"
                tvBattery.visibility = View.VISIBLE
            } ?: run {
                tvBattery.visibility = View.GONE
            }

            // Set background color based on alert type
            when (alert.getAlertType()) {
                com.example.guardband.data.model.AlertType.PANIC -> {
                    itemView.setBackgroundColor(itemView.context.getColor(android.R.color.holo_red_light))
                }
                com.example.guardband.data.model.AlertType.LOW_BATTERY -> {
                    itemView.setBackgroundColor(itemView.context.getColor(android.R.color.holo_orange_light))
                }
                com.example.guardband.data.model.AlertType.CHECKIN -> {
                    itemView.setBackgroundColor(itemView.context.getColor(android.R.color.holo_green_light))
                }
                com.example.guardband.data.model.AlertType.TRACKING_UPDATE -> {
                    itemView.setBackgroundColor(itemView.context.getColor(android.R.color.holo_blue_light))
                }
            }
        }
    }

    class AlertDiffCallback : DiffUtil.ItemCallback<Alert>() {
        override fun areItemsTheSame(oldItem: Alert, newItem: Alert): Boolean {
            return oldItem.sequenceId == newItem.sequenceId
        }

        override fun areContentsTheSame(oldItem: Alert, newItem: Alert): Boolean {
            return oldItem == newItem
        }
    }
}
