package com.example.guardband.ui.reset

import com.example.guardband.utils.PasswordCriteria

/**
 * Which of the screen's four faces is showing.
 *
 * Separate states rather than a pair of booleans, because the screen genuinely
 * looks different in each: a spinner, a form, a dead end, or on its way out.
 */
enum class ResetPasswordStage {

    /** Checking the code from the link. Nothing is editable yet. */
    VERIFYING,

    /** The code is good. The form is live and Submit is gated on the criteria. */
    READY,

    /** [ResetPasswordViewModel.onSubmitClicked] is in flight. */
    SUBMITTING,

    /**
     * The link cannot be used: expired, already spent, or not a reset link at
     * all. A new email is the only way on, so the screen offers that and no
     * Retry.
     */
    LINK_DEAD,

    /** The password is changed. On the way to Login. */
    DONE
}

/** Render state for [ResetPasswordActivity]. */
data class ResetPasswordUiState(
    val stage: ResetPasswordStage = ResetPasswordStage.VERIFYING,

    /**
     * The address the link belongs to, from `verifyPasswordResetCode`, shown
     * read-only so the user can see which account they are changing.
     *
     * Empty until the code is checked. Never logged — it is somebody's email
     * address, and the rest of the app does not log those either.
     */
    val email: String = "",

    /** Which rules the typed pair satisfies. Drives the criteria list and Submit. */
    val criteria: PasswordCriteria = PasswordCriteria.NONE,

    /**
     * An error belonging to the password fields — Firebase rejected it as too
     * weak. Shown on the field, not as a Toast, because it is about what was
     * typed.
     */
    val fieldError: String? = null,

    /**
     * Why the link cannot be used. Only ever set with
     * [ResetPasswordStage.LINK_DEAD], where it is the panel's own text.
     *
     * A failure the user can simply submit through again is **not** here: that
     * belongs to the attempt rather than to the screen, so it goes out as a
     * [ResetPasswordEvent.ShowMessage] and leaves no state behind.
     */
    val message: String? = null,

    /**
     * True when the code check failed for a reason that might not happen again
     * — no connection, rather than a spent code — so the screen offers to try
     * the same link once more.
     */
    val canRetryVerify: Boolean = false
) {

    /** True while a repository call is in flight: controls disabled, Back blocked. */
    val isBusy: Boolean
        get() = stage == ResetPasswordStage.VERIFYING || stage == ResetPasswordStage.SUBMITTING

    /**
     * Whether Submit is live.
     *
     * The criteria gate it, which is the point of showing them: a user should
     * never be able to submit a password the list says is not acceptable, nor
     * be refused one it says is fine.
     */
    val canSubmit: Boolean
        get() = stage == ResetPasswordStage.READY && criteria.allMet
}
