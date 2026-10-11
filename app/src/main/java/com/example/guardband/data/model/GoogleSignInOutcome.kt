package com.example.guardband.data.model

/**
 * The result of exchanging a Google ID token with Firebase.
 *
 * [isNewUser] decides where the UI goes next: a first-time Google user is
 * routed through the remaining sign-up steps (emergency contacts, consent) in
 * complete-profile mode, while a returning user goes straight to Home.
 *
 * It comes from `AdditionalUserInfo.isNewUser` on the Firebase sign-in result,
 * not from guessing at the profile record: Firebase is the only thing that
 * knows whether the credential just created the account.
 *
 * @param user      the signed-in user, filled from the session alone — see
 *                  [AuthRepository.currentUser][com.example.guardband.data.repository.AuthRepository.currentUser].
 * @param isNewUser true when this sign-in created the Firebase account.
 */
data class GoogleSignInOutcome(
    val user: User,
    val isNewUser: Boolean
)
