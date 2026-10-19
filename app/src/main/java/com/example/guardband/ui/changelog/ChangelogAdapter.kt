package com.example.guardband.ui.changelog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * One card per [ChangelogEntry]: date, title, the changes as bullets, and the
 * commit it ends at.
 *
 * Dates are formatted with `SimpleDateFormat`, as the alert rows are, because
 * `java.time` needs API 26 and minSdk is 24. The parsers are main-thread
 * only, which is where `onBindViewHolder` runs.
 */
class ChangelogAdapter :
    ListAdapter<ChangelogEntry, ChangelogAdapter.ChangelogViewHolder>(ChangelogDiff) {

    private val isoParser = SimpleDateFormat(ISO_DATE, Locale.US).apply { isLenient = false }
    private val displayFormat = SimpleDateFormat(DISPLAY_DATE, Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChangelogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_changelog, parent, false)
        return ChangelogViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChangelogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChangelogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_item_changelog_date)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_item_changelog_title)
        private val tvChanges: TextView = itemView.findViewById(R.id.tv_item_changelog_changes)
        private val tvCommit: TextView = itemView.findViewById(R.id.tv_item_changelog_commit)

        fun bind(entry: ChangelogEntry) {
            tvDate.text = displayDate(entry.date)
            tvTitle.text = entry.title
            tvChanges.text = entry.changes.joinToString("\n") { "• $it" }
            tvCommit.text = itemView.context.getString(R.string.label_changelog_commit, entry.commit)
        }
    }

    /** Falls back to the ISO text rather than hiding a date it cannot parse. */
    private fun displayDate(iso: String): String = try {
        isoParser.parse(iso)?.let(displayFormat::format) ?: iso
    } catch (e: ParseException) {
        iso
    }

    private object ChangelogDiff : DiffUtil.ItemCallback<ChangelogEntry>() {
        override fun areItemsTheSame(old: ChangelogEntry, new: ChangelogEntry) =
            old.date == new.date
        override fun areContentsTheSame(old: ChangelogEntry, new: ChangelogEntry) =
            old == new
    }

    private companion object {
        const val ISO_DATE = "yyyy-MM-dd"
        const val DISPLAY_DATE = "d MMM yyyy"
    }
}
