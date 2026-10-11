package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.utils.InputValidator
import com.google.firebase.database.DataSnapshot

/**
 * The on-disk shape of one `users/{uid}/emergency_contacts/{id}` record, in one
 * place.
 *
 * Shared by [FirebaseContactRepository], which writes contacts one at a time,
 * and [FirebaseUserProfileRepository.finalizeSignUp], which writes them in the
 * same batch as the profile. Both must agree on the field names and on what a
 * valid contact is, so neither owns them.
 *
 * Nothing here logs. A contact's phone number is a third party's personal data.
 */
internal object ContactFields {

    const val NODE_EMERGENCY_CONTACTS = "emergency_contacts"

    const val FIELD_NAME = "name"
    const val FIELD_PHONE = "phone"
    const val FIELD_RELATIONSHIP = "relationship"

    /**
     * [contact] trimmed and with its phone in E.164, ready to store.
     *
     * @throws ContactError.Validation if the name, phone or relationship fails
     *   [InputValidator]. Throwing rather than returning null because every
     *   caller is already inside a `Result`-producing wrapper.
     */
    fun validated(contact: EmergencyContact): EmergencyContact {
        if (!InputValidator.isValidContactName(contact.name)) throw ContactError.Validation
        if (!InputValidator.isValidContactRelationship(contact.relationship)) {
            throw ContactError.Validation
        }
        val phone = InputValidator.normalizePhoneToE164(contact.phone)
            ?: throw ContactError.Validation

        return contact.copy(
            name = contact.name.trim(),
            phone = phone,
            relationship = contact.relationship.trim()
        )
    }

    /** The three stored fields of an already-[validated] contact. */
    fun toFieldMap(contact: EmergencyContact): Map<String, Any> = mapOf(
        FIELD_NAME to contact.name,
        FIELD_PHONE to contact.phone,
        FIELD_RELATIONSHIP to contact.relationship
    )

    /**
     * Every contact under an `emergency_contacts` snapshot, sorted by name.
     *
     * The database returns children in key order, which for push ids means
     * oldest first. Sorting by name instead gives the list a stable order the
     * user can predict, and keeps `DiffUtil` from animating rows around when an
     * unrelated contact changes. The id breaks ties so the order is total.
     *
     * Field values are read through `toString` rather than
     * `getValue(String::class.java)`, which throws on a value of the wrong
     * type. A record written by hand in the Console with a numeric phone should
     * render as text, not take down the listener.
     */
    fun fromSnapshot(snapshot: DataSnapshot): List<EmergencyContact> =
        snapshot.children
            .mapNotNull { child ->
                val id = child.key ?: return@mapNotNull null
                EmergencyContact(
                    id = id,
                    name = child.child(FIELD_NAME).value?.toString().orEmpty(),
                    phone = child.child(FIELD_PHONE).value?.toString().orEmpty(),
                    relationship = child.child(FIELD_RELATIONSHIP).value?.toString().orEmpty()
                )
            }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
}
