package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.ui.contacts.ContactAdapter
import com.example.guardband.utils.InputValidator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Sign-Up Step 3 of 3 — the emergency contacts.
 *
 * Receives [EXTRA_NAME], [EXTRA_EMAIL] and [EXTRA_PASSWORD] from the earlier
 * steps, or [EXTRA_NAME], [EXTRA_EMAIL] and [EXTRA_COMPLETE_PROFILE] straight
 * from Login. All of it is passed on to Consent, which is where anything is
 * written.
 *
 * View ids: tv_signup_contacts_title, tv_signup_contacts_subtitle,
 * signup_contacts_min_notice, tv_signup_contacts_empty, rv_signup_contacts,
 * btn_signup_contacts_add, btn_signup_contacts_next. Rows come from
 * `item_contact.xml` and the editor from `dialog_contact_editor.xml` — both
 * shared with the Contacts tab. The back arrow and step indicator come from
 * the shared header; see [SignUpStepHeader]. In complete-profile mode the
 * indicator is hidden, because this is then the only step.
 *
 * Flow: SignUpContactsActivity → SignUpConsentActivity
 */
class SignUpContactsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "extra_name"

        /** From step 1, or the Google address in complete-profile mode. */
        const val EXTRA_EMAIL = "extra_email"

        /** From step 1. Absent in complete-profile mode, where no account is made. */
        const val EXTRA_PASSWORD = "extra_password"

        /** True when a Google sign-in already created the account. */
        const val EXTRA_COMPLETE_PROFILE = "extra_complete_profile"
    }

    private lateinit var tvMinNotice: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnAdd: Button
    private lateinit var btnNext: Button
    private lateinit var adapter: ContactAdapter

    private val viewModel: SignUpContactsViewModel by viewModels()

    private var userName: String = ""
    private var email: String = ""
    private var password: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_contacts)

        userName = intent.getStringExtra(EXTRA_NAME) ?: ""
        email    = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""

        val completeProfile = intent.getBooleanExtra(EXTRA_COMPLETE_PROFILE, false)
        viewModel.setCompleteProfileMode(completeProfile)

        // A first-time Google user skips steps 1 and 2, so this is the only
        // step they see - counting it as 3 of 3 would misreport where they are.
        if (completeProfile) SignUpStepHeader.bindBackOnly(this)
        else SignUpStepHeader.bind(this, step = 3)

        tvMinNotice = findViewById(R.id.signup_contacts_min_notice)
        tvEmpty     = findViewById(R.id.tv_signup_contacts_empty)
        btnAdd      = findViewById(R.id.btn_signup_contacts_add)
        btnNext     = findViewById(R.id.btn_signup_contacts_next)

        adapter = ContactAdapter(
            onEditClicked = viewModel::onEditClicked,
            onDeleteClicked = { viewModel.onDeleteClicked(it.id) }
        )
        findViewById<RecyclerView>(R.id.rv_signup_contacts).adapter = adapter

        btnAdd.setOnClickListener { viewModel.onAddClicked() }
        btnNext.setOnClickListener {
            viewModel.onContinueClicked(name = userName, email = email, password = password)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun render(state: SignUpContactsUiState) {
        btnNext.isEnabled = state.canContinue

        tvEmpty.visibility = if (state.contacts.isEmpty()) View.VISIBLE else View.GONE

        tvMinNotice.visibility = if (state.showMinimumNotice) View.VISIBLE else View.GONE
        if (state.showMinimumNotice) {
            tvMinNotice.text = getString(
                R.string.label_signup_contacts_minimum,
                state.contacts.size,
                InputValidator.MIN_CONTACTS
            )
        }

        adapter.submitList(state.contacts)
    }

    private fun handleEvent(event: SignUpContactsEvent) {
        when (event) {
            is SignUpContactsEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            is SignUpContactsEvent.ShowContactEditor -> showEditor(event.contact)

            is SignUpContactsEvent.NavigateToConsent ->
                startActivity(
                    Intent(this, SignUpConsentActivity::class.java).apply {
                        putExtra(SignUpConsentActivity.EXTRA_NAME, event.name)
                        putExtra(SignUpConsentActivity.EXTRA_EMAIL, event.email)
                        putExtra(SignUpConsentActivity.EXTRA_PASSWORD, event.password)
                        putExtra(
                            SignUpConsentActivity.EXTRA_COMPLETE_PROFILE,
                            event.completeProfile
                        )
                        putParcelableArrayListExtra(
                            SignUpConsentActivity.EXTRA_CONTACTS,
                            ArrayList(event.contacts)
                        )
                    }
                )
        }
    }

    /**
     * The same add/edit dialog the Contacts tab uses. A null [contact] means
     * add; otherwise the fields start on the staged values and the draft id
     * rides back so the ViewModel replaces rather than appends.
     */
    private fun showEditor(contact: EmergencyContact?) {
        val content = LayoutInflater.from(this)
            .inflate(R.layout.dialog_contact_editor, null)

        val etName = content.findViewById<TextInputEditText>(R.id.contact_name_input)
        val etPhone = content.findViewById<TextInputEditText>(R.id.contact_phone_input)
        val etRelationship =
            content.findViewById<TextInputEditText>(R.id.contact_relationship_input)

        etName.setText(contact?.name.orEmpty())
        etPhone.setText(contact?.phone.orEmpty())
        etRelationship.setText(contact?.relationship.orEmpty())

        MaterialAlertDialogBuilder(this)
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
}
