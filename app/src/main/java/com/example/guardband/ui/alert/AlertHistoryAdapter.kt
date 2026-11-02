package com.example.guardband.ui.alert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.Alert
import com.example.guardband.ui.incident.IncidentEntry

/**
 * Alert lists: the Alert history screen's, with a heading for each day
 * ([AlertListItem.Header], `item_alert_day_header.xml`), and Track's
 * recent-alerts panel, rows only. Rows are `item_alert.xml`, keyed by
 * sequenceId and bound by [AlertCardViews]. Adapted from the checkpoint's
 * AlertHistoryAdapter.
 *
 * [timeStyle] is how rows say when: the history's rows sit under a day's
 * heading and show the clock time, the panel's say "5 min ago".
 *
 * Items are values, so a place name arriving after the list, or a row's
 * "5 min ago" turning into "6 min ago", changes just that row and DiffUtil
 * rebinds only it.
 *
 * Rows only, not the latest card: it used to share this ViewHolder through
 * an `<include>`, which meant constructing one by hand outside any
 * RecyclerView. It is its own block in `fragment_alert_history.xml`, bound
 * by the same [AlertCardViews].
 *
 * **Panic rows open their Incident view** (2026-10-27) when [onPanicClick] is
 * given: the row gets a click, the card's ripple and a content description
 * that says so. Every other type stays a plain, non-tappable row, and so does
 * every row when [onPanicClick] is null. Which rows open an incident is
 * [IncidentEntry.opensIncident], the rule every entry point shares.
 */
class AlertHistoryAdapter(
    private val timeStyle: AlertTimeStyle,
    private val onPanicClick: ((Alert) -> Unit)? = null
) : ListAdapter<AlertListItem, RecyclerView.ViewHolder>(AlertDiff) {

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is AlertListItem.Header -> VIEW_TYPE_DAY
            is AlertListItem.Row -> VIEW_TYPE_ALERT
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_DAY) {
            DayViewHolder(inflater.inflate(R.layout.item_alert_day_header, parent, false))
        } else {
            AlertViewHolder(inflater.inflate(R.layout.item_alert, parent, false), onPanicClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AlertListItem.Header -> (holder as DayViewHolder).bind(item.day)
            is AlertListItem.Row -> (holder as AlertViewHolder).bind(item.row, timeStyle)
        }
    }

    /** Binds one `item_alert.xml` row; a panic's is tappable when [onPanicClick] is given. */
    class AlertViewHolder(
        itemView: View,
        private val onPanicClick: ((Alert) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val views = AlertCardViews.ofItem(itemView)

        fun bind(row: AlertRow, style: AlertTimeStyle) {
            views.bind(row, style)

            // Reset on every bind: a recycled card can stop being a panic.
            val onClick = onPanicClick?.takeIf { IncidentEntry.opensIncident(row.alert) }
            if (onClick == null) {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                itemView.isFocusable = false
                itemView.contentDescription = null
            } else {
                itemView.setOnClickListener { onClick(row.alert) }
                itemView.isFocusable = true
                itemView.contentDescription = openIncidentDescription(row, style)
            }
        }

        /** What TalkBack reads for a tappable row: what it shows, then what a tap does. */
        private fun openIncidentDescription(row: AlertRow, style: AlertTimeStyle): String {
            val context = itemView.context
            val shown = listOfNotNull(
                context.getString(AlertFormatting.typeLabel(row.alert.alertType())),
                AlertFormatting.timeText(context, row, style),
                row.alert.location?.let { AlertFormatting.locationText(context, it, row.placeName) }
            )
            return context.getString(R.string.cd_alert_open_incident, shown.joinToString(", "))
        }
    }

    /** Binds one day's heading. */
    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.tv_item_alert_day)

        fun bind(day: AlertDay) {
            label.text = AlertFormatting.dayLabel(itemView.context, day)
        }
    }

    private object AlertDiff : DiffUtil.ItemCallback<AlertListItem>() {
        override fun areItemsTheSame(oldItem: AlertListItem, newItem: AlertListItem) =
            when {
                oldItem is AlertListItem.Header && newItem is AlertListItem.Header ->
                    oldItem.day == newItem.day
                oldItem is AlertListItem.Row && newItem is AlertListItem.Row ->
                    oldItem.row.alert.sequenceId == newItem.row.alert.sequenceId
                else -> false
            }

        override fun areContentsTheSame(oldItem: AlertListItem, newItem: AlertListItem) =
            oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_ALERT = 0
        const val VIEW_TYPE_DAY = 1
    }
}
