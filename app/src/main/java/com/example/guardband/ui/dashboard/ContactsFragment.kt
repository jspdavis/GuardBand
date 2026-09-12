package com.example.guardband.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.ui.signup.EmergencyContactAdapter

class ContactsFragment : Fragment(), ContactsContract.View {

    private lateinit var presenter: ContactsPresenter
    private lateinit var adapter: EmergencyContactAdapter
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_contacts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvTitle).text = "Emergency Contacts"
        emptyView = view.findViewById(R.id.tvEmpty)
        recyclerView = view.findViewById(R.id.rvContacts)

        adapter = EmergencyContactAdapter { contact ->
            presenter.onDeleteContactClicked(contact.id)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        presenter = ContactsPresenter()
        presenter.attachView(this)
        presenter.loadContacts()
    }

    override fun onDestroyView() {
        presenter.detachView()
        super.onDestroyView()
    }

    // ── ContactsContract.View implementation ──────────────────────────────────

    override fun displayContacts(contacts: List<EmergencyContact>) {
        adapter.submit(contacts)
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    override fun showEmptyState() {
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    override fun showLoading() {
        // No-op - layout doesn't have a progress indicator
    }

    override fun hideLoading() {
        // No-op - layout doesn't have a progress indicator
    }

    override fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
