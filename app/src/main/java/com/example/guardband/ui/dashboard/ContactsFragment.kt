package com.example.guardband.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.ui.signup.EmergencyContactAdapter

class ContactsFragment : Fragment() {

    private lateinit var adapter: EmergencyContactAdapter
    private lateinit var emptyView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_contacts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvTitle).text = "Emergency Contacts"
        emptyView = view.findViewById(R.id.tvEmpty)

        adapter = EmergencyContactAdapter { contact ->
            ContactRepository.getInstance().deleteContact(
                contactId = contact.id,
                onSuccess = { loadContacts() },
                onError = {}
            )
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvContacts)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        loadContacts()
    }

    private fun loadContacts() {
        ContactRepository.getInstance().getContacts(
            onSuccess = { list ->
                adapter.submit(list)
                emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            },
            onError = {
                emptyView.visibility = View.VISIBLE
            }
        )
    }
}
