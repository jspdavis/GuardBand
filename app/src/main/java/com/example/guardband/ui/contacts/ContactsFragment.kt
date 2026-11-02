package com.example.guardband.ui.contacts

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.ui.home.HomeViewModel
import com.example.guardband.utils.InputValidator
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Contacts tab: emergency-contact list with search, relationship filter chips,
 * add, edit and delete (FR-06).
 *
 * **Search and filter are purely in-memory.** The full contact list arrives
 * once from the repository and is held in [ContactsUiState.contacts].
 * [ContactsUiState.filteredContacts] applies both the active
 * [ContactsUiState.selectedRelationship] chip and the current
 * [ContactsUiState.query] without any additional database reads.
 *
 * The relationship field in the add/edit dialog is a Material
 * ExposedDropdownMenu with predefined options; the stored value is still a
 * plain String so existing records (possibly with free-text values) are never
 * broken.
 *
 * **The assigned guardian card** sits above the list: the first starred
 * contact ([ContactsUiState.guardian]) with a Call button that opens the
 * dialer. It replaced the Dependent's Guardian tab when roles were rolled back
 * (2026-11-02): anyone can be a guardian and a wearer at once, so the card is
 * for everyone.
 *
 * View ids: ib_contacts_settings, ib_contacts_notifications, tv_contacts_title,
 * et_contacts_search, ib_contacts_search_clear, contacts_chip_group,
 * cv_contacts_guardian, tv_contacts_guardian_title, tv_contacts_guardian_name,
 * tv_contacts_guardian_relationship, tv_contacts_guardian_phone,
 * tv_contacts_guardian_empty, btn_contacts_guardian_call, contacts_min_notice, progress_contacts, ll_contacts_empty, tv_contacts_empty,
 * rv_contacts, contacts_add_button.
 */
class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private lateinit var etSearch: EditText
    private lateinit var ibSearchClear: ImageButton
    private lateinit var chipGroup: ChipGroup
    private lateinit var progressBar: ProgressBar
    private lateinit var llEmpty: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvMinNotice: TextView
    private lateinit var btnAdd: FloatingActionButton
    private lateinit var cvGuardian: MaterialCardView
    private lateinit var tvGuardianName: TextView
    private lateinit var tvGuardianRelationship: TextView
    private lateinit var tvGuardianPhone: TextView
    private lateinit var tvGuardianEmpty: TextView
    private lateinit var btnCallGuardian: View
    private lateinit var adapter: ContactAdapter

    /**
     * Host-scoped since 2026-10-27, like [AlertViewModel][com.example.guardband.ui.alert.AlertViewModel]:
     * the Device tab reads the same instance for its contacts count, so there
     * is still one contacts listener. It ends with the host (Log Out).
     */
    private val viewModel: ContactsViewModel by activityViewModels { ContactsViewModel.Factory }
    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }

    /**
     * The chip tags and their corresponding filters, built once in
     * [buildFilterChips] and used in [render] to sync the selected state.
     */
    private val chipFilters = mutableListOf<Pair<Chip, ContactRelationshipFilter>>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_contacts_settings)
            .setOnClickListener { homeViewModel.onSettingsClicked() }
        view.findViewById<ImageButton>(R.id.ib_contacts_notifications)
            .setOnClickListener { homeViewModel.onNotificationsClicked() }

        etSearch      = view.findViewById(R.id.et_contacts_search)
        ibSearchClear = view.findViewById(R.id.ib_contacts_search_clear)
        chipGroup     = view.findViewById(R.id.contacts_chip_group)
        progressBar   = view.findViewById(R.id.progress_contacts)
        llEmpty       = view.findViewById(R.id.ll_contacts_empty)
        tvEmpty       = view.findViewById(R.id.tv_contacts_empty)
        tvMinNotice   = view.findViewById(R.id.contacts_min_notice)
        btnAdd        = view.findViewById(R.id.contacts_add_button)

        cvGuardian             = view.findViewById(R.id.cv_contacts_guardian)
        tvGuardianName         = view.findViewById(R.id.tv_contacts_guardian_name)
        tvGuardianRelationship = view.findViewById(R.id.tv_contacts_guardian_relationship)
        tvGuardianPhone        = view.findViewById(R.id.tv_contacts_guardian_phone)
        tvGuardianEmpty        = view.findViewById(R.id.tv_contacts_guardian_empty)
        btnCallGuardian        = view.findViewById(R.id.btn_contacts_guardian_call)
        btnCallGuardian.setOnClickListener { viewModel.onCallGuardianClicked() }

        adapter = ContactAdapter(
            onEditClicked      = viewModel::onEditClicked,
            onDeleteClicked    = ::confirmDelete,
            onEmergencyClicked = viewModel::onEmergencyToggled,
            onItemLongClicked  = ::confirmDelete
        )
        view.findViewById<RecyclerView>(R.id.rv_contacts).adapter = adapter

        buildFilterChips()
        wireSearch()

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

    // ── Filter chips ──────────────────────────────────────────────────────────

    /**
     * Inflates one [Chip] per filter category into [chipGroup] and registers
     * click listeners. All chips use the Material FilterChip style so the
     * selected one is visually distinguished by a checkmark and tint.
     */
    private fun buildFilterChips() {
        val filters: List<Pair<Int, ContactRelationshipFilter>> = listOf(
            R.string.label_contacts_filter_all       to ContactRelationshipFilter.All,
            R.string.label_contacts_filter_emergency to ContactRelationshipFilter.Emergency,
            R.string.label_contacts_filter_family    to ContactRelationshipFilter.Family,
            R.string.label_contacts_filter_parent    to ContactRelationshipFilter.Specific("Parent"),
            R.string.label_contacts_filter_sibling   to ContactRelationshipFilter.Specific("Sibling"),
            R.string.label_contacts_filter_relative  to ContactRelationshipFilter.Specific("Relative"),
            R.string.label_contacts_filter_friend    to ContactRelationshipFilter.Specific("Friend"),
            R.string.label_contacts_filter_partner   to ContactRelationshipFilter.Specific("Partner"),
            R.string.label_contacts_filter_guardian  to ContactRelationshipFilter.Specific("Guardian"),
        )

        filters.forEach { (stringRes, filter) ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_filter_chip, chipGroup, false) as Chip
            chip.text = getString(stringRes)
            // Start the "All" chip selected.
            chip.isChecked = filter is ContactRelationshipFilter.All
            chip.setOnClickListener { viewModel.onRelationshipFilterSelected(filter) }
            chipGroup.addView(chip)
            chipFilters += chip to filter
        }
    }

    /**
     * Keeps the chip selection in sync with [ContactsUiState.selectedRelationship].
     * Called from [render] so any external state change (e.g. process death
     * restore) also syncs correctly.
     */
    private fun syncChipSelection(selected: ContactRelationshipFilter) {
        chipFilters.forEach { (chip, filter) ->
            chip.isChecked = filter.label == selected.label
        }
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private fun wireSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                viewModel.onSearchChanged(query)
                ibSearchClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })

        ibSearchClear.setOnClickListener {
            etSearch.setText("")
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun render(state: ContactsUiState) {
        progressBar.visibility =
            if (state.isLoading || state.isMutating) View.VISIBLE else View.GONE
        btnAdd.isEnabled = !state.isMutating

        tvMinNotice.visibility = if (state.showMinimumNotice) View.VISIBLE else View.GONE
        if (state.showMinimumNotice) {
            tvMinNotice.text = getString(
                R.string.label_contacts_min_notice,
                state.contacts.size,
                InputValidator.MIN_CONTACTS
            )
        }

        renderGuardian(state)
        syncChipSelection(state.selectedRelationship)

        val reason = state.emptyReason
        llEmpty.visibility = if (reason != null) View.VISIBLE else View.GONE
        if (reason != null) {
            tvEmpty.text = when (reason) {
                ContactsEmptyReason.NO_CONTACTS ->
                    getString(R.string.label_contacts_empty)
                ContactsEmptyReason.NO_SEARCH_RESULTS ->
                    getString(R.string.label_contacts_empty_search)
                ContactsEmptyReason.NO_FILTER_RESULTS ->
                    getString(R.string.label_contacts_empty_filter)
            }
        }

        adapter.submitList(state.filteredContacts)
    }

    private fun renderGuardian(state: ContactsUiState) {
        cvGuardian.isVisible = state.showGuardianCard
        val guardian = state.guardian

        tvGuardianName.isVisible = guardian != null
        tvGuardianPhone.isVisible = guardian != null
        btnCallGuardian.isVisible = guardian != null
        tvGuardianEmpty.isVisible = guardian == null
        tvGuardianRelationship.isVisible = guardian?.relationship?.isNotBlank() == true

        if (guardian != null) {
            tvGuardianName.text = guardian.name
            tvGuardianRelationship.text = guardian.relationship
            tvGuardianPhone.text = guardian.phone
            btnCallGuardian.contentDescription =
                getString(R.string.cd_contacts_guardian_call, guardian.name)
        }
    }

    private fun handleEvent(event: ContactsEvent) {
        when (event) {
            is ContactsEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()

            is ContactsEvent.ShowContactEditor -> showEditor(event.contact)

            is ContactsEvent.Dial -> dial(event.phone)
        }
    }

    /** ACTION_DIAL fills the number in; the user places the call, so no permission. */
    private fun dial(phone: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
        } catch (e: ActivityNotFoundException) {
            viewModel.onDialerUnavailable()
        }
    }

    // ── Add / edit dialog ────────────────────────────────────────────────

    private fun showEditor(contact: EmergencyContact?) {
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_contact_editor, null)

        val etName = content.findViewById<TextInputEditText>(R.id.contact_name_input)
        val etPhone = content.findViewById<TextInputEditText>(R.id.contact_phone_input)
        val acRelationship =
            content.findViewById<android.widget.AutoCompleteTextView>(
                R.id.contact_relationship_input
            )

        val relationshipOptions = listOf(
            getString(R.string.contact_relationship_parent),
            getString(R.string.contact_relationship_sibling),
            getString(R.string.contact_relationship_relative),
            getString(R.string.contact_relationship_friend),
            getString(R.string.contact_relationship_partner),
            getString(R.string.contact_relationship_guardian),
            getString(R.string.contact_relationship_other),
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            relationshipOptions
        )
        acRelationship.setAdapter(adapter)

        etName.setText(contact?.name.orEmpty())
        etPhone.setText(contact?.phone.orEmpty())

        val storedRelationship = contact?.relationship.orEmpty()
        val matchedOption = relationshipOptions.find {
            it.equals(storedRelationship, ignoreCase = true)
        }
        if (matchedOption != null) {
            acRelationship.setText(matchedOption, false)
        }

        val tvTitle = content.findViewById<TextView>(R.id.tv_dialog_contact_title)
        val btnCancel = content.findViewById<View>(R.id.btn_dialog_contact_cancel)
        val btnSave = content.findViewById<View>(R.id.btn_dialog_contact_save)

        tvTitle.setText(
            if (contact == null) R.string.dialog_contact_add_title
            else R.string.dialog_contact_edit_title
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(content)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            viewModel.onEditorSubmitted(
                contactId    = contact?.id.orEmpty(),
                name         = etName.text.toString(),
                phone        = etPhone.text.toString(),
                relationship = acRelationship.text.toString()
            )
            dialog.dismiss()
        }

        // Remove the default system window background overlay so the card's surface and geometry shine through
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.show()
    }

    // ── Delete confirmation ───────────────────────────────────────────────────

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