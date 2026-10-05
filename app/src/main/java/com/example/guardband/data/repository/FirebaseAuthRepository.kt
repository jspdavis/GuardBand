package com.example.guardband.data.repository

import com.example.guardband.data.model.GoogleSignInOutcome
import com.example.guardband.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [AuthRepository] on Firebase Authentication: email + password, plus
 * Google via [signInWithGoogle].
 *
 * The session is [FirebaseAuth.getCurrentUser], which the SDK persists, so it
 * survives process death — unlike the in-memory session this replaces. That
 * makes [com.example.guardband.ui.home.HomeViewModel]'s second gate a
 * belt-and-braces check rather than the load-bearing one it was.
 *
 * Nothing here logs: no uid, no email, no Firebase error text.
 *
 * @param auth the shared instance from
 *   [FirebaseProvider][com.example.guardband.data.FirebaseProvider].
 * @param userProfileRepository writes `users/{uid}` after a successful sign-up.
 *   Injected rather than called from the ViewModel so that one repository call
 *   still means one completed sign-up. [signInWithGoogle] also reads it, to
 *   avoid overwriting a returning user's profile.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val userProfileRepository: UserProfileRepository
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> = authResult {
        val signedIn = auth.signInWithEmailAndPassword(email.trim(), password).await()
        (signedIn.user ?: throw AuthError.NotSignedIn).toUser()
    }

    /**
     * Creates the account, stores [name] as the Firebase display name so
     * [currentUser] can return it without a database read, then writes the
     * profile. A failed profile write fails the whole call: the account exists
     * at that point, but reporting success would hide a half-made account.
     */
    override suspend fun register(
        name: String,
        location: String,
        email: String,
        password: String
    ): Result<User> = authResult {
        val created = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = created.user ?: throw AuthError.NotSignedIn

        val displayName = name.trim()
        val trimmedLocation = location.trim()

        firebaseUser.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        ).await()

        userProfileRepository.saveProfile(
            uid = firebaseUser.uid,
            name = displayName,
            email = firebaseUser.email.orEmpty(),
            location = trimmedLocation
        ).getOrThrow()

        User(
            id = firebaseUser.uid,
            name = displayName,
            email = firebaseUser.email.orEmpty(),
            location = trimmedLocation
        )
    }

    /**
     * Signs in with a Google ID token and makes sure a profile record exists.
     *
     * Unlike [register], a failed profile write does **not** fail the call: the
     * session already exists by then, so reporting a failure would leave the
     * user signed in and staring at an error. [GoogleProfileProvisioning] holds
     * that rule and the reason the write is conditional.
     */
    override suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInOutcome> = authResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val signedIn = auth.signInWithCredential(credential).await()
        val firebaseUser = signedIn.user ?: throw AuthError.NotSignedIn
        val isNewUser = signedIn.additionalUserInfo?.isNewUser == true

        val user = firebaseUser.toUser()
        // Result deliberately ignored - see GoogleProfileProvisioning.ensureProfile.
        GoogleProfileProvisioning.ensureProfile(userProfileRepository, user, isNewUser)

        GoogleSignInOutcome(user = user, isNewUser = isNewUser)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = authResult {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** `location` is empty by contract — see [AuthRepository.currentUser]. */
    override fun currentUser(): User? = auth.currentUser?.toUser()

    override fun isLoggedIn(): Boolean = auth.currentUser != null

    override fun signOut() = auth.signOut()

    private fun FirebaseUser.toUser(): User = User(
        id = uid,
        name = displayName.orEmpty(),
        email = email.orEmpty(),
        location = ""
    )

    /**
     * Runs [block] and maps anything it throws to an [AuthError].
     *
     * [CancellationException] is rethrown so a cancelled viewModelScope stays
     * cancelled instead of being reported to the user as a failure.
     */
    private inline fun <T> authResult(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(FirebaseAuthErrorMapper.map(e))
        }
}
