package com.example.guardband.ui.contacts

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.google.android.material.chip.Chip

/**
 * Contact rows for the Contacts tab (`item_contact.xml`).
 *
 * The star button toggles the emergency designation ([EmergencyContact.isEmergency]).
 * When a contact is designated as emergency:
 *  - The star shows filled and amber.
 *  - The "Emergency" chip becomes visible on the row.
 *  - The contact sorts to the top of the list.
 *
 * Tapping the star calls [onEmergencyClicked]; the ViewModel flips the value
 * and the repository persists it via a single RTDB field write.
 */
class ContactAdapter(
    private val onEditClicked: (EmergencyContact) -> Unit,
    private val onDeleteClicked: (EmergencyContact) -> Unit,
    private val onEmergencyClicked: (EmergencyContact) -> Unit,
) : ListAdapter<EmergencyContact, ContactAdapter.ContactViewHolder>(ContactDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView         = itemView.findViewById(R.id.tv_item_contact_name)
        private val tvRelationship: TextView = itemView.findViewById(R.id.tv_item_contact_relationship)
        private val tvPhone: TextView        = itemView.findViewById(R.id.tv_item_contact_phone)
        private val chipEmergency: Chip      = itemView.findViewById(R.id.chip_item_contact_emergency)
        private val ibEmergency: ImageButton = itemView.findViewById(R.id.ib_item_contact_favorite)
        private val ibEdit: ImageButton      = itemView.findViewById(R.id.ib_item_contact_edit)
        private val ibDelete: ImageButton    = itemView.findViewById(R.id.ib_item_contact_delete)

        fun bind(contact: EmergencyContact) {
            tvName.text = contact.name
            tvRelationship.text = contact.relationship
            tvRelationship.visibility =
                if (contact.relationship.isBlank()) View.GONE else View.VISIBLE
            tvPhone.text = contact.phone

            // Emergency badge chip — visible only when designated.
            chipEmergency.visibility = if (contact.isEmergency) View.VISIBLE else View.GONE

            // Star: filled amber when emergency, outlined grey when not.
            if (contact.isEmergency) {
                ibEmergency.setImageResource(R.drawable.ic_star_filled)
                ibEmergency.imageTintList = ColorStateList.valueOf(
                    itemView.context.getColor(R.color.contact_favorite_tint)
                )
            } else {
                ibEmergency.setImageResource(R.drawable.ic_star)
                ibEmergency.imageTintList = ColorStateList.valueOf(
                    itemView.context.getColor(R.color.text_hint)
                )
            }

            ibEmergency.setOnClickListener { onEmergencyClicked(contact) }
            ibEdit.setOnClickListener      { onEditClicked(contact) }
            ibDelete.setOnClickListener    { onDeleteClicked(contact) }
        }
    }

    private object ContactDiff : DiffUtil.ItemCallback<EmergencyContact>() {
        override fun areItemsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact) =
            oldItem == newItem
    }
}
