package com.example.guardband.data.model

/**
 * GuardBand user profile persisted in Realtime Database at users/{uid}.
 */
data class User(
    val uid: String = "",
    val email: String = "",
    val phone: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val location: String = "",
    val photoUrl: String = "",
    val profileComplete: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "email" to email,
        "phone" to phone,
        "firstName" to firstName,
        "lastName" to lastName,
        "location" to location,
        "photoUrl" to photoUrl,
        "profileComplete" to profileComplete,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(uid: String, data: Map<*, *>?): User {
            if (data == null) return User(uid = uid)
            return User(
                uid = uid,
                email = data["email"]?.toString().orEmpty(),
                phone = data["phone"]?.toString().orEmpty(),
                firstName = data["firstName"]?.toString().orEmpty(),
                lastName = data["lastName"]?.toString().orEmpty(),
                location = data["location"]?.toString().orEmpty(),
                photoUrl = data["photoUrl"]?.toString().orEmpty(),
                profileComplete = data["profileComplete"] as? Boolean ?: false,
                createdAt = (data["createdAt"] as? Number)?.toLong()
                    ?: System.currentTimeMillis()
            )
        }
    }
}
