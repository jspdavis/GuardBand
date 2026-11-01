package com.example.guardband.ui.guardian

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.contacts.ContactsViewModel
import com.example.guardband.ui.home.HomeViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Dependent's "Guardian" tab: shows their assigned guardian contact details,
 * direct call trigger, and panic SMS routing status.
 */
class GuardianDetailsFragment : Fragment(R.layout.fragment_guardian_details) {

    private lateinit var tvGuardianName: TextView
    private lateinit var tvGuardianRelationship: TextView
    private lateinit var tvGuardianPhone: TextView
    private lateinit var btnCallGuardian: MaterialButton

    private var currentPhone: String = ""

    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }
    private val contactsViewModel: ContactsViewModel by activityViewModels { ContactsViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_guardian_settings)
            .setOnClickListener { homeViewModel.onSettingsClicked() }
        view.findViewById<ImageButton>(R.id.ib_guardian_notifications)
            .setOnClickListener { homeViewModel.onNotificationsClicked() }

        tvGuardianName         = view.findViewById(R.id.tv_guardian_name)
        tvGuardianRelationship = view.findViewById(R.id.tv_guardian_relationship)
        tvGuardianPhone        = view.findViewById(R.id.tv_guardian_phone)
        btnCallGuardian        = view.findViewById(R.id.btn_call_guardian)

        btnCallGuardian.setOnClickListener {
            if (currentPhone.isNotBlank()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$currentPhone"))
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), R.string.label_no_guardian_assigned, Toast.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                contactsViewModel.uiState.collect { state ->
                    val guardian = state.contacts.find { it.isEmergency }
                        ?: state.contacts.find { it.relationship.equals("Guardian", ignoreCase = true) }
                        ?: state.contacts.firstOrNull()

                    if (guardian != null) {
                        currentPhone = guardian.phone
                        tvGuardianName.text = guardian.name
                        tvGuardianRelationship.text = if (guardian.isEmergency) {
                            "${guardian.relationship} · Primary Guardian (Emergency)"
                        } else {
                            guardian.relationship
                        }
                        tvGuardianPhone.text = guardian.phone
                        btnCallGuardian.isEnabled = true
                    } else {
                        tvGuardianName.text = getString(R.string.label_no_guardian_assigned)
                        tvGuardianRelationship.text = "Add in Contacts tab"
                        tvGuardianPhone.text = "None"
                        btnCallGuardian.isEnabled = false
                    }
                }
            }
        }
    }
}
