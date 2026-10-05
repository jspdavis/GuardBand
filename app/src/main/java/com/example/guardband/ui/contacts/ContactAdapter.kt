package com.example.guardband.ui.contacts

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

/**
 * Emergency-contact rows for the Contacts tab (`item_contact.xml`).
 *
 * Forwards edit taps to [onEditClicked] and delete taps to [onDeleteClicked].
 * The Fragment opens the editor and confirms the delete; neither decision is
 * made here.
 */
class ContactAdapter(
    private val onEditClicked: (EmergencyContact) -> Unit,
    private val onDeleteClicked: (EmergencyContact) -> Unit
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
        private val tvName: TextView = itemView.findViewById(R.id.tv_item_contact_name)
        private val tvRelationship: TextView = itemView.findViewById(R.id.tv_item_contact_relationship)
        private val tvPhone: TextView = itemView.findViewById(R.id.tv_item_contact_phone)
        private val ibEdit: ImageButton = itemView.findViewById(R.id.ib_item_contact_edit)
        private val ibDelete: ImageButton = itemView.findViewById(R.id.ib_item_contact_delete)

        fun bind(contact: EmergencyContact) {
            tvName.text = contact.name
            tvRelationship.text = contact.relationship
            tvRelationship.visibility = if (contact.relationship.isBlank()) View.GONE else View.VISIBLE
            tvPhone.text = contact.phone
            ibEdit.setOnClickListener { onEditClicked(contact) }
            ibDelete.setOnClickListener { onDeleteClicked(contact) }
        }
    }

    private object ContactDiff : DiffUtil.ItemCallback<EmergencyContact>() {
        override fun areItemsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact) =
            oldItem == newItem
    }
}
