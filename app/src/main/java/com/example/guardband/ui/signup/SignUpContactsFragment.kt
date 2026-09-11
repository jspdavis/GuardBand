package com.example.guardband.ui.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SignUpContactsFragment : Fragment() {

    interface Host {
        fun onAddContact(name: String, phone: String, relationship: String)
        fun onDeleteContact(contactId: String)
        fun onContactsContinue()
    }

    private var host: Host? = null
    private lateinit var adapter: EmergencyContactAdapter
    private lateinit var btnAction: MaterialButton
    private lateinit var layoutAddForm: View
    private lateinit var tvAddNewContact: TextView

    private val relationships = listOf(
        "Family", "Father", "Mother", "Sibling", "Spouse", "Friend", "Partner", "Other"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_signup_contacts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        host = activity as? Host

        btnAction = view.findViewById(R.id.btnAction)
        layoutAddForm = view.findViewById(R.id.layoutAddForm)
        tvAddNewContact = view.findViewById(R.id.tvAddNewContact)

        val spinner = view.findViewById<Spinner>(R.id.spinnerRelationship)
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            relationships
        )

        adapter = EmergencyContactAdapter { contact ->
            host?.onDeleteContact(contact.id)
        }
        val rv = view.findViewById<RecyclerView>(R.id.rvContacts)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        tvAddNewContact.setOnClickListener {
            layoutAddForm.visibility = View.VISIBLE
            btnAction.text = "ADD"
        }

        btnAction.setOnClickListener {
            if (btnAction.text.toString().equals("CONTINUE", ignoreCase = true) &&
                layoutAddForm.visibility != View.VISIBLE
            ) {
                host?.onContactsContinue()
            } else {
                host?.onAddContact(
                    name = view.findViewById<TextInputEditText>(R.id.etName).text?.toString().orEmpty(),
                    phone = view.findViewById<TextInputEditText>(R.id.etPhone).text?.toString().orEmpty(),
                    relationship = spinner.selectedItem?.toString().orEmpty()
                )
            }
        }
    }

    fun renderContacts(contacts: List<EmergencyContact>) {
        if (!::adapter.isInitialized || view == null) return
        adapter.submit(contacts)
        if (contacts.isEmpty()) {
            layoutAddForm.visibility = View.VISIBLE
            tvAddNewContact.visibility = View.GONE
            btnAction.text = "ADD"
        } else {
            layoutAddForm.visibility = View.GONE
            tvAddNewContact.visibility = View.VISIBLE
            btnAction.text = "CONTINUE"
        }
    }

    fun clearForm() {
        view?.findViewById<TextInputEditText>(R.id.etName)?.setText("")
        view?.findViewById<TextInputEditText>(R.id.etPhone)?.setText("")
        view?.findViewById<TextInputLayout>(R.id.tilName)?.error = null
        view?.findViewById<TextInputLayout>(R.id.tilPhone)?.error = null
    }

    fun showFieldError(field: String, message: String?) {
        val v = view ?: return
        when (field) {
            "contactName" -> v.findViewById<TextInputLayout>(R.id.tilName).error = message
            "contactPhone" -> v.findViewById<TextInputLayout>(R.id.tilPhone).error = message
            "relationship" -> { /* spinner — toast via host */ }
        }
    }
}
