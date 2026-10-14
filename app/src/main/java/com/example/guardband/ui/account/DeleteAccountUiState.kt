package com.example.guardband.ui.account

import com.example.guardband.data.model.SignInProvider

/**
 * How far the delete has got.
 *
 * The order is the order of operations, and it matters: re-authenticate, then
 * remove `users/{uid}` while the session can still authorise it, then delete
 * the Auth user. See [DeleteAccountViewModel].
 */
enum class DeleteAccountStep {

    /** Nothing started. The only step the user can walk away from freely. */
    IDLE,

    /** The modal is up, or the password is being typed. Still nothing written. */
    CONFIRMING,

    /** Proving it is really them: a password check or a fresh Google token. */
    REAUTHENTICATING,

    /** Removing the data and then the account. */
    DELETING,

    /** Something failed. [DeleteAccountUiState.errorMessage] says what; Retry resumes. */
    FAILED,

    /** Gone. The session is over and the screen is on its way to Login. */
    DONE
}

/**
 * Render state for the Delete account flow, owned by [DeleteAccountViewModel]
 * and rendered by [AccountActivity].
 */
data class DeleteAccountUiState(
    val step: DeleteAccountStep = DeleteAccountStep.IDLE,

    /**
     * What the user must produce to re-authenticate, decided once from the
     * account's providers when the flow starts.
     *
     * Null until then, and null for a session whose providers are all
     * unrecognised — which is why [DeleteAccountViewModel] refuses to start
     * rather than guessing a default.
     */
    val method: SignInProvider? = null,

    /**
     * True once Firebase has accepted a fresh credential, so the deletes are
     * allowed to run.
     *
     * Cleared again if the Auth delete comes back
     * [AuthError.RequiresRecentLogin][com.example.guardband.data.repository.AuthError.RequiresRecentLogin],
     * because at that point the window has closed and Retry has to ask again.
     */
    val reauthenticated: Boolean = false,

    /**
     * True once `users/{uid}` is gone.
     *
     * The guard that makes Retry resume instead of start over, in the same
     * spirit as
     * [SignUpConsentUiState.accountCreated][com.example.guardband.ui.signup.SignUpConsentUiState.accountCreated].
     * It is also the point of no return: from here the account exists with no
     * data behind it, so the flow may only move on to [DeleteAccountStep.DONE]
     * or sit in [DeleteAccountStep.FAILED] offering Retry. It must never fall
     * back to [DeleteAccountStep.IDLE] and leave the user with no way to
     * finish.
     */
    val profileDeleted: Boolean = false,

    /** What to show while [step] is [DeleteAccountStep.FAILED]. Never Firebase's own text. */
    val errorMessage: String? = null
) {

    /** True while a repository call is in flight: controls disabled, Back blocked. */
    val isBusy: Boolean
        get() = step == DeleteAccountStep.REAUTHENTICATING || step == DeleteAccountStep.DELETING

    /**
     * True when the failure left the account alive with its data already gone,
     * so the message has to say "finish this" rather than "that didn't work".
     */
    val isUnfinished: Boolean
        get() = step == DeleteAccountStep.FAILED && profileDeleted
}
