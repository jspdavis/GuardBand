package com.example.guardband.data.repository

import com.example.guardband.data.model.GoogleSignInOutcome
import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import com.google.firebase.auth.ActionCodeResult
import com.google.firebase.auth.EmailAuthProvider
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
 * @param userProfileRepository read and conditionally written on every sign-in,
 *   so a session always has a `users/{uid}` record behind it. Not written by
 *   [createAccount]: sign-up's own write is the atomic one in
 *   [UserProfileRepository.finalizeSignUp].
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val userProfileRepository: UserProfileRepository
) : AuthRepository {

    /**
     * Signs in and makes sure a profile record exists, the same way
     * [signInWithGoogle] does.
     *
     * An email account can legitimately have no `users/{uid}` record: one made
     * in the Console has never had one, and one whose sign-up write failed lost
     * the race to it. Those users used to sign in to a Profile tab with no name
     * and no email at all. The write is conditional and its failure is ignored
     * - see [ProfileProvisioning].
     */
    override suspend fun login(email: String, password: String): Result<User> = authResult {
        val signedIn = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = (signedIn.user ?: throw AuthError.NotSignedIn).toUser()

        // Result deliberately ignored - see ProfileProvisioning.ensureProfile.
        ProfileProvisioning.ensureProfile(userProfileRepository, user, isNewUser = false)

        user
    }

    /**
     * Creates the account and stores [name] as the Firebase display name, so
     * [currentUser] can return it without a database read.
     *
     * Writes nothing to the database - see [AuthRepository.createAccount] for
     * why that is split out.
     */
    override suspend fun createAccount(
        name: String,
        email: String,
        password: String
    ): Result<User> = authResult {
        val created = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = created.user ?: throw AuthError.NotSignedIn

        val displayName = name.trim()

        firebaseUser.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        ).await()

        User(
            id = firebaseUser.uid,
            name = displayName,
            email = firebaseUser.email.orEmpty()
        )
    }

    /**
     * Signs in with a Google ID token and makes sure a profile record exists.
     *
     * Unlike [register], a failed profile write does **not** fail the call: the
     * session already exists by then, so reporting a failure would leave the
     * user signed in and staring at an error. [ProfileProvisioning] holds
     * that rule and the reason the write is conditional.
     */
    override suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInOutcome> = authResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val signedIn = auth.signInWithCredential(credential).await()
        val firebaseUser = signedIn.user ?: throw AuthError.NotSignedIn
        val isNewUser = signedIn.additionalUserInfo?.isNewUser == true

        val user = firebaseUser.toUser()
        // Result deliberately ignored - see ProfileProvisioning.ensureProfile.
        ProfileProvisioning.ensureProfile(userProfileRepository, user, isNewUser)

        GoogleSignInOutcome(user = user, isNewUser = isNewUser)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = authResult {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Returns the email Firebase has on file for the code. Project-level, not
     * session-level: `auth.currentUser` is neither read nor changed.
     */
    override suspend fun verifyPasswordResetCode(code: String): Result<String> = authResult {
        auth.verifyPasswordResetCode(code).await()
    }

    override suspend fun confirmPasswordReset(
        code: String,
        newPassword: String
    ): Result<Unit> = authResult {
        auth.confirmPasswordReset(code, newPassword).await()
    }

    override fun currentUser(): User? = auth.currentUser?.toUser()

    // -- Re-authentication and deletion ----------------------------------------

    /**
     * Reads the providers off `providerData`, not off
     * [FirebaseUser.getProviderId], which returns the literal `"firebase"` for
     * the user object itself and so says nothing about how they sign in.
     *
     * The `"firebase"` entry `providerData` also contains maps to null and is
     * dropped, along with any provider this app does not offer.
     */
    override fun currentUserProviders(): Set<SignInProvider> =
        auth.currentUser?.providerData.orEmpty().mapNotNullTo(mutableSetOf()) { info ->
            when (info.providerId) {
                EmailAuthProvider.PROVIDER_ID -> SignInProvider.PASSWORD
                GoogleAuthProvider.PROVIDER_ID -> SignInProvider.GOOGLE
                else -> null
            }
        }

    /**
     * The email is taken from the live session rather than accepted as an
     * argument, so the credential can only ever be built for the account that
     * is actually signed in.
     *
     * A password provider always has an email, so a blank one means the
     * session is not what the caller thinks it is.
     */
    override suspend fun reauthenticateWithPassword(password: String): Result<Unit> = authResult {
        val user = auth.currentUser ?: throw AuthError.NotSignedIn
        val email = user.email?.takeIf { it.isNotBlank() } ?: throw AuthError.NotSignedIn
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }

    override suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> = authResult {
        val user = auth.currentUser ?: throw AuthError.NotSignedIn
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    /**
     * Deletes the Auth user only. The database subtree is
     * [UserProfileRepository.markDeletionPending]'s job and must already be done -
     * see [AuthRepository.deleteAccount].
     *
     * Firebase clears `currentUser` itself on success, so no `signOut` is
     * needed here; the caller still calls it, because a session that somehow
     * survived would put a deleted account back through the Splash gate.
     */
    override suspend fun deleteAccount(): Result<Unit> = authResult {
        val user = auth.currentUser ?: throw AuthError.NotSignedIn
        user.delete().await()
    }

    // -- Email verification ------------------------------------------------------

    override fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    override suspend fun sendEmailVerification(): Result<Unit> = authResult {
        val user = auth.currentUser ?: throw AuthError.NotSignedIn
        user.sendEmailVerification().await()
    }

    override suspend fun reloadUser(): Result<Unit> = authResult {
        val user = auth.currentUser ?: throw AuthError.NotSignedIn
        user.reload().await()
    }

    /**
     * `checkActionCode` first, so only a verify-email code is ever applied -
     * see [AuthRepository.applyEmailVerification]. Project-level, like the
     * reset calls: `auth.currentUser` is neither read nor changed.
     */
    override suspend fun applyEmailVerification(code: String): Result<Unit> = authResult {
        val info = auth.checkActionCode(code).await()
        if (info.operation != ActionCodeResult.VERIFY_EMAIL) throw AuthError.InvalidActionCode
        auth.applyActionCode(code).await()
    }

    override fun isLoggedIn(): Boolean = auth.currentUser != null

    override fun signOut() = auth.signOut()

    private fun FirebaseUser.toUser(): User = User(
        id = uid,
        name = displayName.orEmpty(),
        email = email.orEmpty(),
        photoUrl = photoUrl?.toString()
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
