package com.example.guardband.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
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
import com.example.guardband.utils.InputValidator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Contacts tab: the emergency-contact list with add, edit and delete (FR-06).
 *
 * View ids: tv_contacts_title, contacts_min_notice, progress_contacts,
 * tv_contacts_empty, rv_contacts, contacts_add_button. Rows add
 * ib_item_contact_edit beside ib_item_contact_delete, and the editor dialog
 * (`dialog_contact_editor.xml`) holds contact_name_input, contact_phone_input
 * and contact_relationship_input.
 */
class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvMinNotice: TextView
    private lateinit var btnAdd: Button
    private lateinit var adapter: ContactAdapter

    private val viewModel: ContactsViewModel by viewModels { ContactsViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar  = view.findViewById(R.id.progress_contacts)
        tvEmpty      = view.findViewById(R.id.tv_contacts_empty)
        tvMinNotice  = view.findViewById(R.id.contacts_min_notice)
        btnAdd       = view.findViewById(R.id.contacts_add_button)

        adapter = ContactAdapter(
            onEditClicked = viewModel::onEditClicked,
            onDeleteClicked = ::confirmDelete
        )
        view.findViewById<RecyclerView>(R.id.rv_contacts).adapter = adapter

        btnAdd.setOnClickListener { viewModel.onAddClicked() }

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
        progressBar.visibility = if (state.isLoading || state.isMutating) View.VISIBLE else View.GONE
        tvEmpty.visibility = if (state.showEmpty) View.VISIBLE else View.GONE
        btnAdd.isEnabled = !state.isMutating

        tvMinNotice.visibility = if (state.showMinimumNotice) View.VISIBLE else View.GONE
        if (state.showMinimumNotice) {
            tvMinNotice.text = getString(
                R.string.label_contacts_min_notice,
                state.contacts.size,
                InputValidator.MIN_CONTACTS
            )
        }

        adapter.submitList(state.contacts)
    }

    private fun handleEvent(event: ContactsEvent) {
        when (event) {
            is ContactsEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()

            is ContactsEvent.ShowContactEditor -> showEditor(event.contact)
        }
    }

    /**
     * The one add/edit dialog (D6). A null [contact] means add; otherwise the
     * fields start on the stored values and the id rides back to the ViewModel
     * so it knows to update rather than insert.
     */
    private fun showEditor(contact: EmergencyContact?) {
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_contact_editor, null)

        val etName = content.findViewById<TextInputEditText>(R.id.contact_name_input)
        val etPhone = content.findViewById<TextInputEditText>(R.id.contact_phone_input)
        val etRelationship =
            content.findViewById<TextInputEditText>(R.id.contact_relationship_input)

        etName.setText(contact?.name.orEmpty())
        etPhone.setText(contact?.phone.orEmpty())
        etRelationship.setText(contact?.relationship.orEmpty())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (contact == null) R.string.dialog_contact_add_title
                else R.string.dialog_contact_edit_title
            )
            .setView(content)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                viewModel.onEditorSubmitted(
                    contactId = contact?.id.orEmpty(),
                    name = etName.text.toString(),
                    phone = etPhone.text.toString(),
                    relationship = etRelationship.text.toString()
                )
            }
            .show()
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
