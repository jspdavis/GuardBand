package com.example.guardband.utils

import com.example.guardband.BuildConfig

/**
 * The URLs of the static pages served from the project's Firebase Hosting
 * domain.
 *
 * **The host is `BuildConfig.HOSTING_HOST`, the same one the password-reset App
 * Link uses.** It and the paths are declared once in `app/build.gradle.kts`;
 * nothing else in the app may spell out a URL on that domain. See
 * `docs/privacy-and-consent.md` and `docs/forgot-password.md`.
 *
 * Both pages are deliberately **outside** the app. Someone who has uninstalled
 * GuardBand, or who never had it, still needs to be able to read the notice and
 * find out how to get their data removed, so neither may be an in-app screen
 * only.
 *
 * [url] is pure and takes its host, so it is unit-testable and so a test can
 * check the joining rules without depending on `BuildConfig`.
 */
internal object HostedPages {

    /** The Privacy Notice. Linked from sign-up's consent step and from Settings. */
    val privacyNotice: String
        get() = url(BuildConfig.HOSTING_HOST, BuildConfig.PRIVACY_PATH)

    /**
     * "How to delete your account". Public because the in-app route needs a
     * working sign-in, and someone locked out needs the manual one.
     */
    val deleteAccount: String
        get() = url(BuildConfig.HOSTING_HOST, BuildConfig.DELETE_ACCOUNT_PATH)

    /**
     * `https://host/path`, with the slashes sorted out.
     *
     * Forgiving about the join rather than demanding one exact spelling of
     * each constant: a host that picked up a trailing slash or a path that
     * lost its leading one are both edits somebody will make in the build
     * file, and neither should produce a URL that 404s. A scheme already on
     * [host] is dropped, since the result always uses `https`.
     *
     * @return the URL, or just `https://host` when [path] is blank or `/`.
     */
    fun url(host: String, path: String): String {
        val bareHost = host
            .removePrefix("https://")
            .removePrefix("http://")
            .trim()
            .trimEnd('/')

        val barePath = path.trim().trim('/')
        return if (barePath.isEmpty()) "https://$bareHost" else "https://$bareHost/$barePath"
    }
}
