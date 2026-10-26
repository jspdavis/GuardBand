# Handoff

**As of 2026-10-20 · `mvvm-main`, pushed, with
`feature/alerts-into-track`, `feature/track-bottom-sheet`,
`feature/maptiler-tiles`, `feature/rules-and-logo`,
`feature/credit-overlap-fix`, `feature/track-profile-marker`,
`feature/alert-symbols-track-buttons`, `feature/alert-notifications` and
`feature/alert-place-names` merged (last code commit `c28c3bb`). `main` was
last merged at `feature/alert-symbols-track-buttons` and is behind ·
691 unit tests, 0 failures · run `git status -sb` to see
whether anything is unpushed. Pick up from §4, starting with the MapTiler
key: a developer without one sees "Map unavailable" on Track. Then
the `devices/` rules decision, which is waiting on Jul ([`database-rules.md`](database-rules.md)). Every branch written on
2026-10-14 is merged (§3), the avatar and profile fixes landed as `0b8db04`,
and the Alert tab is gone: its content is now Track's recent-alerts panel and
the Alert history screen (§3). The
developer checks every change on their own phone during each session
(CLAUDE.md, "Working agreements"), so the device checklists below are a
reference, not a queue to chase.**

**Where the 2026-10-19 session stopped.** Everything is merged and pushed;
nothing is in flight. The open threads, all in §4: the device checks of the
face marker, the alert symbols and the Track buttons; two small design
questions (match Contacts' and Profile's top buttons to Track's, and a
battery icon for low battery); Jul's MapTiler key; and Jul's `devices/` rules
decision, with `RTDBS-RULES.md` still ahead of the Console. **Then alert
notifications landed** (`feature/alert-notifications`): the release manifest
gained three permissions and is **waiting for the owner's approval**, and
none of its device checks has been run (§4). **On 2026-10-20** the inbox got
a sample row (testing only, §4 small cleanups), alerts got place names
(`feature/alert-place-names`, a new notice row), and Contacts' and
Profile's top buttons were matched to Track's. All pushed; `main` not
updated.

The core build is in place for now, and its backend is live: Hosting, the
Security Rules and the password-reset App Link were all deployed and checked on
2026-10-11. Most of the UI has still not had a human pass on a device. This
file is the **backlog**: what is next, what is waiting on a person, and the
traps worth knowing before you start. What is already built is documented in
the files under §2, not here.

---

## 1. Where things stand

| Area | State |
|---|---|
| **Firebase Hosting** | Live at `guardband-aae65.web.app`: a root page, `/privacy`, `/delete-account`, `/.well-known/assetlinks.json`. Deploy from `hosting/` (§6). |
| **Privacy Notice + delete page** | **Published as final** on 2026-10-24 (notice 1.2, no DRAFT banner): the team chose to publish without the adviser review. Every claim was re-checked against the code that day, including the new Delete account path and the deletion marker. [`privacy-and-consent.md`](privacy-and-consent.md) §2. |
| **Password reset** | The App Link claims Firebase's own handler, `guardband-aae65.firebaseapp.com/__/auth/action`, because the Console action URL cannot be changed. Verified on a device; manual pass passed. |
| **Security Rules** | Deployed 2026-10-11. **Since 2026-10-19 [`../RTDBS-RULES.md`](../RTDBS-RULES.md) is ahead of the Console**: an append-only `history` (Option A+), pending Jul's decision, not deployed; `users/` unchanged. Inventory, options, Playground tests, rollout and rollback text: [`database-rules.md`](database-rules.md). |
| **Legacy nodes** | `password_resets`, `phone_index`, `email_index`: rules removed, data exported and deleted. |
| **Firebase Analytics** | Removed. The notice says the app has no analytics; adding any back means changing the notice in the same commit. |
| **Manual pass, 2026-10-11** | Consent, forgot password, Delete account: **passed**. Alert (now Track's recent-alerts panel and Alert history), Track, mock sender: **with a teammate** (§4). Delete account has **moved and changed since** (Account information, the deletion marker), so it needs passing again (§4). |
| **Account safety, 2026-10-14** | Delete account lives in **Settings → Account information**, in a danger zone at the bottom. A half-finished deletion leaves `users/{uid}: { deletion_pending: true }` and resumes on the next sign-in; **no rules change** (checked in the emulator). Email/password accounts are sent a **verification email**; the link is handled in the app through the existing App Link. **Unverified password accounts are locked out** of everything behind Home until they verify (a client-side gate; Google accounts exempt). |
| **Teammate UI pass, 2026-10-13** | `f34487b` (Jehan's redesign) did not compile; fixed and built on in `b05d769`, with real GPS in the mock sender from `c045738`. Everything that changed, and what is still open, is in [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md). **None of it has had a phone pass yet** (§4). |
| **Map tiles, 2026-10-19** | **MapTiler** raster tiles (Streets, 256 px) replace OpenStreetMap's public server, merged from `feature/maptiler-tiles` (`1ca8177`). The key lives in each machine's `local.properties` as `MAPTILER_API_KEY`; without it the build still succeeds and the map says "Map unavailable" and requests nothing. Credits shown bottom right; the Free plan's **MapTiler logo** bottom left since `feature/rules-and-logo` (never collapses). [`osmdroid.md`](osmdroid.md) §4. |
| **Release manifest, 2026-10-19** | Checked at the merge stage (`:app:processReleaseManifest`), last at `68055fb`, again on `feature/maptiler-tiles`, `feature/rules-and-logo` and `feature/credit-overlap-fix` (unchanged each time), and on `feature/alert-notifications`, which **added three**: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`, plus the `AlertWatchService` entry. **Awaiting the owner's approval** (§4). Still **no location permission** and no mock sender; the App Link entry is byte-identical to before email verification. The others: `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_GSERVICES` (reCAPTCHA, via Firebase Auth), and the app's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. The notice's "no location permission" line is accurate for release; debug builds do ask. Re-run after any dependency change (§6). |

**Branches.** `mvvm-main` is ahead of `main`. Branch new work off `mvvm-main`
and merge back with `--no-ff`, as `feature/trust-gaps` was. CLAUDE.md's branch
table now includes `main`.

---

## 2. Read these first

| For | Read |
|---|---|
| The agent's working rules, conventions, gotchas | [`../CLAUDE.md`](../CLAUDE.md) |
| **Running a demo with no hardware** | [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md): both launchers, end to end |
| The map library and its configuration | [`osmdroid.md`](osmdroid.md): **read before touching the map** |
| How alerts are read from RTDB | [`alert-data.md`](alert-data.md) |
| The sign-up wizard | [`signup-flow.md`](signup-flow.md) |
| The post-login host and its tabs | [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md) |
| The in-app password reset | [`forgot-password.md`](forgot-password.md) |
| Email verification: the link, Account information's status and Resend | [`email-verification.md`](email-verification.md) |
| Account information and Delete account | [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md) and [`../CLAUDE.md`](../CLAUDE.md), "Account deletion" |
| The 2026-10-13 teammate UI pass: what was fixed, what to check | [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md) |
| Privacy Notice, consent versions, hosted pages | [`privacy-and-consent.md`](privacy-and-consent.md) |
| The alert payload contract | [`../SCHEMA.md`](../SCHEMA.md): **authoritative** |
| Security Rules | [`../RTDBS-RULES.md`](../RTDBS-RULES.md): the source of truth; [`database-rules.md`](database-rules.md): every path, the `devices/` options, the Playground tests, rollout |

---

## 3. Done, and in flight

### Done

- **Real GPS in the mock sender.** Shipped by jspdavis in `c045738`
  (2026-10-13). The mock sender reports **Phone A's own fix** through
  `LocationManager`, asks for location at runtime and falls back to the random
  Cebu City point if refused. The permission is declared only in
  `app/src/debug/AndroidManifest.xml`, so release builds have none, and
  CLAUDE.md's rule now reads "no phone-location permission in the **main** app"
  with that file as the one named exception. The Privacy Notice stays true.
  Still open: testing both permission answers on Phone A (§4). Details:
  [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md).
- **Quick cleanups (2026-10-14).** Button strings back to normal case with
  `textAllCaps` doing the capitals (`cd_back` stays plain for TalkBack); the
  two hard-coded greens are colour tokens; `reset_success` and `welcome` are
  WebP at screen size; the unused `label_dashboard_*` / `label_your_contacts`
  strings are gone; `main` is in CLAUDE.md's branch table. All tests passed.
- **Check-in status (FR-10).** The Check-in pill on Track is now a read-only
  status of the latest check-in, and the debug mock sender can send one. The
  main app never writes a check-in. See CLAUDE.md, "Check-in status", and
  `SCHEMA.md`. **The band firmware still has to send the same payload** (§4).
- **Launcher icon.** The 2026-10-13 icons came from a PowerShell script that was
  not committed and is gone. To change the logo, use Android Studio's Image
  Asset tool with `logo_mark.png`, logo on white, no monochrome layer.
- **Small cleanups 2** (`457ec8b`). `BandStatus` reads `AlertFormatting`'s
  ISO-8601 patterns; the unreachable `SignUpNameViewModel.onGoogleSignInClicked`
  is gone; `item_contact.xml` has a stand-in pencil (`ic_edit`, `DESIGN: Jul`).
- **Google photo on the Track avatar** (`b75156b`). Coil 2.7.0, pinned; the pure
  `AvatarPhoto` picks the photo (Google sign-in only, `https` only), the default
  icon is placeholder and fallback. Both now live in `ui/avatar/` and the
  Profile tab shows the same photo through the shared `AvatarImage` binder,
  which also clips to the circle and drops the square background behind a
  photo (it showed as a square for a moment on a new Google account). The avatar opens the Profile tab (`0c5ef4d`),
  which also added the **Changelog** under Settings.
- **Account safety** (`68055fb`):
  - **Delete account moved** into a new Account information screen
    (`ui/account/AccountActivity`, Settings → Account information), in a
    red-outlined danger zone at the bottom. Every safeguard came with it. The
    inert Account information card on the Profile tab was removed.
  - **Deletion-pending marker.** Step 2 replaces `users/{uid}` with
    `{ "deletion_pending": true }` in one write. `ProfileProvisioning` never
    re-provisions over it; `HomeViewModel`'s gate sends the account back to
    Account information to finish, with Log Out offered beside Retry. No
    Security Rules change: verified in the local emulator against the stripped
    `RTDBS-RULES.md`. The marker outlives a finished deletion (a uid and one
    boolean; no session is left to clear it).
  - **Email verification.** Sent once after an email/password sign-up, best
    effort. The link reaches `VerifyEmailActivity` through the existing App
    Link (`EmailActionLink.routeOf`); reset links behave exactly as before,
    other modes say "isn't supported". Account information shows Verified /
    Not verified with Resend on a 60 s cooldown. [`email-verification.md`](email-verification.md).
- **Verification gate** (`feature/verification-gate`). An unverified
  email/password account meets **Verify your email** instead of the tabs, until
  it verifies; Google accounts are exempt. Client-side only: a rule requiring
  `email_verified` on `users/$uid` would break sign-up.
  [`email-verification.md`](email-verification.md) §8.
- **Consent gate** (`feature/consent-gate`). An account with no
  `users/{uid}/consent` (made in the Console, or a Google account whose record
  went missing) meets the consent step before the tabs: sign-up's own
  statement and notice link, and Agree writes the same record at the same
  `CONSENT_VERSION`. Decline signs out and changes nothing. The Home host now
  reads the record before showing the tabs, and a failed or slow read (10 s)
  is a Retry cover rather than a way in. No rules change (emulator-checked).
  [`privacy-and-consent.md`](privacy-and-consent.md) §8.
- **Alerts folded into Track** (`feature/alerts-into-track`, 2026-10-19). The
  bottom bar is **three icons: Track, Contacts, Profile**; the Alert tab is
  gone. Track shows the newest three alerts in a draggable bottom sheet over
  the map, and its **See all** opens **Alert history**, the old
  tab's screen as a pushed screen in the Home host (bar hidden, Back to
  Track, behind the same gates as the tabs). Both read one host-scoped
  `AlertViewModel` (`activityViewModels`), so there is still one history
  listener. The bell is unchanged: it still opens Notifications. A restored
  tag naming the removed tab falls back to Track (`HomeDestinations`). No
  change to `AlertParser`, the repository, the payload, the rules or the
  gates. [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md),
  [`alert-data.md`](alert-data.md) §7 and §8.
- **MapTiler map tiles** (`feature/maptiler-tiles`, `1ca8177`, 2026-10-19).
  The Track map draws MapTiler's Streets raster tiles; nothing requests
  `tile.openstreetmap.org`. The key is `MAPTILER_API_KEY` in each machine's
  `local.properties` (without it: "Map unavailable", no requests). The credits
  show bottom right and collapse to an ⓘ after 5 s or the first pan, per the
  OSMF guidelines. Still open: Jul's key, a phone check with the key, and
  asking MapTiler about the collapse (§4).
  [`osmdroid.md`](osmdroid.md) §4.
- **MapTiler logo** (`feature/rules-and-logo`, 2026-10-19). Bottom left of
  the Track map, in the credits' row, 20 dp tall, linked to maptiler.com,
  and it **never collapses**. The row grew from 18 to 24 dp and the ⓘ from
  40 to 48 dp; the check-in pill and the buttons above it sit 14 dp higher.
- **Credit and logo on narrow screens** (`feature/credit-overlap-fix`,
  2026-10-19). When the credit does not fit beside the logo, it moves onto
  its own line above it (wrapping at the largest font sizes); the pill rises
  only for a wrapped credit. Checked on a phone at five width and font-scale
  combinations ([`osmdroid.md`](osmdroid.md) §4.6).
- **The band's marker is a face** (`feature/track-profile-marker`,
  2026-10-19). The Track marker is a round picture in a white ring with a
  pointer whose tip is the band's position: the signed-in user's Google photo,
  or their initials on brand green (email accounts, offline, a failed load).
  It reuses `AvatarPhoto` and Coil; `TrackViewModel.markerFace()` is the one
  place that decides whose face it is, for when roles exist. Seen on a phone
  with a Google account; the rest of its device checks are in §4.
  [`osmdroid.md`](osmdroid.md) §5.4.
- **Alert symbols and Track buttons** (`feature/alert-symbols-track-buttons`,
  2026-10-19). Alert rows and the latest-alert card show a symbol in the
  type's colour instead of a dot: a red octagon with "!" for a panic, an
  orange warning triangle for low battery, a green tick for a check-in, a
  grey "?" for an unknown type (`AlertFormatting.typeIcon`;
  [`alert-data.md`](alert-data.md) D1). Track's four round buttons are all
  36 dp, and the bell and gear are 16 dp from the sides, so the bell lines up
  with Layers and Navigate. Contacts and Profile were left at 40 dp / 24 dp,
  and matched to Track on 2026-10-20 (36 dp, 16 dp from the sides).
- **Alert notifications** (`feature/alert-notifications`, 2026-10-19, merged,
  not pushed). Settings → Alert notifications (off by default) starts a
  `specialUse` foreground service that keeps the history listener open and
  posts new alerts as system notifications, including in the background and
  with the phone locked. No server, no FCM, nothing paid. Panic is silent
  (one buzz, no heads-up) for the wearer's discretion; nothing on a
  notification names a place or a person; the lock screen's public version
  says only "New alert from your band". A tap opens Alert history through
  the Home gate. Three new release permissions; the notice has a paragraph
  (since 1.2). [`notifications.md`](notifications.md).
- **Place names on alerts** (`feature/alert-place-names`, 2026-10-20). Every
  alert row, Track's panel and the latest card say "Street, Barangay, City"
  instead of latitude and longitude, from Android's `Geocoder` (no key, no
  dependency, no permission; Google's service on Play-services phones). One
  lookup per point rounded to about 11 m, names in memory only, coordinates
  until a name arrives or when none exists. A new flow of the band's
  positions to Google's geocoder, so the notice has a row (since 1.2).
  [`alert-data.md`](alert-data.md) §7.
- **Alert rows redesigned** (2026-10-20). One binder, `AlertCardViews`, for
  the rows and the latest card: the type's symbol on a pale circle of its
  colour, a pale red card with a red outline for a panic, the place as the
  main text with a pin, and the battery with one of five new battery
  symbols (orange at FR-11's 20%, a bolt while charging). The panel and
  the latest card say "5 min ago"; Alert history has day headings (Today,
  Yesterday, a date) over clock times. Times age on a 30 s ticker.
  [`alert-data.md`](alert-data.md) §8.
- **Settings tidy-up** (2026-10-20). Account information and Changelog got
  the white background the other screens already had (Account's details card
  lost its shadow, as Settings' did). On Settings a chevron now means
  "another screen of the app". Alert notifications became a chevron row
  opening its own screen, `ui/notifications/AlertNotificationsActivity`,
  which holds the switch, the blocked text and the battery row; the watch's
  ongoing notification opens that screen over Settings. The Privacy Notice
  moved into a new **About** screen (`ui/about/AboutActivity`: logo,
  version, and the notice as a row without a chevron, `SettingsRow.Action`).
  **Device information** (for the band, once pairing exists) is a
  placeholder row that says "Coming soon".
- **Dark mode** (2026-10-20). The theme is DayNight, chosen in Settings →
  **Display** (`ui/display/`): Use phone setting (default), Light or Dark,
  saved per phone and re-applied at every start by `GuardBandApplication`.
  `values-night/colors.xml` holds a placeholder dark palette (`DESIGN: Jul`);
  layouts use `surface_bg` / `surface_elevated` instead of `@color/white`.
  The Track map switches to MapTiler's Streets Dark, cached apart; the map
  credits and logo stay light chips. CLAUDE.md "Dark mode",
  [`osmdroid.md`](osmdroid.md) §4.8.

### In flight

Nothing: every branch written on 2026-10-14, `feature/alerts-into-track`,
`feature/track-bottom-sheet`, `feature/maptiler-tiles`,
`feature/rules-and-logo` and `feature/credit-overlap-fix` are merged and
pushed. Before trusting that, run `git branch --no-merged mvvm-main`; only
`main` and the MVP checkpoint branches should be listed.

`feature/maptiler-tiles` was merged on 2026-10-19 before any phone check with
a key. Part of that check happened the same day (§4); the rest is still open.
The logo followed in `feature/rules-and-logo`, and the narrow-screen fix for
the credit in `feature/credit-overlap-fix`.

**Rules proposal not deployed.** `RTDBS-RULES.md` is ahead of the Console
(Option A+, pending Jul). Do not describe it as the live ruleset until someone
has published it ([`database-rules.md`](database-rules.md) §7).

---

## 4. Backlog

### Waiting on a person

| | Who | Notes |
|---|---|---|
| **Phone pass of everything since 2026-10-13** | anyone with the phone | Section A of [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md): tab layouts, back arrows, gear/bell on every tab, the shorter icon-only bar, the Track avatar, the consent step, Welcome (once only), the reset success screen and the launcher icon. **Plus the 2026-10-14 changes:** every button label (capitals), the Welcome and reset-success images, the Check-in pill (starts empty, updates from the mock sender, not tappable), the contact pencil icon, the Google photo on the avatar (Google account: round photo; email account: default icon; airplane mode: default icon; switching accounts: no stale photo), the avatar opening Profile (the Track avatar was removed on 2026-10-19, so check the photo on Profile only), the Changelog. **And account safety** ([`email-verification.md`](email-verification.md) §7 for the email half): Delete account gone from Settings' own list and at the bottom of Account information, with the confirmation dialog; a new email/password sign-up gets a verification email and reads "Not verified"; the link opens the app (or the browser fallback) and the status turns "Verified" on return; Resend works once, then counts down; a Google account reads "Verified" with no Resend; the reset link still works; and a pending deletion resumes on the next sign-in instead of making a blank profile. **That last one is hard to produce with airplane mode** (the window between the two calls is milliseconds): set `users/{uid}/deletion_pending: true` on a throwaway account in the Console, then sign in. **And the consent gate:** [`privacy-and-consent.md`](privacy-and-consent.md) §6, steps 9 to 16. |
| **MapTiler key** | Jul | **Blocks the map on every machine without one.** One development machine had a key by 2026-10-19 (tiles rendered on a phone); whether it is the dedicated, restricted key below is not recorded. Create a dedicated GuardBand key in the MapTiler dashboard, restricted by **Allowed user-agent header** to `GuardBand/1.0 (+https://github.com/jspdavis/GuardBand)` (`OsmdroidConfig.USER_AGENT`), and give it to each developer privately for their own `local.properties` (`MAPTILER_API_KEY=…`, never committed; then Sync and rebuild). The logo is done (§3). [`osmdroid.md`](osmdroid.md) §4.4. |
| **Phone check of the MapTiler map** | whoever has the key | **Partly done 2026-10-19**, with a key, on a Realme RMX5111, during the credit-overlap size checks: MapTiler tiles render; the credit shows, collapses to the ⓘ after 5 s, and the ⓘ brings it back; the logo shows bottom left and stays when the credit collapses; the marker shows. **Still open:** tapping the two credit links and the logo (each should open its page); pan, zoom and follow behaving as before; logcat showing tile requests to `api.maptiler.com` only; and the zoom limit (22) against the style's `tiles.json`. [`osmdroid.md`](osmdroid.md) §8. Without a key it was checked on a phone on 2026-10-19: "Map unavailable", no crash. |
| **Ask MapTiler about the collapsing credit** | Jul | Before any public demo. Their guide says only that the text "must appear on every map"; the ⓘ collapse follows the OSMF guidelines, not a MapTiler statement. If they refuse, keep the credit always expanded ([`osmdroid.md`](osmdroid.md) §4.6 says how). |
| **Decide `devices/` and publish the rules** | Jul | Pick A, A+ (recommended), B or C ([`database-rules.md`](database-rules.md) §4); B breaks the mock sender and C breaks every receiver until pairing. Then follow §7 there: save the live rules, strip, run the §5 Playground tests, publish, check the app and the mock sender. Also ask the firmware teammate whether anything uses `devices/{id}/command` or `status`; if not, delete both blocks. |
| **Mock sender GPS on Phone A** | anyone with two phones | Allow location: alerts carry the phone's real position. Refuse: they fall back to the Cebu City point. Pre-grant location on any phone used for a demo, or the prompt appears mid-demo. |
| **Device check: alert symbols and Track buttons** | whoever has the phone | From `feature/alert-symbols-track-buttons`. The symbols on Track's recent-alerts sheet, on Alert history's rows and on its latest-alert card (send a panic, a low battery and a check-in from the mock sender); the four 36 dp buttons on Track, with the bell over Layers and Navigate. |
| **Approve the release manifest for alert notifications** | owner | `feature/alert-notifications` added `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` to the release build (§1's manifest row has the full list). Approve before any build is handed out; [`notifications.md`](notifications.md) §5 says why each one is there and what was left out. |
| **Device checks: alert notifications** | whoever has the phone | **None run yet.** The twelve steps in [`notifications.md`](notifications.md) §7: the prompt only from the switch, each type's wording and sound (the panic silent), background and locked delivery, Doze forced with `adb shell dumpsys deviceidle force-idle`, the lock screen with "hide sensitive content", the tap through the gate, swipe-from-recents and reboot on the Realme, Log Out stopping it, and logcat clean. Record the Realme's battery setting (§6 there) alongside step 4's result. [`notifications-test-cases.md`](notifications-test-cases.md) has the full 47 cases. |
| **Device check: place names on alerts** | whoever has the phone | From `feature/alert-place-names`. Send a panic from the mock sender: its row's coordinates turn into a place name in a moment, the same on the panel, Alert history and the latest card. Airplane mode on a fresh launch: coordinates, no crash; back online, names fill in within a minute of the next alert. Is the name right for the spot, and is it too long for a row? ([`alert-data.md`](alert-data.md) §12). |
| **Device check: dark mode** | whoever has the phone | Settings → Display → Dark: every screen turns dark at once, including the status bar icons (light) and the bottom bar; Back still lands on Settings. Walk every screen, sign-up and login included, for white patches, unreadable text, and badges or buttons that vanish. The Track map turns dark with no light grid flashing while tiles load, the credits and MapTiler logo stay readable, the pill and round buttons are grey. Use phone setting follows the phone's own switch while the app is open. Kill the app with Dark chosen on a light phone: it reopens dark, with no white flash on Splash. |
| **Design: the dark palette** | Jul | `values-night/colors.xml` is a placeholder on Material's dark guidance. Retune it there; also whether to use MapTiler's white logo on the dark map. |
| **Device check: alert row design** | whoever has the phone | The badges, the panic card's red, the pin and battery symbols, "Just now" turning into "1 min ago" with no new alert, Alert history's day headings, and no red outline leaking onto other rows while scrolling ([`alert-data.md`](alert-data.md) §12). |
| **One small design question** | Jul | Low battery uses the generic warning triangle; a battery-with-"!" icon would say what the warning is about. `DESIGN: Jul`. The rows now have battery symbols of their own (`ic_battery_*`, 2026-10-20); `ic_battery_critical` could stand in for the triangle, though the triangle's shape is what keeps the types apart without colour. Every new row size, shade and symbol is a placeholder marked `DESIGN: Jul`. (The other question, matching Contacts' and Profile's top buttons to Track's, was answered yes and done on 2026-10-20: all three tabs are 36 dp at 16 dp from the sides and 24 dp from the top. Check on the phone that the gear and bell no longer jump when switching tabs.) |
| **Device checks of the face marker** | whoever has the phone | From `feature/track-profile-marker`. **Seen:** a Google account's photo on the marker after a cold launch, above the collapsed sheet. **Still to check:** an email/password account shows initials; airplane mode on a fresh launch shows initials (or the cached photo) and does not crash; the pointer's tip on the same spot as the old pin's at the same coordinate and zoom (use the mock sender's fixed point, follow on); pan, zoom and follow unchanged with no stutter. [`osmdroid.md`](osmdroid.md) §5.4. |
| **Two app-wide theme changes** | Jehan + Jul | Confirm they are intended: outlined buttons went from 8 dp to 28 dp corners, and every sign-up screen's top padding from 96 dp to 24 dp (`themes.xml`, `f34487b`). |
| **Test Track (map and recent-alerts panel), Alert history and the mock sender** | teammate | Never had a full human pass. Checklists: [`mock-sender-and-receiver.md` §6](mock-sender-and-receiver.md), [`alert-data.md` §12](alert-data.md) and the 2026-10-19 block in [`home-host-and-bottom-nav.md` §12](home-host-and-bottom-nav.md). Pan-and-follow matters most, now with the panel over the bottom of the map: a drag that starts on the panel must not stop the map following. (The staleness badge is no longer on screen: the Track status panel was removed on 2026-10-14.) Before 2026-10-11 every history read was denied by the rules, so this has effectively never worked end to end until now. |
| **Band firmware sends check-ins** | band/firmware teammate | The app now reads the check-in payload defined in `SCHEMA.md` (FR-10: double-press within 2 seconds, no emergency alert). The band has to write exactly that. Until then only the mock sender produces check-ins. |
| **Each teammate's fingerprints** | each developer | Debug **SHA-1** in Firebase Console → Project settings (else Google sign-in fails on their build). Debug **SHA-256** added to `hosting/public/.well-known/assetlinks.json` and Hosting redeployed (else the reset and verification links open the browser; Firebase's own page still completes them). `assetlinks.json` holds **four** developers' since 2026-10-25 (James's and three teammates'); each of those three still runs `adb shell pm verify-app-links --re-verify com.example.guardband` on their own phone. Anyone else adds theirs. Release keystore's SHA-1 and SHA-256 before any signed build. |

### Engineering, roughly in order

| | Notes |
|---|---|
| **Close the world-readable, world-writable `devices/`** | Needs device pairing first: a device id per account, an auth token for the band, and `AlertSender` taught to send `?auth=`. Close it before any public demo; the Privacy Notice says plainly that it is open. |
| ~~**A real tile source**~~ | **Resolved by `feature/maptiler-tiles`** (2026-10-19): MapTiler raster tiles, no request to OSM's public server. What is left: MapTiler's **Free plan is for non-commercial use** and **pauses at its monthly limit** (tiles stop until the next period rather than being billed), the key ships inside the APK with only the user-agent restriction and the cap protecting it, and a release would want a paid plan. See `osmdroid.md` §4 and §7. |
| **Navigate button** | Since 2026-10-19 it is the round arrow button on Track (tap = Navigate, long-press = re-centre); the white pill is gone, and what it opens is unchanged. Parked 2026-10-14. It opens Google Maps for the pinned area. Before changing it: confirm it sends the band's latest reported position (not the map centre or a fixed point); use a standard `geo:` link with a web fallback and catch "no app found"; disable it with no fix; show the age of a stale position beside it. Do it after the Track changes are merged, since both touch the Track layout and ViewModel. |
| **Rename `applicationId` off `com.example.*`** | Only if publishing: Play rejects the prefix and it cannot change after a first release. Means re-registering the Firebase app, every SHA-1, and the `assetlinks.json` package name. |
| **No re-consent flow** | Users who accepted `1.0` keep `1.0`. Building one means deciding what happens to an account that declines. |
| **Server-side enforcement of verification, if ever wanted** | The app is gated on verification for password accounts, **client-side only** (`email-verification.md` §8). A rule on `users/$uid` requiring `auth.token.email_verified` would fail every sign-up, because the profile is written before the email can be opened. If a rule ever does read the claim, force a token refresh after verifying. |
| **Sweep finished deletions' markers** | Optional. Each completed Delete account leaves `users/{uid}: { deletion_pending: true }` under a uid no account owns. Harmless, but a human can delete them from the Console now and then. |
| **The layers button is a stub** | D7. The Check-in pill is a read-only status of the latest check-in (CLAUDE.md, "Check-in status"); check-in is FR-10's band double-press and the app must never send one. |
| **A Terms page, only if the course needs one** | The Privacy Notice already covers what users agree to. Adding Terms means a hosted `/terms` page, a `termsPath` + `HostedPages.terms`, and a `CONSENT_VERSION` bump because what is agreed to changes. |

### Small cleanups

- `item_contact.xml`'s pencil (`ic_edit`) is a stand-in; the real icon comes
  from Figma.
- Coil's disk cache (the Google photo) is not cleared on Log Out or Delete
  account.
- **Optional**: give the mock sender its own launcher icon; today it inherits
  the app's, so the two differ only by label.
- One manual check never done: confirm RTDB traffic stops when the listener
  should go (`awaitClose` removing it). Note the alerts listener lives as
  long as the Home host, in `viewModelScope`, so **backgrounding alone does
  not stop it**; Log Out does ([`alert-data.md`](alert-data.md) §9). A code
  read does not replace this.
- **Remove the sample inbox row** (2026-10-20, testing only): the
  Notifications inbox shows a static "Welcome to GuardBand" card
  (`cv_notifications_welcome` in `fragment_notifications.xml`, two
  `label_notifications_welcome_*` strings) and hides the empty text. Delete
  the card and the strings and make `tv_notifications_empty` visible again
  when the inbox gets real data.
- Refresh this file after each merge: commit hash, test count, and the lines
  the "In flight" table says to remove.

---

## 5. Needs a human, not an agent

- **Changing the Security Rules.** Edit `RTDBS-RULES.md`, strip every `"//"`
  key, paste the whole file over the Console's, run the Playground tests in
  [`database-rules.md`](database-rules.md) §5 (or the emulator recipe in §6),
  publish. Copy the live rules out first. The steps are §7 of that file. The
  deletion-pending marker needed **no** rule. If anyone ever adds
  `"$other": { ".validate": false }` at `users/$uid`, give `deletion_pending`
  a rule of its own in the same change, or Delete account fails at step 2.
- **Registering fingerprints** (§4).
- **Firebase Console settings.** Email templates' sender name and subject can
  be edited (this covers the verification email too); the action URL cannot on
  this project, so leave it at the default.
- **Reviewing the Privacy Notice** before its banner comes off. It describes
  how real people's data is handled, including emergency contacts who never
  signed up, and it was drafted by an agent from the code: a reason for a
  human to read it, not a reason to trust it.
- **Figma** is the visual source of truth and is Jul's. `DESIGN: Jul` marks the
  spots the agent left as placeholders.
- **The check-in payload** is a contract with the band firmware: the agent can
  propose it, a human agrees it.

---

## 6. Building, deploying and verifying

**Build and test.**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # JDK 17
./gradlew assembleDebug testDebugUnitTest
./gradlew installDebug                                           # to the USB phone
```

**The map needs a MapTiler key** in your own `local.properties`
(`MAPTILER_API_KEY=YOUR_MAPTILER_API_KEY`; ask Jul). Without it everything
builds and the Track map reads "Map unavailable".

The `java` on PATH is JDK 25, and Gradle 8.4 cannot run on it. Set
`JAVA_HOME` explicitly; it is not reliably inherited. Run **one** Gradle build
at a time: two concurrent builds write the same `app/build/tmp/kotlin-classes/`
and fail with a bare directory listing that looks like a compile error.

To build a commit without disturbing a dirty tree, use a throwaway worktree;
copy `local.properties` in (it is gitignored), and remove it with PowerShell +
`robocopy /MIR` from an empty directory, because `git worktree remove` fails
with "Filename too long" on Gradle output.

**Check what the release build asks for.** After any dependency change:

```bash
./gradlew :app:processReleaseManifest
```

Then read the merged `AndroidManifest.xml` under
`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/`. It merges without
compiling or signing, and nothing after it (R8, packaging) adds permissions.
Expect only the four permissions in §1 and no location permission.

**Deploy Hosting.** From `hosting/`, never `firebase init`:

```bash
firebase deploy --only hosting --project guardband-aae65
```

Then confirm `/.well-known/assetlinks.json` returns `application/json` on both
`guardband-aae65.web.app` and `guardband-aae65.firebaseapp.com`.

**Check the App Link on a device.** `adb` is not on PATH; it is in the SDK's
`platform-tools`:

```bash
adb shell pm get-app-links com.example.guardband        # want: verified
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE \
  -d "'https://guardband-aae65.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=x'"
```

The second should open `ResetPasswordActivity` directly, then report the
dummy link as invalid. The same command with `mode=verifyEmail` should open
`VerifyEmailActivity` instead and say the link "has already been used" (a dummy
code fails Firebase's check); `mode=recoverEmail` should say the link "isn't
supported".

**Testing email flows with one Gmail inbox.** `yourname+test1@gmail.com`,
`yourname+test2@gmail.com` and so on all arrive in the same inbox, so each can
be a separate test account. Firebase's default sender sometimes lands in spam;
check there first.

**Test rules without touching production.** The project has no emulator suite,
but the CLI's Database Emulator works ad hoc from a scratch directory: a
`firebase.json` pointing `database.rules` at a stripped copy of
`RTDBS-RULES.md`, then `firebase emulators:exec --only database --project
demo-guardband "node test.mjs"`. Use a `demo-` project id so nothing can
reach the real project. Fake users are unsigned JWTs passed as `?auth=`;
`Authorization: Bearer owner` bypasses rules for seeding. See §7 for its traps.

**Not covered by tests:** `FirebaseAlertRepository`'s listener and whether
`awaitClose` removes it, the Firebase calls in auth, reset, verification and
the deletion marker's write, the `MapView`, and anything in the UI layer. `ExampleInstrumentedTest` is still the template.

---

## 7. Traps that have already cost time

**Firebase**

- **The Console's password-reset action URL cannot be changed** on this
  project: `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED`, from the Console and the API
  alike. The app claims the default handler instead, with an exact
  `android:path`, because `/__/auth/handler` shares the host.
- **A child's `.read` never grants the parent.** `history/$sequenceId` alone
  let each entry be read but denied the list, which 401'd the Alert tab (now Alert history) and the
  mock sender until 2026-10-11. `history` now has its own `.read`.
- **The Console rejects `RTDBS-RULES.md`'s `"//"` keys.** Strip them before
  pasting.
- **Firebase Hosting's default `ignore` drops `.well-known/`.** The committed
  `firebase.json` omits that glob on purpose; don't tidy it back.
- **Hosting caches a 404 for an hour** (`max-age=3600`). A page that was
  missing on first visit keeps showing "not found" after a deploy; hard-refresh
  or open a private tab.
- **The emulator needs Java 21+**, the opposite of Gradle: unset `JAVA_HOME`
  so the PATH's Java 25 is used. On Windows its `java` process outlives
  `emulators:exec` and holds port 9000. And rules from `firebase.json` apply to
  the `<project>-default-rtdb` namespace, so querying `?ns=<project>` silently
  runs with open rules; load them with a `PUT /.settings/rules.json` instead.
- **A new library can add permissions silently.** Re-run
  `:app:processReleaseManifest` after any dependency bump (§6).

**Android and the build**

- **A view removed from a layout with only its `findViewById` line deleted**
  crashes at runtime with `UninitializedPropertyAccessException`, not at
  compile time. It happened twice. Remove the field and every use of it.
- **An unescaped apostrophe in `strings.xml`** fails `mergeDebugResources`
  naming neither the string nor the line. Write `\'`.
- **A `--` inside an XML comment** fails the same task with a
  `SAXParseException`.
- **A duplicated `<string name="…">`** fails with "Found item String/x more than
  one time". Grep first.
- **`README.md` is UTF-16 LE with CRLF.** A naive script read collapses its
  CRLFs into a whole-file change that git shows only as `Bin … differ`. Read it
  with `newline=''`.
- **A `StateFlow` drops an emission equal to the previous one.**
  `FakeAlertRepository` uses a replaying `MutableSharedFlow` for that reason.
- **Don't "simplify" `AlertParser` into `getValue(Alert::class.java)`.** It
  reintroduces the `isCharging` → `charging` mapper bug, silently. Any new
  parser (the check-in one included) has the same exposure for `is…` fields.
- **Don't turn `TrackViewModel`'s injected ticker into an internal
  `while (true) { delay(…) }`.** Tests calling `advanceUntilIdle()` then hang
  instead of failing.
- **A ViewModel that launches in `init` breaks a test that builds it in a
  field initializer.** The field runs before `MainDispatcherRule` swaps in
  Main, so every test fails with "Module with the Main dispatcher had failed to
  initialize", and the leaked exception then fails an unrelated test class.
  Build it `by lazy` (as `DeleteAccountViewModelTest` now does) or inside the
  test.

**Git**

- **`git merge -F -` does not read stdin** ("could not read file '-'"). Write
  the message to a file. `git commit -F -` does.