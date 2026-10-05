package com.example.guardband.ui.signup

/** One-shot events emitted by [SignUpLocationViewModel]. */
sealed interface SignUpLocationEvent {
    data class ShowMessage(val text: String) : SignUpLocationEvent

    /**
     * @param email           the Google address, in complete-profile mode only.
     * @param completeProfile true when the account already exists (Google
     *                        sign-in) and the next step must not create one.
     */
    data class NavigateToContacts(
        val name: String,
        val location: String,
        val email: String,
        val completeProfile: Boolean
    ) : SignUpLocationEvent
}
