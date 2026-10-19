package com.example.guardband.ui.reset

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * The checks every Firebase email-action link shares, and which screen one
 * belongs to.
 *
 * Firebase sends **every** email action - `resetPassword`, `verifyEmail`,
 * `recoverEmail`, `verifyAndChangeEmail` - to the same default handler,
 * `https://guardband-aae65.firebaseapp.com/__/auth/action`, and tells them apart
 * only by the `mode` query parameter. An intent filter cannot match on a query
 * parameter, so all of them open [ResetPasswordActivity], the one exported
 * Activity, and [routeOf] decides where each goes from there.
 *
 * **The reset path is not changed by this.** [paramsFrom] is the scheme, host,
 * path and query handling that used to sit inside [ResetLinkParser], moved here
 * verbatim so the verify-email link is held to exactly the same checks;
 * [ResetLinkParser] now calls it and adds its own `mode=resetPassword` and
 * `oobCode` checks, as before. `ResetLinkParserTest` is unchanged and still
 * passes, which is the proof.
 *
 * Takes a `String` and uses [URI], not `android.net.Uri`, so it is a plain JVM
 * test. **Nothing here logs**: the URL carries a single-use code.
 */
internal object EmailActionLink {

    /** Which screen a link belongs to. */
    enum class Route {

        /**
         * Today's path, untouched: [ResetPasswordActivity] and
         * [ResetLinkParser]. A real reset link, and also anything that is not
         * a well-formed link on the right host and path at all - those were
         * refused there before, and still are, word for word.
         */
        RESET_SCREEN,

        /** `mode=verifyEmail` on the right host and path. */
        VERIFY_EMAIL,

        /**
         * A well-formed link on the right host and path whose `mode` is one
         * this app does not handle (`recoverEmail`, `verifyAndChangeEmail`, ...).
         * Told plainly that the app does not support it, rather than that a
         * "reset link" is invalid.
         */
        UNSUPPORTED
    }

    /**
     * Where [url] should go. Decided by the **first** `mode`, the same rule
     * [ResetLinkParser] applies, so an appended `mode=` cannot reroute a
     * genuine link.
     */
    fun routeOf(url: String, expectedHost: String, expectedPath: String): Route {
        val params = paramsFrom(url, expectedHost, expectedPath) ?: return Route.RESET_SCREEN
        val mode = params[PARAM_MODE]
        return when {
            mode == MODE_VERIFY_EMAIL -> Route.VERIFY_EMAIL
            mode.isNullOrBlank() || mode == MODE_RESET_PASSWORD -> Route.RESET_SCREEN
            else -> Route.UNSUPPORTED
        }
    }

    /**
     * The `oobCode` of a verify-email link, or null. Everything has to line
     * up: `https`, [expectedHost], [expectedPath], `mode=verifyEmail` and a
     * non-blank code. The verify screen re-checks this itself rather than
     * trusting that it was only ever started with a routed link.
     */
    fun verifyEmailCodeFrom(url: String, expectedHost: String, expectedPath: String): String? {
        val params = paramsFrom(url, expectedHost, expectedPath) ?: return null
        if (params[PARAM_MODE] != MODE_VERIFY_EMAIL) return null
        return params[PARAM_OOB_CODE]?.takeIf { it.isNotBlank() }
    }

    /**
     * The decoded query of [url] if it is `https` on [expectedHost] at
     * [expectedPath], else null.
     *
     * @param expectedHost compared case-insensitively, as DNS names are.
     * @param expectedPath compared exactly, give or take one trailing slash.
     */
    fun paramsFrom(url: String, expectedHost: String, expectedPath: String): Map<String, String>? {
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
        return queryParams(uri.rawQuery ?: return null)
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
    const val PARAM_MODE = "mode"
    const val PARAM_OOB_CODE = "oobCode"
    const val MODE_RESET_PASSWORD = "resetPassword"
    const val MODE_VERIFY_EMAIL = "verifyEmail"
}
