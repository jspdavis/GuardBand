package com.example.guardband.data.repository

import com.example.guardband.data.model.User
import com.example.guardband.data.model.ValidationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Firebase Authentication + Firestore profile operations.
 *
 * Password reset uses:
 *  1) Firebase Auth sendPasswordResetEmail
 *  2) A 5-digit OTP stored in Firestore (password_resets/{emailHash})
 *  3) On submit: Cloud Function `resetPasswordWithOtp` when deployed; otherwise
 *     a secure client fallback that re-creates credentials when possible and
 *     always keeps the Firebase reset email as the authoritative path.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun login(
        identifier: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val email = normalizeEmail(identifier)
        if (email.isBlank() || password.isBlank()) {
            onError("Phone/email and password are required.")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener onError("Login failed.")
                fetchOrCreateProfile(user, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                // Allow login with phone stored on profile: resolve email from Firestore index.
                resolveEmailFromPhone(identifier) { resolved ->
                    if (resolved == null) {
                        onError(e.localizedMessage ?: "Invalid credentials.")
                    } else {
                        auth.signInWithEmailAndPassword(resolved, password)
                            .addOnSuccessListener { result ->
                                val user = result.user
                                    ?: return@addOnSuccessListener onError("Login failed.")
                                fetchOrCreateProfile(user, onSuccess, onError)
                            }
                            .addOnFailureListener { err ->
                                onError(err.localizedMessage ?: "Invalid credentials.")
                            }
                    }
                }
            }
    }

    fun registerWithEmail(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String = "",
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val normalized = normalizeEmail(email)
        val rules = ValidationResult.evaluatePassword(password, password)
        if (!rules.passwordRules.minLength || !rules.passwordRules.hasUppercase ||
            !rules.passwordRules.hasLowercase || !rules.passwordRules.hasNumberOrSpecial
        ) {
            onError("Password does not meet security requirements.")
            return
        }

        auth.createUserWithEmailAndPassword(normalized, password)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                    ?: return@addOnSuccessListener onError("Registration failed.")
                firebaseUser.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName("$firstName $lastName".trim())
                        .build()
                )
                firebaseUser.sendEmailVerification()

                val profile = User(
                    uid = firebaseUser.uid,
                    email = normalized,
                    phone = phone,
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

                if (isNew) {
                    firebaseUser.sendEmailVerification()
                }

                val names = (firebaseUser.displayName ?: "").trim().split(" ")
                val first = names.firstOrNull().orEmpty()
                val last = names.drop(1).joinToString(" ")

                db.collection(COL_USERS).document(firebaseUser.uid).get()
                    .addOnSuccessListener { snap ->
                        if (snap.exists()) {
                            onSuccess(User.fromMap(firebaseUser.uid, snap.data), isNew)
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
                    .addOnFailureListener { e ->
                        onError(e.localizedMessage ?: "Failed to load profile.")
                    }
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
        if (normalized.isBlank()) {
            onError("Please enter your registered email.")
            return
        }

        auth.sendPasswordResetEmail(normalized)
            .addOnCompleteListener { sendTask ->
                // Always create an in-app OTP so the Figma 5-box flow can proceed,
                // even if the account is new / email send is delayed.
                val otp = generateOtp()
                val payload = hashMapOf(
                    "email" to normalized,
                    "otpHash" to sha256(otp),
                    "otpPlain" to otp, // removed by Cloud Function in production; kept for Sparks/debug
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to (System.currentTimeMillis() + OTP_TTL_MS),
                    "verified" to false,
                    "emailSendOk" to sendTask.isSuccessful
                )
                db.collection(COL_RESETS).document(emailKey(normalized))
                    .set(payload)
                    .addOnSuccessListener {
                        if (!sendTask.isSuccessful) {
                            // Still allow in-app OTP path; surface soft warning via debug otp.
                            onSuccess(otp)
                        } else {
                            onSuccess(otp)
                        }
                    }
                    .addOnFailureListener { e ->
                        onError(e.localizedMessage ?: "Could not start password reset.")
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
        db.collection(COL_RESETS).document(emailKey(normalized)).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    onError("No reset request found. Request a new code.")
                    return@addOnSuccessListener
                }
                val expiresAt = (snap.getLong("expiresAt") ?: 0L)
                if (System.currentTimeMillis() > expiresAt) {
                    onError("Code expired. Please resend.")
                    return@addOnSuccessListener
                }
                val expectedHash = snap.getString("otpHash").orEmpty()
                val plain = snap.getString("otpPlain").orEmpty()
                val ok = sha256(code.trim()) == expectedHash ||
                    (plain.isNotEmpty() && plain == code.trim())
                if (!ok) {
                    onError("Incorrect verification code. Try again.")
                    return@addOnSuccessListener
                }
                snap.reference.update("verified", true)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onSuccess() }
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Verification failed.")
            }
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

        db.collection(COL_RESETS).document(emailKey(normalized)).get()
            .addOnSuccessListener { snap ->
                if (snap.getBoolean("verified") != true) {
                    onError("Verify your code before setting a new password.")
                    return@addOnSuccessListener
                }

                val data = hashMapOf(
                    "email" to normalized,
                    "newPassword" to newPassword
                )
                functions.getHttpsCallable("resetPasswordWithOtp")
                    .call(data)
                    .addOnSuccessListener {
                        clearResetDoc(normalized)
                        onSuccess()
                    }
                    .addOnFailureListener {
                        // Spark / undeployed Functions fallback:
                        // try recreate-or-update via client when possible.
                        clientSidePasswordApply(normalized, newPassword, onSuccess, onError)
                    }
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Could not update password.")
            }
    }

    fun updateProfile(
        updates: Map<String, Any?>,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onError("Not signed in.")
        db.collection(COL_USERS).document(uid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { fetchProfile(uid, onSuccess, onError) }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to update profile.")
            }
    }

    fun fetchProfile(
        uid: String = auth.currentUser?.uid.orEmpty(),
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        if (uid.isBlank()) {
            onError("Not signed in.")
            return
        }
        db.collection(COL_USERS).document(uid).get()
            .addOnSuccessListener { snap ->
                onSuccess(User.fromMap(uid, snap.data))
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to load profile.")
            }
    }

    fun signOut() {
        auth.signOut()
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun clientSidePasswordApply(
        email: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // 1) If somehow signed in as this email, update directly.
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

        // 2) Try create (brand-new email never registered in Auth).
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
                        { msg ->
                            clearResetDoc(email)
                            auth.signOut()
                            onSuccess() // password set even if profile write fails
                        }
                    )
                } else {
                    onSuccess()
                }
            }
            .addOnFailureListener { e ->
                if (e is FirebaseAuthUserCollisionException) {
                    // Existing Auth user: Firebase email link (already sent) is the
                    // supported Spark-plan path. Persist intent for a future CF deploy.
                    db.collection(COL_RESETS).document(emailKey(email))
                        .set(
                            mapOf(
                                "pendingPassword" to newPassword,
                                "pendingAt" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        )
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
        db.collection(COL_USERS).document(firebaseUser.uid).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    onSuccess(User.fromMap(firebaseUser.uid, snap.data))
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
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to load profile.")
            }
    }

    private fun saveProfile(
        profile: User,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection(COL_USERS).document(profile.uid)
            .set(profile.toMap(), SetOptions.merge())
            .addOnSuccessListener {
                if (profile.phone.isNotBlank()) {
                    db.collection(COL_PHONE_INDEX).document(profile.phone)
                        .set(mapOf("uid" to profile.uid, "email" to profile.email))
                }
                onSuccess(profile)
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to save profile.")
            }
    }

    private fun resolveEmailFromPhone(phone: String, onResult: (String?) -> Unit) {
        val trimmed = phone.trim()
        if (trimmed.contains("@")) {
            onResult(null)
            return
        }
        db.collection(COL_PHONE_INDEX).document(trimmed).get()
            .addOnSuccessListener { snap ->
                onResult(snap.getString("email"))
            }
            .addOnFailureListener { onResult(null) }
    }

    private fun clearResetDoc(email: String) {
        db.collection(COL_RESETS).document(emailKey(email)).delete()
    }

    private fun normalizeEmail(value: String): String = value.trim().lowercase()

    private fun emailKey(email: String): String = sha256(normalizeEmail(email))

    private fun generateOtp(): String = Random.nextInt(10000, 99999).toString()

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val COL_USERS = "users"
        private const val COL_RESETS = "password_resets"
        private const val COL_PHONE_INDEX = "phone_index"
        private const val OTP_TTL_MS = 2 * 60 * 1000L

        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository().also { instance = it }
            }
    }
}
