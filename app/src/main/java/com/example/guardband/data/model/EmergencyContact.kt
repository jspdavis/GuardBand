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
        fun fromMap(id: String, data: Map<*, *>?): EmergencyContact {
            if (data == null) return EmergencyContact(id = id)
            return EmergencyContact(
                id = id,
                name = data["name"]?.toString().orEmpty(),
                phoneNumber = data["phoneNumber"]?.toString().orEmpty(),
                relationship = data["relationship"]?.toString().orEmpty()
            )
        }
    }
}
