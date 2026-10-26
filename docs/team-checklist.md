# GuardBand team checklist

What is left to build, fix, check and decide on the Android app. Heavy
features come first; each item says who it is waiting on and where its
details live in the repo. The full record is [`handoff.md`](handoff.md) §4.

As of 20 Oct 2026 · `mvvm-main` at `9f23f06` · 727 unit tests passing.

**Types:** 🟥 heavy feature · 🟩 feature · 🔧 fix · 📱 phone check · 🗳️ decision / setup

---

## Heavy features

Both need the band hardware and touch the database rules, the firmware and
the app together. Pairing comes first: SMS depends on it.

- [ ] 🟥 **Device pairing** · *app team + firmware*
  Today every install watches one hard-coded band, `guardband-001`, and
  `devices/` in the database is open to anyone. Pairing links a band to an
  account.
  - A device id per account instead of `DeviceConstants.DEFAULT_DEVICE_ID`,
    and a way to add or remove a band in the app.
  - An auth token for the band, so it can write without the rules being open.
  - Close `devices/` in `RTDBS-RULES.md` and publish it; teach the mock sender
    to send `?auth=`.
  - Build the Device information screen (now a "Coming soon" row in Settings).
  - Decide roles: who wears the band and who watches it.

  Where: [`database-rules.md`](database-rules.md) §4 and §7, CLAUDE.md "Alert data" (D4)

- [ ] 🟥 **SMS alerts to emergency contacts** · *firmware + app team*
  The band (Air780E) texts the wearer's emergency contacts on a panic. The
  decision so far: SMS stays with the band, not the phone.
  - Get the contacts onto the band after pairing:
    `users/{uid}/emergency_contacts`, already stored in E.164 (`+639…`).
  - Agree the message wording and what it may include (a location link is
    sensitive).
  - If the phone ever sends SMS instead, that adds `SEND_SMS` to the release
    manifest and needs the owner's approval first.
  - The Privacy Notice already says the numbers are collected for this;
    re-read it if anything changes.

  Where: CLAUDE.md "Contacts and profile data", [`privacy-and-consent.md`](privacy-and-consent.md)

---

## Features to build

None of these needs the band.

- [ ] 🟩 **Change password screen** · *app team*
  The Settings row still says "Coming soon". Reuse the password-rules
  checklist (`PasswordCriteriaList`) and the re-sign-in step from Delete
  account. Password accounts only; a Google account has no password to change.
  Where: `ui/settings/`, `ui/auth/PasswordCriteriaList`, `ui/account/DeleteAccountViewModel`

- [ ] 🟩 **Notifications inbox with real data** · *app team*
  The bell's screen shows one sample "Welcome to GuardBand" card. Fill it with
  real entries (for example the alerts this phone was notified about), then
  delete the sample card and its two strings and show the empty text again.
  Where: `fragment_notifications.xml` (`cv_notifications_welcome`), [`handoff.md`](handoff.md) §4 small cleanups

- [ ] 🟩 **Navigate button polish** · *app team*
  Confirm it sends the band's last reported position (not the map centre),
  use a standard `geo:` link with a web fallback, and show how old the
  position is.
  Where: `ui/track/NavigationUrl`, [`handoff.md`](handoff.md) §4 engineering

- [ ] 🟩 **Layers button on Track** · *app team*
  Still a stub that shows a toast. It could switch map styles, now that the
  tile source supports more than one (light and dark).
  Where: `ui/track/MapTilerTileSource`, [`osmdroid.md`](osmdroid.md) §4.8

- [ ] 🟩 **Band sends check-ins and alerts** · *firmware*
  The app already reads the payload in `SCHEMA.md`. The band has to write
  exactly that: a double-press within 2 seconds is a `CHECKIN` (FR-10), and
  every alert goes to `history/{sequenceId}` first, then `latest`.
  Where: `SCHEMA.md`, [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md)

---

## Fixes and cleanups

- [x] 🔧 **Bring `main` up to date** · *done 20 Oct*
  `mvvm-main` was merged into `main` on 20 Oct, with all of that day's work
  (notifications, place names, alert rows, Settings changes, dark mode),
  before the phone checks below. Merge again after each batch lands.

- [ ] 🔧 **Mock sender lint errors** · *app team*
  `./gradlew lint` fails on two `StringFormatInvalid` errors:
  `mock_sender_gps_fix` passed to `String.format` in `MockSenderActivity.kt:284`.
  Debug-only, but it stops lint from passing.

- [ ] 🔧 **Clear the Google photo cache on sign-out** · *app team*
  Coil's disk cache keeps the profile photo after Log Out and Delete account.

- [ ] 📱 **Confirm the database listener stops** · *app team*
  Never checked by hand: database traffic stops after Log Out. Backgrounding
  the app does not stop it, by design.
  Where: [`alert-data.md`](alert-data.md) §9

- [ ] 🔧 **Placeholder icons** · *Jul, then app team*
  The contact pencil (`ic_edit`) is a stand-in, and the mock sender has no
  launcher icon of its own (it differs from the app only by its label).

---

## Phone checks

Built and unit-tested, never seen on a phone. Each doc has the exact steps.
Use the debug mock sender to produce alerts.

- [ ] 📱 **Dark mode**
  Settings → Display → Dark. Walk every screen, sign-up and login included,
  for white patches and unreadable text. The map turns dark; the credits and
  MapTiler logo stay readable. Close the app with Dark on a light phone: it
  reopens dark.
  Where: [`handoff.md`](handoff.md) §4

- [ ] 📱 **Alert notifications (47 cases)**
  The permission prompt only from the switch, delivery in the background and
  while locked, the panic notification silent, nothing sensitive on the lock
  screen, swipe-away and reboot on the Realme.
  Where: [`notifications-test-cases.md`](notifications-test-cases.md)

- [ ] 📱 **Alert rows and place names**
  Coordinates turn into a place name; the badges, panic card, pin and battery
  icons; "Just now" becoming "1 min ago"; day headings in Alert history; no
  red outline leaking while scrolling.
  Where: [`alert-data.md`](alert-data.md) §12

- [ ] 📱 **Map, marker and Track buttons**
  The credit links and logo open their pages; pan, zoom and follow; the face
  marker (initials for an email account, nothing breaks offline); the alert
  symbols and the 36 dp buttons.
  Where: [`osmdroid.md`](osmdroid.md) §5.4 and §8

- [ ] 📱 **Settings, About, Display, Alert notifications**
  Every row opens its screen and Back returns to Settings; About's Privacy
  Notice opens the hosted page; the watch notification opens the Alert
  notifications screen.
  Where: [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md)

- [ ] 📱 **Full pass since 13 Oct**
  The older backlog of checks: tab layouts, sign-up, email verification, the
  consent gate, Delete account resuming, the mock sender's GPS on a second
  phone.
  Where: [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md) section A, [`privacy-and-consent.md`](privacy-and-consent.md) §6

---

## Decisions and setup

- [ ] 🗳️ **Approve the release manifest** · *owner*
  Alert notifications added `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` and
  `FOREGROUND_SERVICE_SPECIAL_USE`. Approve before any release build is shared.
  Where: [`notifications.md`](notifications.md) §5

- [ ] 🗳️ **Pick the `devices/` rules and publish** · *Jul*
  Choose option A, A+ (recommended), B or C, run the Playground tests, then
  publish. `RTDBS-RULES.md` is ahead of the live rules until then.
  Where: [`database-rules.md`](database-rules.md) §4–§7

- [ ] 🗳️ **MapTiler key for each developer** · *Jul*
  A dedicated key restricted to the app's user agent, shared privately, in
  each `local.properties` as `MAPTILER_API_KEY`. Never committed. Also ask
  MapTiler whether the credit may collapse to the ⓘ before a public demo.
  Where: [`osmdroid.md`](osmdroid.md) §4.4 and §4.6

- [ ] 🗳️ **Register your debug fingerprints** · *each developer*
  SHA-1 in the Firebase Console (or Google sign-in fails on your build);
  SHA-256 in `hosting/public/.well-known/assetlinks.json`, then redeploy
  Hosting (or reset and verification links open the browser). **Four
  SHA-256s are live since 25 Oct** (James's and three teammates'); those three
  run `adb shell pm verify-app-links --re-verify com.example.guardband` on
  their phones. Anyone else still adds theirs.

- [x] 🗳️ **Publish the hosted pages** · *done 24 Oct*
  Published as final without the adviser review, at the team's decision:
  notice 1.2 (MapTiler, Google profile photo, alert notifications, Google
  place names), `CONSENT_VERSION` bumped to match, the Delete account path
  fixed, the DRAFT banners removed.
  Where: [`privacy-and-consent.md`](privacy-and-consent.md) §2

- [ ] 🗳️ **Design pass on the placeholders** · *Jul*
  - The dark palette (`values-night/colors.xml`) and whether to use
    MapTiler's white logo on the dark map.
  - The low-battery icon: the warning triangle, or the new nearly-empty battery.
  - Everything marked `DESIGN: Jul` (alert rows, About, Display).
  - Confirm two older theme changes: 28 dp button corners and 24 dp sign-up
    top padding (with Jehan).

---

## Later, only if needed

- [ ] 🗳️ **Rename the `com.example` app id**
  Only for a Play Store release: Play rejects the prefix, and the id cannot
  change after a first release. Means re-registering the Firebase app and
  every fingerprint.

- [ ] 🗳️ **A map plan for a real release**
  MapTiler's free plan is for non-commercial use and pauses at its monthly
  limit. Fine for the course; a public release would need a paid plan.

- [ ] 🟩 **Re-consent flow and a Terms page**
  Only if the notice's wording changes what people agreed to, or the course
  asks for Terms. Both mean a `CONSENT_VERSION` bump.
