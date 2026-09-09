package com.example.guardband.ui.signup

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact

class EmergencyContactAdapter(
    private val onDelete: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<EmergencyContactAdapter.VH>() {

    private val items = mutableListOf<EmergencyContact>()

    fun submit(list: List<EmergencyContact>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_contact, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.relation.text = item.relationship
        holder.phone.text = item.phoneNumber
        holder.delete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvContactName)
        val relation: TextView = view.findViewById(R.id.tvContactRelation)
        val phone: TextView = view.findViewById(R.id.tvContactPhone)
        val delete: ImageView = view.findViewById(R.id.btnDelete)
    }
}
