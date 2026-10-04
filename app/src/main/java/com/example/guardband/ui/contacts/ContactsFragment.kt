package com.example.guardband.ui.contacts

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Contacts tab: the emergency-contact list (moved here from the old
 * Dashboard), with delete behind a confirmation dialog.
 */
class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ContactAdapter

    private val viewModel: ContactsViewModel by viewModels { ContactsViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progress_contacts)
        tvEmpty     = view.findViewById(R.id.tv_contacts_empty)

        adapter = ContactAdapter(onDeleteClicked = ::confirmDelete)
        view.findViewById<RecyclerView>(R.id.rv_contacts).adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun render(state: ContactsUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        tvEmpty.visibility =
            if (!state.isLoading && state.contacts.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(state.contacts)
    }

    private fun handleEvent(event: ContactsEvent) {
        when (event) {
            is ContactsEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(contact: EmergencyContact) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_contact_title)
            .setMessage(getString(R.string.dialog_delete_contact_message, contact.name))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                viewModel.onDeleteConfirmed(contact.id)
            }
            .show()
    }
}
