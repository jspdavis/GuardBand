package com.example.guardband.ui.reset

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Pulls the `oobCode` out of a Firebase password-reset link, or refuses it.
 *
 * Firebase appends `mode`, `oobCode`, `apiKey` and sometimes `continueUrl` and
 * `lang` to whatever action URL the Console's "Password reset" template points
 * at. Only `mode` and `oobCode` are of any use here; `apiKey` is already in
 * `google-services.json` and the rest is Firebase's hosted page's business.
 *
 * **Takes a `String` and uses [URI], not `android.net.Uri`.** That is what
 * makes it a plain JVM unit test rather than a Robolectric one, in a project
 * that declares no mocking library. The Activity hands it
 * `intent.data.toString()`.
 *
 * **Nothing here logs.** The `oobCode` is a single-use credential for changing
 * somebody's password, and the full URL contains it, so neither may ever reach
 * logcat — not even on the reject paths, which is why failure is a null rather
 * than a message explaining which check failed.
 *
 * The host and path are passed in rather than read from `BuildConfig`, so this
 * object depends on nothing and a test can hand it a deliberately wrong host.
 * Their single source of truth is `hostingHost` / `resetLinkPath` in
 * `app/build.gradle.kts`.
 */
internal object ResetLinkParser {

    /**
     * The `oobCode` from [url], or null if [url] is not a reset link this app
     * should act on.
     *
     * Everything has to line up: `https`, [expectedHost], [expectedPath],
     * `mode=resetPassword` and a non-blank `oobCode`. Anything else is null —
     * an App Link filter can be matched by a crafted URL, so the parser
     * re-checks the host and path rather than trusting that it was only
     * reached legitimately.
     *
     * @param expectedHost compared case-insensitively, as DNS names are.
     * @param expectedPath compared exactly, give or take one trailing slash.
     */
    fun oobCodeFrom(url: String, expectedHost: String, expectedPath: String): String? {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }

        if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        if (!expectedHost.equals(uri.host, ignoreCase = true)) return null
        if (!pathMatches(uri.path, expectedPath)) return null

        // getRawQuery, so a percent-encoded separator inside a value cannot
        // split a parameter in two: each value is decoded only after the split.
        val params = queryParams(uri.rawQuery ?: return null)

        if (params[PARAM_MODE] != MODE_RESET_PASSWORD) return null
        return params[PARAM_OOB_CODE]?.takeIf { it.isNotBlank() }
    }

    /** Tolerates one trailing slash, which some mail clients add. */
    private fun pathMatches(path: String?, expectedPath: String): Boolean {
        val actual = path?.removeSuffix("/").orEmpty()
        return actual == expectedPath.removeSuffix("/")
    }

    /**
     * Splits a raw query into decoded pairs, **first occurrence winning**.
     *
     * First rather than last so a second `mode=` appended to a legitimate link
     * cannot override the one Firebase put there. A pair with no `=`, or an
     * empty name, is dropped rather than treated as a flag.
     */
    private fun queryParams(rawQuery: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val name = decode(pair.substring(0, separator))
            if (name.isEmpty() || params.containsKey(name)) return@forEach
            params[name] = decode(pair.substring(separator + 1))
        }
        return params
    }

    /** A malformed escape is treated as no value at all, never as a crash. */
    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: IllegalArgumentException) {
            ""
        }

    private const val SCHEME = "https"
    private const val PARAM_MODE = "mode"
    private const val PARAM_OOB_CODE = "oobCode"

    /**
     * The only mode this app handles. Firebase also sends `recoverEmail` and
     * `verifyEmail` to the same action URL, and neither has a screen here, so
     * both are refused rather than half-handled.
     */
    private const val MODE_RESET_PASSWORD = "resetPassword"
}
