package com.example.guardband.ui.changelog

/**
 * One milestone in the app's progress: a day on which work landed on
 * `mvvm-main`.
 *
 * @param date    ISO `yyyy-MM-dd`, the day the work landed. Parsed only for
 *   display and for `ChangelogTest`'s
 *   ordering check.
 * @param title   What the milestone was, in a few words.
 * @param changes One line per change, written for the team and the
 *   instructor rather than for an end user.
 * @param commit  The short hash of the last commit of the milestone on
 *   `mvvm-main`, so a line can be traced back with `git show`.
 */
data class ChangelogEntry(
    val date: String,
    val title: String,
    val changes: List<String>,
    val commit: String
)

/**
 * The app's progress log, newest first, shown by [ChangelogActivity].
 *
 * Built from the `mvvm-main` history, grouped by the day work landed. The
 * app has no release versions yet (`versionName` is still 1.0), so a date is
 * the honest label.
 *
 * **Kept in Kotlin rather than `strings.xml`** because it is a document that
 * grows with the code, not screen copy: one entry per milestone, appended in
 * the same commit as the work it describes, and checked by `ChangelogTest`.
 * When something merges into `mvvm-main`, add or extend the entry for that
 * day at the top.
 */
object Changelog {

    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            date = "2026-10-19",
            title = "Alerts move onto the Track tab; Track's controls tidied; MapTiler map tiles and logo; Settings tidied",
            changes = listOf(
                "The bottom bar has three icons: Track, Contacts and Profile. The Alert tab is gone.",
                "Track shows the newest three alerts in a panel over the bottom of the map, which can be collapsed.",
                "See all on that panel opens Alert history, the full list that used to be the Alert tab. Back returns to Track.",
                "The panel and Alert history share one alerts reader, so they always agree and the database is read once.",
                "The recent-alerts panel is a bottom sheet you drag up and down; collapsed it shows only its header.",
                "The white Navigate pill is gone. The round arrow button navigates on a tap and re-centres the map on a long-press.",
                "Track's top bar is Settings on the left and Notifications on the right, like the other tabs; its profile picture is gone.",
                "The Contacts tab's icon is a phone.",
                "The Track map's tiles come from MapTiler instead of OpenStreetMap's public servers. Each developer keeps the key in local.properties; without one the map says \"Map unavailable\" and loads nothing.",
                "The map shows its credits (© MapTiler © OpenStreetMap contributors, each a link), which shrink to an ⓘ button after 5 seconds or the first pan.",
                "The MapTiler logo, which MapTiler's free plan requires, sits bottom left of the map and links to maptiler.com. Unlike the credits it never shrinks away.",
                "The ⓘ button is a full 48dp touch target; the check-in pill above it moved up slightly to make room.",
                "On a narrow screen or with a large system font, the map credits move onto their own line above the MapTiler logo instead of running into it; the check-in pill only moves up if the credits need two lines.",
                "Settings has one privacy row, Privacy Notice; the \"Privacy & security\" placeholder, which only said \"Coming soon\", is gone.",
                "Settings, Contacts, Profile, Notifications and Alert history have a white background, and the Settings rows no longer sit in a raised card.",
                "Every screen with a top bar (Settings, Notifications, Alert history, Account information, Changelog) has a thin line under it.",
                "Database rules reviewed against every path the app uses (docs/database-rules.md). Proposed, not yet published: band history entries can be added but no longer overwritten or deleted. Whether to require sign-in to read the band's data is still to be decided.",
                "588 unit tests, up from 576 (the Track avatar's tests went with it)."
            ),
            commit = "2ed649b"
        ),
        ChangelogEntry(
            date = "2026-10-15",
            title = "Avatar and profile fixes",
            changes = listOf(
                "The Profile tab shows the same Google photo as the Track avatar; both share one avatar binder in ui/avatar.",
                "The photo is clipped to a circle and no longer flashes as a square on a new Google account.",
                "The band status panel (Reporting, Last seen, battery) was removed from the Track screen; the Check-in pill is its one status line.",
                "576 unit tests, up from 572."
            ),
            commit = "0b8db04"
        ),
        ChangelogEntry(
            date = "2026-10-14",
            title = "Google photo, check-in status, account safety, consent gate",
            changes = listOf(
                "The Track avatar shows the Google profile photo, cropped to a circle; email accounts keep the default icon.",
                "Coil 2.7.0 added as the image loader, pinned to an exact version. No new permission in the release manifest.",
                "The photo URL is chosen by a pure AvatarPhoto helper: Google sign-in only, https only, resized for the avatar.",
                "Tapping the Track avatar opens the Profile tab.",
                "Top-bar Settings and Notifications buttons set to an explicit 40dp on every tab.",
                "This Changelog, under Settings.",
                "The Check-in pill shows the wearer's latest check-in (\"Last check-in: 5 minutes ago\") instead of a toast. It is read-only: check-ins come from the band.",
                "Delete account moved into a new Account information screen (Settings), in a danger zone at the bottom.",
                "A half-finished account deletion now resumes on the next sign-in instead of bringing the account back empty.",
                "Email verification: new email/password accounts get a verification email; Account information shows Verified or Not verified, with Resend.",
                "The app handles the verification link itself; password-reset links work exactly as before.",
                "Email/password accounts must verify their email before using the app: a Verify your email screen replaces the tabs until they do. Google accounts are not affected.",
                "Consent gate: an account with no consent record (made in the Firebase Console, or a Google account whose record went missing) is shown the consent step before the app. Decline signs out and changes nothing.",
                "The app now checks the account's record before showing the tabs; if it cannot (offline), it offers Try again instead of letting the account in.",
                "572 unit tests, up from 396."
            ),
            commit = "372ce46"
        ),
        ChangelogEntry(
            date = "2026-10-13",
            title = "Polish, app icon and contact limits",
            changes = listOf(
                "UI fixes after the redesign, and the GuardBand logo as the launcher icon.",
                "Emergency contacts: minimum 1, maximum 3, since these are the people the band texts first. Add contact became a + button.",
                "Button text in sentence case so TalkBack reads words, not capitals.",
                "Hard-coded greens replaced by colour tokens; two illustrations converted to WebP, about 5.5 MB smaller.",
                "Contact rows got a pencil icon for Edit; dead strings and an unreachable Google shortcut removed."
            ),
            commit = "7fdcb16"
        ),
        ChangelogEntry(
            date = "2026-10-12",
            title = "UI redesign and real GPS in the mock band",
            changes = listOf(
                "Screens restyled to the new design, with new Consent and Welcome pages.",
                "The debug-only mock sender reports the phone's own GPS fix, standing in for the band's GPS module. The release app still asks for no location permission."
            ),
            commit = "f34487b"
        ),
        ChangelogEntry(
            date = "2026-10-11",
            title = "Account deletion, in-app password reset and privacy",
            changes = listOf(
                "Delete account in Settings: re-authenticate, remove the profile and contacts, then delete the sign-in, with Retry if a step fails.",
                "Password reset now opens in the app through an App Link, with a live checklist of the password rules.",
                "Privacy Notice and delete-account pages published on Firebase Hosting and linked from sign-up and Settings; consent version 1.1.",
                "Firebase Analytics removed: nothing used it, and it collected an advertising ID.",
                "Database Security Rules deployed, and the Alert history read fixed.",
                "Map tiles fixed: OpenStreetMap had been blocking the app's user agent."
            ),
            commit = "72b377d"
        ),
        ChangelogEntry(
            date = "2026-10-07",
            title = "Live map and the mock band",
            changes = listOf(
                "Track tab shows the band's last known position on an OpenStreetMap map (osmdroid), with Reporting / Not reporting, last seen, battery and Navigate.",
                "The mock sender writes alerts straight to the database the way the band will, and gained a walk mode that moves the marker.",
                "Security Rules drafted in RTDBS-RULES.md."
            ),
            commit = "73993e6"
        ),
        ChangelogEntry(
            date = "2026-10-06",
            title = "Live alerts and the new sign-up",
            changes = listOf(
                "Alert tab reads the band's history live: panic, check-in and low battery are colour-coded; tracking updates are hidden.",
                "Sign-up rebuilt as Account, Name, Contacts, Consent. Nothing is written until Consent, and consent is recorded with a version and time.",
                "The self-reported location field was dropped: the band's GPS is the real location.",
                "Philippine mobile numbers validated strictly, so the band never texts a number that cannot ring."
            ),
            commit = "01ae33d"
        ),
        ChangelogEntry(
            date = "2026-10-05",
            title = "Google sign-in, contacts and profile on Firebase",
            changes = listOf(
                "Continue with Google, using Android's Credential Manager.",
                "Emergency contacts and the profile stored in Firebase Realtime Database, read live.",
                "Phone numbers stored in one format (E.164, e.g. +639171234567) whichever way they are typed.",
                "Sign-up's final write is a single atomic step, so it cannot half-succeed.",
                "59 unit tests."
            ),
            commit = "48a3b44"
        ),
        ChangelogEntry(
            date = "2026-10-04",
            title = "MVVM rebuild and Firebase sign-in",
            changes = listOf(
                "Architecture rebuilt as MVVM: ViewModels with StateFlow, repositories behind interfaces.",
                "Theme: Poppins and Material 3 styles across the sign-in screens.",
                "Home screen with four tabs (Track, Contacts, Alert, Profile) plus Settings and Notifications.",
                "Email and password sign-in on Firebase Authentication; the session survives restarts.",
                "Forgot password sends Firebase's reset email, without revealing which addresses have accounts.",
                "First unit test suite: 32 tests."
            ),
            commit = "5f589e6"
        ),
        ChangelogEntry(
            date = "2026-09-22",
            title = "First MVVM draft",
            changes = listOf(
                "ViewModels and UI states drafted for the main screens. It did not compile, and was rebuilt on 4 October."
            ),
            commit = "e9af6d8"
        ),
        ChangelogEntry(
            date = "2026-09-09",
            title = "MVP prototype",
            changes = listOf(
                "First working structure in the MVP pattern: login, sign-up and a dashboard on in-memory data.",
                "Mock sender prototype, to stand in for the band during demos.",
                "SCHEMA.md: the alert payload the band will send (version 1.0)."
            ),
            commit = "81a954a"
        ),
        ChangelogEntry(
            date = "2026-09-01",
            title = "Project created",
            changes = listOf(
                "Android project set up and connected to the guardband-aae65 Firebase project."
            ),
            commit = "d2bd504"
        )
    )
}
