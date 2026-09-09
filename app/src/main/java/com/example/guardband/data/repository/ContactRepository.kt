package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

/**
 * Firebase Realtime Database access for emergency contacts.
 *
 * All data is stored under the RTDB path:
 *   users/{uid}/emergency_contacts/{contactId}
 *
 * This repository exclusively uses [DatabaseManager.database] — no separate
 * FirebaseDatabase instance is constructed anywhere else in the app.
 */
class ContactRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val dbManager: DatabaseManager = DatabaseManager
) {

    private fun contactsRef(uid: String) =
        dbManager.database.getReference("users/$uid/$NODE_CONTACTS")

    fun getContacts(
        onSuccess: (List<EmergencyContact>) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onError("Not signed in.")
        contactsRef(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val data = child.value as? Map<*, *> ?: return@mapNotNull null
                    EmergencyContact.fromMap(child.key ?: "", data.mapKeys { it.key.toString() })
                }
                onSuccess(list)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        })
    }

    fun addContact(
        contact: EmergencyContact,
        onSuccess: (EmergencyContact) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onError("Not signed in.")
        val ref = contactsRef(uid).push()
        val saved = contact.copy(id = ref.key ?: contact.id)
        ref.setValue(saved.toMap())
            .addOnSuccessListener { onSuccess(saved) }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Failed to add contact.") }
    }

    fun deleteContact(
        contactId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onError("Not signed in.")
        contactsRef(uid).child(contactId).removeValue()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Failed to delete contact.") }
    }

    fun replaceAll(
        contacts: List<EmergencyContact>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onError("Not signed in.")
        val ref = contactsRef(uid)

        // Build a map of {pushKey -> contactMap} for a single atomic write.
        val payload = mutableMapOf<String, Any?>()
        contacts.forEach { contact ->
            val key = if (contact.id.isBlank() || contact.id.startsWith("local-")) {
                ref.push().key ?: return@forEach
            } else {
                contact.id
            }
            payload[key] = contact.copy(id = key).toMap()
        }

        ref.setValue(payload)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Failed to save contacts.") }
    }

    companion object {
        private const val NODE_CONTACTS = "emergency_contacts"

        @Volatile
        private var instance: ContactRepository? = null

        fun getInstance(): ContactRepository =
            instance ?: synchronized(this) {
                instance ?: ContactRepository().also { instance = it }
            }
    }
}
