package com.example.guardband.ui.mocksender

import android.content.SharedPreferences
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * The Mock Sender's own band identity, the same as the firmware's
 * (docs/device-pairing.md §2): the first time it is needed, the harness signs
 * up as a Firebase **anonymous user** over the Auth REST API, and that user's
 * uid is this band's own id. The uid and the refresh token are kept in this
 * phone's SharedPreferences, so a relaunch is the same band; the one-hour ID
 * token is kept in memory and renewed through the refresh token.
 *
 * It never touches the app's own session: these are REST calls, not
 * `FirebaseAuth`, so whoever is signed in to the receiver screens stays signed
 * in.
 *
 * **Never log or show a token.** Either one lets anyone write as this band.
 * The band id itself is shown, because pairing will need it typed in.
 *
 * Blocking network I/O: call it off the main thread ([AlertSender] does, on
 * `Dispatchers.IO` under its send lock).
 *
 * @param apiKey the project's web API key, from the Firebase app's options
 *   (`google-services.json`); never a copy in source.
 */
class BandIdentity(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private var idToken: String? = null
    private var idTokenExpiresAt = 0L

    /** No sign-in is tried again before this, after a failure. */
    private var failedUntil = 0L
    private var lastFailure: String? = null

    /** This band's own id (its anonymous uid), or null before the first sign-up. */
    val bandId: String? get() = prefs.getString(KEY_UID, null)

    /**
     * A signed-in ID token, signing up or refreshing as needed.
     *
     * After a failure nothing is tried for [BandAuthReplies.BACKOFF_MS], and
     * the same failure is thrown at once, so a project with anonymous sign-in
     * off does not slow every alert to the shared band down.
     *
     * @throws IOException when Firebase refuses or cannot be reached
     */
    @Synchronized
    fun idToken(): String {
        val now = clock()
        idToken?.let { token ->
            if (!BandAuthReplies.needsRefresh(idTokenExpiresAt, now)) return token
        }
        if (now < failedUntil) throw IOException(lastFailure ?: "Not signed in")

        val refresh = prefs.getString(KEY_REFRESH, null)
        return try {
            val session = if (bandId == null || refresh == null) signUp() else refresh(refresh)
            prefs.edit()
                .putString(KEY_UID, session.uid ?: bandId)
                .putString(KEY_REFRESH, session.refreshToken ?: refresh)
                .apply()
            idToken = session.idToken
            idTokenExpiresAt = now + session.expiresInSeconds * 1_000L
            lastFailure = null
            session.idToken
        } catch (e: IOException) {
            idToken = null
            lastFailure = e.message
            failedUntil = now + BandAuthReplies.BACKOFF_MS
            throw e
        }
    }

    /** The database refused the token (HTTP 401): sign in afresh next time. */
    @Synchronized
    fun invalidate() {
        idToken = null
    }

    /**
     * Forgets this band: the next sign-in makes a new anonymous user, so a
     * new band id. The old user stays in Firebase Auth, unused.
     */
    @Synchronized
    fun forget() {
        prefs.edit().remove(KEY_UID).remove(KEY_REFRESH).apply()
        idToken = null
        idTokenExpiresAt = 0L
        failedUntil = 0L
        lastFailure = null
    }

    private fun signUp(): BandAuthReplies.Session {
        val request = Request.Builder()
            .url("$SIGN_UP_URL?key=$apiKey")
            .post("""{"returnSecureToken":true}""".toRequestBody(JSON))
            .build()
        val body = call(request, "Creating the band id")
        return BandAuthReplies.fromSignUp(body)
            ?: throw IOException("Creating the band id: unexpected reply")
    }

    private fun refresh(refreshToken: String): BandAuthReplies.Session {
        val request = Request.Builder()
            .url("$TOKEN_URL?key=$apiKey")
            .post(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build()
            )
            .build()
        val body = call(request, "Signing the band in")
        return BandAuthReplies.fromRefresh(body)
            ?: throw IOException("Signing the band in: unexpected reply")
    }

    /**
     * The reply's body, or an [IOException] naming Firebase's error code and,
     * where there is one, what to do. Never the URL: it carries the key.
     */
    private fun call(request: Request, what: String): String =
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val code = BandAuthReplies.errorCode(body)
                val hint = BandAuthReplies.hintFor(code)?.let { " ($it)" }.orEmpty()
                throw IOException("$what: HTTP ${response.code} ${code.orEmpty()}$hint".trim())
            }
            body
        }

    companion object {
        /** The SharedPreferences file: this phone's band, not the signed-in account's. */
        const val PREFS_NAME = "mock_sender_band"

        private const val KEY_UID = "uid"
        private const val KEY_REFRESH = "refresh_token"

        private const val SIGN_UP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
        private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
        private val JSON = "application/json".toMediaType()
    }
}

/**
 * Reading the Auth REST API's replies: pure, so it is a plain test
 * (`BandAuthRepliesTest`). The sign-up reply is camelCase (`idToken`,
 * `localId`), the token reply snake_case (`id_token`, `user_id`).
 */
object BandAuthReplies {

    /** What one sign-up or sign-in gives back. */
    data class Session(
        val uid: String?,
        val idToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long
    )

    /** Sign in again this long before the ID token runs out, as the firmware does. */
    const val REFRESH_MARGIN_MS = 5 * 60_000L

    /** After a failed sign-in, how long before the next try. */
    const val BACKOFF_MS = 60_000L

    private const val DEFAULT_EXPIRES_IN_S = 3_600L

    /** `accounts:signUp`: null unless it holds a uid and both tokens. */
    fun fromSignUp(body: String): Session? {
        val reply = parse(body) ?: return null
        val uid = reply.optString("localId").ifBlank { return null }
        val idToken = reply.optString("idToken").ifBlank { return null }
        val refresh = reply.optString("refreshToken").ifBlank { return null }
        return Session(uid, idToken, refresh, expiresIn(reply.optString("expiresIn")))
    }

    /** `securetoken /v1/token`: null unless it holds an ID token. */
    fun fromRefresh(body: String): Session? {
        val reply = parse(body) ?: return null
        val idToken = reply.optString("id_token").ifBlank { return null }
        return Session(
            uid = reply.optString("user_id").ifBlank { null },
            idToken = idToken,
            refreshToken = reply.optString("refresh_token").ifBlank { null },
            expiresInSeconds = expiresIn(reply.optString("expires_in"))
        )
    }

    /** Firebase's error code (`error.message`), e.g. `OPERATION_NOT_ALLOWED`. */
    fun errorCode(body: String?): String? =
        body?.let(::parse)?.optJSONObject("error")?.optString("message")?.ifBlank { null }

    /** What to do about the two failures with a known cause; null otherwise. */
    fun hintFor(code: String?): String? = when {
        code == null -> null
        code.startsWith("OPERATION_NOT_ALLOWED") || code.startsWith("ADMIN_ONLY_OPERATION") ->
            "turn on Anonymous sign-in in the Firebase Console"
        code.startsWith("TOKEN_EXPIRED") || code.startsWith("USER_NOT_FOUND") ||
            code.startsWith("USER_DISABLED") || code.startsWith("INVALID_REFRESH_TOKEN") ->
            "this band id no longer works: tap New band id"
        else -> null
    }

    /** Whether a token running out at [expiresAt] should be renewed at [now]. */
    fun needsRefresh(expiresAt: Long, now: Long): Boolean = now >= expiresAt - REFRESH_MARGIN_MS

    private fun expiresIn(value: String): Long =
        value.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_EXPIRES_IN_S

    private fun parse(body: String): JSONObject? = runCatching { JSONObject(body) }.getOrNull()
}
