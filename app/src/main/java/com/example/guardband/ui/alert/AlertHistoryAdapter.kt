package com.example.guardband.ui.alert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R

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
 */
class AlertHistoryAdapter(
    private val timeStyle: AlertTimeStyle
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
            AlertViewHolder(inflater.inflate(R.layout.item_alert, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AlertListItem.Header -> (holder as DayViewHolder).bind(item.day)
            is AlertListItem.Row -> (holder as AlertViewHolder).bind(item.row, timeStyle)
        }
    }

    /** Binds one `item_alert.xml` row. */
    class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val views = AlertCardViews.ofItem(itemView)

        fun bind(row: AlertRow, style: AlertTimeStyle) = views.bind(row, style)
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
