package com.example.guardband.data.model

/**
 * An emergency contact associated with a user.
 *
 * Mirrors one record under `users/{uid}/emergency_contacts/{id}`. The field
 * names match the database's, so the repository stores and reads them without
 * a translation step.
 *
 * Every property defaults, because the Realtime Database SDK needs a no-arg
 * constructor to deserialize a snapshot.
 *
 * @param id           The record's key. Assigned by the repository on add, and
 *                     empty on a contact that has not been saved yet.
 * @param name         Contact's display name.
 * @param phone        Phone number in E.164, as
 *                     [normalizePhoneToE164][com.example.guardband.utils.InputValidator.normalizePhoneToE164]
 *                     produced it. Never logged: it is a third party's personal
 *                     data.
 * @param relationship Optional relationship label (e.g. "Friend", "Family").
 */
data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val relationship: String = ""
)
