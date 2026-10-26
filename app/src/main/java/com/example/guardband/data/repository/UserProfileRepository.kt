package com.example.guardband.data.repository

import com.example.guardband.data.model.ConsentRecord
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.model.User

/**
 * The user's own profile record, separate from authentication.
 *
 * Only ever holds what the user typed during sign-up. Passwords, reset codes
 * and tokens are never written here.
 *
 * Same conventions as [AuthRepository]: suspend, and failures come back as
 * [Result.failure] carrying an [AuthError].
 */
interface UserProfileRepository {

    /**
     * Creates or updates the profile fields for [uid].
     *
     * Merges `name` and `email` and leaves every other child of
     * `users/{uid}` alone. That matters now that
     * `users/{uid}/emergency_contacts` exists: this used to replace the whole
     * record, which would delete every emergency contact the user had.
     */
    suspend fun saveProfile(
        uid: String,
        name: String,
        email: String
    ): Result<Unit>

    /**
     * The stored profile for [uid], or `Result.success(null)` when there is no
     * record.
     *
     * A missing record is a success with null, not a failure: an account can legitimately exist without one, and
     * the caller needs to tell that apart from a read that did not work.
     */
    suspend fun fetchProfile(uid: String): Result<User?>

    /**
     * Whether `users/{uid}` holds a profile, nothing, or only the marker a
     * half-finished deletion leaves behind ([markDeletionPending]).
     *
     * Kept alongside [fetchProfile] because [ProfileProvisioning] and the
     * Home gate need that question answered without caring what is in the
     * record. Three answers, not "exists or not": the marker must never be
     * read as a profile, and never as an empty slot a sign-in may fill.
     */
    suspend fun profileState(uid: String): Result<ProfileState>

    /**
     * The consent record stored for [uid], or `Result.success(null)` when there
     * is none.
     *
     * **Three outcomes, and the caller must keep them apart:** a record, no
     * record, and a read that failed. A failure is never reported as null -
     * the consent gate would otherwise send everybody on a bad network to the
     * consent screen. Same contract as [fetchProfile].
     */
    suspend fun consentRecord(uid: String): Result<ConsentRecord?>

    /**
     * Records that the signed-in user has agreed, for an account that already
     * exists but has no consent record - the consent gate's write.
     *
     * Writes exactly what [finalizeSignUp] writes for consent, through the same
     * code: `accepted = true`, [consentVersion] and a **server** `acceptedAt`,
     * in one commit, so a record means the same thing whichever route made it.
     * Touches nothing else under `users/{uid}`.
     */
    suspend fun recordConsent(uid: String, consentVersion: String): Result<Unit>

    /**
     * Writes the profile, [contacts] and the consent record for a brand-new
     * account in **one** atomic commit (D5).
     *
     * [consentVersion] is stored alongside a server timestamp. A ticked box
     * that leaves no trace is no use as a consent record, and putting it in
     * this same write means a profile can never exist without one.
     *
     * One multi-path write, so sign-up cannot half-succeed: either the profile
     * and every contact land, or nothing does. That is what makes the retry on
     * [SignUpContactsViewModel][com.example.guardband.ui.signup.SignUpContactsViewModel]
     * safe to run again - there is no partial state to reconcile.
     *
     * Each contact is validated and normalised first, so an invalid phone fails
     * the whole call before anything is sent. Only for a new account: the
     * contacts are written under fresh keys, so calling this on an account that
     * already has contacts would add duplicates rather than replace them.
     */
    suspend fun finalizeSignUp(
        uid: String,
        name: String,
        email: String,
        contacts: List<EmergencyContact>,
        consentVersion: String
    ): Result<Unit>

    /**
     * Deletes everything at `users/{uid}` — profile fields, every emergency
     * contact and the consent record — and leaves only the deletion marker,
     * `{ "deletion_pending": true }`, in **one** write.
     *
     * **One write is the point.** The data goes and the marker lands in the
     * same commit, so there is no moment at which the data is gone without a
     * marker to say why. If the Auth delete that follows fails and the user
     * gives up, the next sign-in reads [ProfileState.DELETION_PENDING] and
     * resumes the deletion instead of provisioning a fresh, thin profile.
     *
     * A replace, not a child-by-child delete, so a field nobody listed here
     * goes too, and there is no order in which it can half-succeed.
     *
     * **The marker outlives a finished deletion.** Once the Auth user is gone
     * no session can write `users/{uid}` again, so `deletion_pending: true`
     * stays under a uid that no longer belongs to anyone. It holds no personal
     * data; a human can sweep it from the Console. Clearing it any earlier
     * would reopen the gap it exists to close.
     *
     * Needs no rules change: `users/$uid/.write` is owner-only and nothing
     * forbids an unlisted child, and RTDB skips `.validate` on the children
     * this nulls. Must run **while the user is still signed in**, for the same
     * reason: once [AuthRepository.deleteAccount] succeeds there is no
     * `auth.uid` left to authorise it.
     *
     * Idempotent: writing the marker over the marker is a success, which is
     * what a retry after a half-finished delete relies on.
     */
    suspend fun markDeletionPending(uid: String): Result<Unit>

    companion object {
        /**
         * The wording the user agreed to, stored with their consent.
         *
         * Versioned so a reworded notice can be told apart from this one, and
         * can require agreeing again. Bump it whenever
         * `label_signup_consent_statement` changes in a way that alters what
         * is being agreed to.
         *
         * **It moves with the Privacy Notice.** The notice page shows its own
         * version, and the two must always read the same: the point of storing
         * a version is to know which words somebody agreed to, which is no use
         * if the number cannot be matched to a document. Bumping it means
         * editing `hosting/public/privacy/index.html` and adding a row to
         * `docs/privacy-and-consent.md` in the same commit.
         *
         * A **String**, not a number, so "1.10" can follow "1.9" without
         * sorting before it. The Security Rules draft validates it as a string
         * for the same reason.
         *
         * Bumped to 1.1 when the consent step gained a working link to the
         * notice. Bumped to 1.2 (2026-10-24) when the notice was published
         * without its DRAFT banner and gained MapTiler, Google's place names
         * and the Google profile picture. Existing records keep the version
         * they were given, and **nobody is re-prompted** - there is no
         * re-consent flow yet.
         */
        const val CONSENT_VERSION = "1.2"
    }
}
