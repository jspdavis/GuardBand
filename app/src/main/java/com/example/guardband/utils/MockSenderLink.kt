package com.example.guardband.utils

import android.content.Intent
import com.example.guardband.BuildConfig

/**
 * The one place the way into the debug-only Mock Sender is spelled out.
 *
 * The harness lives in `app/src/debug` (`ui/mocksender/MockSenderActivity`),
 * so **`main` cannot name its class**: a release build would not compile.
 * The link is therefore an implicit [Intent] on [ACTION], which the debug
 * manifest declares, restricted to this app's own package so nothing else on
 * the phone can answer it.
 *
 * **Settings' Mock Sender row is the only way in (2026-11-27).** The harness
 * had its own launcher icon until then, which let a demo phone play the band
 * with no account; that was removed, so reaching the harness now means a
 * signed-in account past the verification and consent gates. If a no-account
 * route is ever wanted again, put the launcher filter back in the debug
 * manifest rather than adding a second door here, and say so in
 * `docs/mock-sender-and-receiver.md` §1.
 *
 * [isAvailable] is `BuildConfig.DEBUG` rather than a `PackageManager` lookup:
 * the harness exists exactly when the debug source set is in the build, and
 * the same constant already gates the one sanctioned log in
 * [com.example.guardband.ui.auth.GoogleIdTokenProvider].
 */
internal object MockSenderLink {

    /**
     * The action the debug manifest's intent filter declares. Its own name,
     * not a reused Android one, so only the harness can match it.
     */
    const val ACTION = "com.example.guardband.action.MOCK_SENDER"

    /** Whether this build contains the harness, and so whether to offer it. */
    val isAvailable: Boolean
        get() = BuildConfig.DEBUG

    /**
     * The intent that opens the harness, restricted to this app.
     *
     * Takes the package name rather than a `Context` so it is pure and
     * unit-testable, the same reason [HostedPages.url] takes its host.
     */
    fun intent(packageName: String): Intent =
        Intent(ACTION).setPackage(packageName)
}
