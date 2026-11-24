package com.example.guardband.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.guardband.BuildConfig
import com.example.guardband.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Gets a Google ID token through Credential Manager.
 *
 * The **only** class in the app that touches `androidx.credentials`. It lives
 * in the UI layer because `CredentialManager.getCredential` needs an Activity
 * context, which a ViewModel must not hold; the ViewModel receives nothing but
 * the token string or a [GoogleIdTokenResult].
 *
 * The legacy `GoogleSignIn` / `play-services-auth` API is deliberately not
 * used. `play-services-auth` is on the classpath only because
 * `credentials-play-services-auth` needs it as its backend provider.
 *
 * `setFilterByAuthorizedAccounts(false)` so the chooser offers every account on
 * the device, not just ones that already signed in to this app — a first-time
 * user would otherwise be shown an empty sheet.
 *
 * The server client ID is `R.string.default_web_client_id`, which the Google
 * Services plugin generates from the web `oauth_client` entry in
 * `google-services.json`. It is never hardcoded.
 *
 * **What this logs:** in a debug build only, the exception's class name, one
 * allow-listed token from [GoogleIdTokenFailure.diagnostic] and the result it
 * chose. Never the token, never an account, never the provider's own message —
 * which is also why the exception itself is never passed to [Log], since that
 * would print the message inside the stack trace. A release build logs nothing.
 *
 * @param context an Activity context. Credential Manager renders a sheet, so
 *   an application context will not do.
 */
class GoogleIdTokenProvider(private val context: Context) {

    private val credentialManager by lazy { CredentialManager.create(context) }

    /**
     * Shows the account chooser and returns what came back.
     *
     * Exceptions are mapped to [GoogleIdTokenResult], never swallowed into
     * null, so the caller can stay silent on a cancel and speak up otherwise.
     * [CancellationException] is rethrown, so a cancelled scope stays
     * cancelled instead of being reported as a sign-in failure.
     *
     * `NoCredentialException` and the catch-all both go through [classified]:
     * the two of them carry the "not authorised for this project" failure
     * between them, depending on the Play Services version, so the decision
     * has to live in one place or the two will drift.
     */
    suspend fun requestIdToken(): GoogleIdTokenResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(R.string.default_web_client_id))
                    .build()
            )
            .build()

        return try {
            val credential = credentialManager.getCredential(context, request).credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenResult.Success(
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                )
            } else {
                // A provider returned something that is not a Google ID token.
                GoogleIdTokenResult.Unknown
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            GoogleIdTokenResult.Cancelled
        } catch (e: NoCredentialException) {
            classified(e, fallback = GoogleIdTokenResult.NoGoogleAccount)
        } catch (e: GetCredentialUnsupportedException) {
            GoogleIdTokenResult.Unavailable
        } catch (e: GetCredentialProviderConfigurationException) {
            GoogleIdTokenResult.Unavailable
        } catch (e: GoogleIdTokenParsingException) {
            GoogleIdTokenResult.Unknown
        } catch (e: IOException) {
            GoogleIdTokenResult.Network
        } catch (e: GetCredentialException) {
            classified(e, fallback = GoogleIdTokenResult.Unknown)
        }
    }

    /**
     * Narrows [e] to a cause, falling back to [fallback] when its message says
     * nothing recognisable, and records the outcome in a debug build.
     */
    private fun classified(
        e: GetCredentialException,
        fallback: GoogleIdTokenResult
    ): GoogleIdTokenResult {
        val message = e.errorMessage?.toString() ?: e.message
        val result = GoogleIdTokenFailure.classify(message, fallback)
        if (BuildConfig.DEBUG) {
            logFailure(e, GoogleIdTokenFailure.diagnostic(message), result)
        }
        return result
    }

    /**
     * Debug-only. Logs the shape of the failure, never its text.
     *
     * A status-10 failure gets the fix spelled out, because it is the one a
     * developer meets on a new machine and the one the user-facing message
     * deliberately does not explain.
     */
    private fun logFailure(
        e: GetCredentialException,
        diagnostic: String,
        result: GoogleIdTokenResult
    ) {
        Log.w(
            TAG,
            "No Google credential: ${e.javaClass.simpleName}" +
                " / $diagnostic -> ${result.javaClass.simpleName}"
        )
        if (result is GoogleIdTokenResult.NotConfigured) {
            Log.w(
                TAG,
                "This build is not authorised for the Firebase project. Register this" +
                    " machine's debug SHA-1 in the Firebase Console and re-download" +
                    " google-services.json (README.md, step 3)."
            )
        }
    }

    /**
     * Forgets which account was used, so the chooser appears again next time
     * instead of silently reusing the last one.
     *
     * Called on log out. A failure is ignored on purpose: logging out must
     * never be blocked by the credential provider.
     */
    suspend fun clearCredentialState() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignored - log out proceeds regardless.
        }
    }

    private companion object {
        const val TAG = "GoogleIdTokenProvider"
    }
}
