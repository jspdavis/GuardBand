package com.example.guardband.data.model

/**
 * An emergency contact associated with a user.
 *
 * @param id           Unique contact identifier.
 * @param name         Contact's display name.
 * @param phoneNumber  Contact's phone number.
 * @param relationship Relationship label (e.g. "Friend", "Family").
 */
data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = ""
)
