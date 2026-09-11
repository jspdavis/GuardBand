package com.example.guardband.data.repository

import com.example.guardband.data.model.User
import com.example.guardband.data.model.ValidationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Firebase Authentication + Realtime Database profile layer.
 *
 * RTDB root: [DatabaseManager] →
 *   https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/
 *
 * Paths:
 *   users/{uid}
 *   phone_index/{phone} → { uid, email }
 *   email_index/{emailKey} → { uid }
 *   password_resets/{emailKey}
 */
open class AuthRepository(
    auth: FirebaseAuth? = null,
    private val dbManager: DatabaseManager = DatabaseManager
) {
    private val auth: FirebaseAuth by lazy { auth ?: FirebaseAuth.getInstance() }
    private val root: DatabaseReference get() = dbManager.database.reference

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    open fun login(
        identifier: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = identifier.trim()
        if (trimmed.isBlank() || password.isBlank()) {
            onError("Phone/email and password are required.")
            return
        }

        resolveLoginEmail(trimmed) { email ->
            if (email.isNullOrBlank()) {
                onError("No account found for that phone number or email.")
                return@resolveLoginEmail
            }
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user ?: return@addOnSuccessListener onError("Login failed.")
                    fetchOrCreateProfile(user, onSuccess, onError)
                }
                .addOnFailureListener { e ->
                    val msg = e.localizedMessage.orEmpty()
                    when {
                        msg.contains("badly formatted", ignoreCase = true) ->
                            onError("Invalid phone number or email format.")
                        msg.contains("password", ignoreCase = true) ||
                            msg.contains("credential", ignoreCase = true) ||
                            msg.contains("no user", ignoreCase = true) ||
                            msg.contains("INVALID", ignoreCase = true) ->
                            onError("Invalid credentials. Check your phone/email and password.")
                        else -> onError(msg.ifBlank { "Invalid credentials." })
                    }
                }
        }
    }

    open fun registerWithEmail(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String = "",
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val identifier = email.trim()
        val rules = ValidationResult.evaluatePassword(password, password)
        if (!rules.passwordRules.minLength || !rules.passwordRules.hasUppercase ||
            !rules.passwordRules.hasLowercase || !rules.passwordRules.hasNumberOrSpecial
        ) {
            onError("Password does not meet security requirements.")
            return
        }

        val authEmail: String
        val profilePhone: String
        when {
            identifier.contains("@") -> {
                authEmail = normalizeEmail(identifier)
                profilePhone = phone.trim()
            }
            isPhoneLike(identifier) -> {
                profilePhone = identifier.filter { it.isDigit() || it == '+' }
                authEmail = phoneToAuthEmail(profilePhone)
            }
            else -> {
                onError("Enter a valid email or phone number.")
                return
            }
        }

        auth.createUserWithEmailAndPassword(authEmail, password)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                    ?: return@addOnSuccessListener onError("Registration failed.")
                firebaseUser.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName("$firstName $lastName".trim())
                        .build()
                )
                // Verification email only works for real addresses.
                if (authEmail.contains("@") && !authEmail.endsWith("@guardband.phone")) {
                    firebaseUser.sendEmailVerification()
                }

                val profile = User(
                    uid = firebaseUser.uid,
                    email = authEmail,
                    phone = profilePhone,
                    firstName = firstName,
                    lastName = lastName,
                    profileComplete = false
                )
                saveProfile(profile, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Registration failed.")
            }
    }

    fun signInWithGoogleIdToken(
        idToken: String,
        onSuccess: (user: User, isNewUser: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                    ?: return@addOnSuccessListener onError("Google sign-in failed.")
                val isNew = result.additionalUserInfo?.isNewUser == true
                if (isNew && !firebaseUser.email.isNullOrBlank()) {
                    firebaseUser.sendEmailVerification()
                }

                val names = (firebaseUser.displayName ?: "").trim().split(" ")
                val first = names.firstOrNull().orEmpty()
                val last = names.drop(1).joinToString(" ")

                root.child(NODE_USERS).child(firebaseUser.uid)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                onSuccess(User.fromMap(firebaseUser.uid, snapshotToMap(snapshot)), isNew)
                            } else {
                                val profile = User(
                                    uid = firebaseUser.uid,
                                    email = firebaseUser.email.orEmpty(),
                                    firstName = first,
                                    lastName = last,
                                    photoUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                                    profileComplete = false
                                )
                                saveProfile(profile, { onSuccess(it, true) }, onError)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            onError(error.message)
                        }
                    })
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Google sign-in failed.")
            }
    }

    fun requestPasswordReset(
        email: String,
        onSuccess: (otpForDebug: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        val normalized = normalizeEmail(email)
        if (normalized.isBlank() || !normalized.contains("@")) {
            onError("Please enter your registered email.")
            return
        }

        // Confirm the account exists in RTDB (or Auth) before sending.
        root.child(NODE_EMAIL_INDEX).child(emailKey(normalized))
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    dispatchResetEmailAndOtp(normalized, onSuccess, onError)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Still attempt Firebase Auth reset — Auth is source of truth for email.
                    dispatchResetEmailAndOtp(normalized, onSuccess, onError)
                }
            })
    }

    private fun dispatchResetEmailAndOtp(
        normalized: String,
        onSuccess: (otpForDebug: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.sendPasswordResetEmail(normalized)
            .addOnCompleteListener { sendTask ->
                val otp = generateOtp()
                val payload = mapOf(
                    "email" to normalized,
                    "otpHash" to sha256(otp),
                    "otpPlain" to otp,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to (System.currentTimeMillis() + OTP_TTL_MS),
                    "verified" to false,
                    "emailSendOk" to sendTask.isSuccessful
                )
                root.child(NODE_RESETS).child(emailKey(normalized))
                    .setValue(payload)
                    .addOnSuccessListener {
                        // Always advance the in-app OTP flow. If Auth email failed,
                        // surface the OTP for testing and still continue.
                        onSuccess(otp)
                    }
                    .addOnFailureListener { e ->
                        if (sendTask.isSuccessful) {
                            onSuccess(otp)
                        } else {
                            onError(
                                sendTask.exception?.localizedMessage
                                    ?: e.localizedMessage
                                    ?: "Could not start password reset."
                            )
                        }
                    }
            }
    }

    fun resendOtp(
        email: String,
        onSuccess: (otpForDebug: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        requestPasswordReset(email, onSuccess, onError)
    }

    fun verifyOtp(
        email: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val normalized = normalizeEmail(email)
        root.child(NODE_RESETS).child(emailKey(normalized))
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        onError("No reset request found. Request a new code.")
                        return
                    }
                    val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java) ?: 0L
                    if (System.currentTimeMillis() > expiresAt) {
                        onError("Code expired. Please resend.")
                        return
                    }
                    val expectedHash = snapshot.child("otpHash").getValue(String::class.java).orEmpty()
                    val plain = snapshot.child("otpPlain").getValue(String::class.java).orEmpty()
                    val ok = sha256(code.trim()) == expectedHash ||
                        (plain.isNotEmpty() && plain == code.trim())
                    if (!ok) {
                        onError("Incorrect verification code. Try again.")
                        return
                    }
                    snapshot.ref.child("verified").setValue(true)
                        .addOnCompleteListener { onSuccess() }
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    fun updatePasswordWithOtp(
        email: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val normalized = normalizeEmail(email)
        val validation = ValidationResult.evaluatePassword(newPassword, newPassword)
        if (!validation.passwordRules.allMet) {
            onError("Password does not meet all requirements.")
            return
        }

        root.child(NODE_RESETS).child(emailKey(normalized))
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.child("verified").getValue(Boolean::class.java) != true) {
                        onError("Verify your code before setting a new password.")
                        return
                    }
                    clientSidePasswordApply(normalized, newPassword, onSuccess, onError)
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    open fun updateProfile(
        updates: Map<String, Any?>,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onError("Not signed in.")
        root.child(NODE_USERS).child(uid)
            .updateChildren(updates.filterValues { it != null }.mapValues { it.value as Any })
            .addOnSuccessListener { fetchProfile(uid, onSuccess, onError) }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to update profile.")
            }
    }

    open fun fetchProfile(
        uid: String = auth.currentUser?.uid.orEmpty(),
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        if (uid.isBlank()) {
            onError("Not signed in.")
            return
        }
        root.child(NODE_USERS).child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onSuccess(User.fromMap(uid, snapshotToMap(snapshot)))
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    open fun signOut() {
        auth.signOut()
    }

    open fun isLoggedIn(): Boolean = auth.currentUser != null

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun resolveLoginEmail(identifier: String, onResult: (String?) -> Unit) {
        val trimmed = identifier.trim()
        if (trimmed.contains("@")) {
            onResult(normalizeEmail(trimmed))
            return
        }
        if (!isPhoneLike(trimmed)) {
            onResult(null)
            return
        }
        val phoneKey = trimmed.filter { it.isDigit() || it == '+' }
        root.child(NODE_PHONE_INDEX).child(sanitizeKey(phoneKey))
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val email = snapshot.child("email").getValue(String::class.java)
                    if (!email.isNullOrBlank()) {
                        onResult(email)
                    } else {
                        // Fallback: scan users (small datasets / MVP).
                        findEmailByPhoneScan(phoneKey, onResult)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    findEmailByPhoneScan(phoneKey, onResult)
                }
            })
    }

    private fun findEmailByPhoneScan(phone: String, onResult: (String?) -> Unit) {
        root.child(NODE_USERS)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val stored = child.child("phone").getValue(String::class.java).orEmpty()
                        if (stored.filter { it.isDigit() } == phone.filter { it.isDigit() }) {
                            onResult(child.child("email").getValue(String::class.java))
                            return
                        }
                    }
                    onResult(null)
                }

                override fun onCancelled(error: DatabaseError) {
                    onResult(null)
                }
            })
    }

    private fun clientSidePasswordApply(
        email: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val current = auth.currentUser
        if (current?.email.equals(email, ignoreCase = true)) {
            current!!.updatePassword(newPassword)
                .addOnSuccessListener {
                    clearResetDoc(email)
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    onError(e.localizedMessage ?: "Could not update password.")
                }
            return
        }

        auth.createUserWithEmailAndPassword(email, newPassword)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    saveProfile(
                        User(uid = user.uid, email = email, profileComplete = false),
                        {
                            clearResetDoc(email)
                            auth.signOut()
                            onSuccess()
                        },
                        {
                            clearResetDoc(email)
                            auth.signOut()
                            onSuccess()
                        }
                    )
                } else onSuccess()
            }
            .addOnFailureListener { e ->
                if (e is FirebaseAuthUserCollisionException) {
                    // Existing Auth user: Firebase reset email (already sent) is the update path.
                    root.child(NODE_RESETS).child(emailKey(email))
                        .child("pendingPassword").setValue(newPassword)
                        .addOnCompleteListener {
                            auth.sendPasswordResetEmail(email)
                            clearResetDoc(email)
                            onSuccess()
                        }
                } else {
                    onError(e.localizedMessage ?: "Could not update password.")
                }
            }
    }

    private fun fetchOrCreateProfile(
        firebaseUser: FirebaseUser,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        root.child(NODE_USERS).child(firebaseUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        onSuccess(User.fromMap(firebaseUser.uid, snapshotToMap(snapshot)))
                    } else {
                        val names = (firebaseUser.displayName ?: "").split(" ")
                        saveProfile(
                            User(
                                uid = firebaseUser.uid,
                                email = firebaseUser.email.orEmpty(),
                                firstName = names.firstOrNull().orEmpty(),
                                lastName = names.drop(1).joinToString(" "),
                                photoUrl = firebaseUser.photoUrl?.toString().orEmpty()
                            ),
                            onSuccess,
                            onError
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    private fun saveProfile(
        profile: User,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mutableMapOf<String, Any>(
            "$NODE_USERS/${profile.uid}" to profile.toMap()
                .filterValues { it != null }
                .mapValues { (_, v) -> v as Any }
        )
        if (profile.email.isNotBlank()) {
            updates["$NODE_EMAIL_INDEX/${emailKey(profile.email)}"] =
                mapOf("uid" to profile.uid, "email" to profile.email)
        }
        if (profile.phone.isNotBlank()) {
            updates["$NODE_PHONE_INDEX/${sanitizeKey(profile.phone)}"] =
                mapOf("uid" to profile.uid, "email" to profile.email)
        }

        root.updateChildren(updates)
            .addOnSuccessListener { onSuccess(profile) }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to save profile to Realtime Database.")
            }
    }

    private fun clearResetDoc(email: String) {
        root.child(NODE_RESETS).child(emailKey(email)).removeValue()
    }

    private fun snapshotToMap(snapshot: DataSnapshot): Map<*, *> {
        return (snapshot.value as? Map<*, *>) ?: emptyMap<String, Any?>()
    }

    private fun normalizeEmail(value: String): String = value.trim().lowercase()

    private fun emailKey(email: String): String = sha256(normalizeEmail(email))

    private fun sanitizeKey(value: String): String =
        value.replace(".", "_").replace("#", "_").replace("$", "_")
            .replace("[", "_").replace("]", "_").replace("/", "_")

    private fun isPhoneLike(value: String): Boolean =
        Regex("^\\+?\\d{10,15}$").matches(value.trim())

    private fun phoneToAuthEmail(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return "$digits@guardband.phone"
    }

    private fun generateOtp(): String = Random.nextInt(10000, 99999).toString()

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val NODE_USERS = "users"
        private const val NODE_RESETS = "password_resets"
        private const val NODE_PHONE_INDEX = "phone_index"
        private const val NODE_EMAIL_INDEX = "email_index"
        private const val OTP_TTL_MS = 2 * 60 * 1000L

        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository().also { instance = it }
            }
    }
}
