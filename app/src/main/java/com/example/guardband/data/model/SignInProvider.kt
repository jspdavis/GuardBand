package com.example.guardband.data.model

/**
 * A sign-in provider an account can be reached by, in the app's own
 * vocabulary.
 *
 * Deliberately not Firebase's provider-id strings. The mapping from
 * `"password"` and `"google.com"` lives in
 * [FirebaseAuthRepository][com.example.guardband.data.repository.FirebaseAuthRepository],
 * which is the only layer allowed to import `EmailAuthProvider` and
 * `GoogleAuthProvider` and so the only place those constants should appear.
 *
 * Firebase also reports a `"firebase"` entry in `providerData`; it is not a
 * sign-in method and is dropped rather than represented here.
 */
enum class SignInProvider {

    /** Email and password. Re-authenticating needs the password typed again. */
    PASSWORD,

    /** Google. Re-authenticating needs a fresh ID token, so it needs an Activity. */
    GOOGLE
}
