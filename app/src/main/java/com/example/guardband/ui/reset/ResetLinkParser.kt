package com.example.guardband.ui.reset

import java.net.URI

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
 * Their single source of truth is `resetLinkHost` / `resetLinkPath` in
 * `app/build.gradle.kts`: Firebase's own default action handler, which every
 * email action shares. That is why the `mode` check below is load-bearing, not
 * a formality.
 *
 * The scheme, host, path and query handling live in [EmailActionLink], shared
 * with the verify-email link so both are held to the same checks. They were
 * moved there unchanged; this parser's behaviour is what it was.
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
        val params = EmailActionLink.paramsFrom(url, expectedHost, expectedPath) ?: return null

        // The only mode this screen handles. A `verifyEmail` link is routed to
        // its own screen before it gets here (EmailActionLink.routeOf), and
        // any other mode is refused rather than half-handled.
        if (params[EmailActionLink.PARAM_MODE] != EmailActionLink.MODE_RESET_PASSWORD) return null
        return params[EmailActionLink.PARAM_OOB_CODE]?.takeIf { it.isNotBlank() }
    }
}
