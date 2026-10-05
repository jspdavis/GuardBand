package com.example.guardband.ui.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
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
 * Nothing here logs: not the token, not an account, not a provider message.
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
            GoogleIdTokenResult.NoGoogleAccount
        } catch (e: GetCredentialUnsupportedException) {
            GoogleIdTokenResult.Unavailable
        } catch (e: GetCredentialProviderConfigurationException) {
            GoogleIdTokenResult.Unavailable
        } catch (e: GoogleIdTokenParsingException) {
            GoogleIdTokenResult.Unknown
        } catch (e: IOException) {
            GoogleIdTokenResult.Network
        } catch (e: GetCredentialException) {
            GoogleIdTokenResult.Unknown
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
}
