package com.example.guardband.data.model

/**
 * Emergency contact stored under users/{uid}/emergency_contacts/{contactId}.
 */
data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "phoneNumber" to phoneNumber,
        "relationship" to relationship
    )

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>?): EmergencyContact {
            if (data == null) return EmergencyContact(id = id)
            return EmergencyContact(
                id = id,
                name = data["name"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String ?: "",
                relationship = data["relationship"] as? String ?: ""
            )
        }
    }
}
