# GuardBand team checklist

What is left to build, fix, check and decide on the Android app. Heavy
features come first; each item says who it is waiting on and where its
details live in the repo. Finished items are at the bottom. The full record
is [`handoff.md`](handoff.md) §4.

As of 27 Oct 2026 · `mvvm-main` and `main` · 741 unit tests passing · lint
passes with no errors.

**Types:** 🟥 heavy feature · 🟩 feature · 🔧 fix · 📱 phone check · 🗳️ decision / setup

**Pushing:** push to `mvvm-main` only. `main` is then fast-forwarded to the
same commit (`git push origin mvvm-main:main`), so the two never drift.

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
    manifest and needs the owner's (James's) approval first.
  - The Privacy Notice says the numbers are collected for this, and that the
    Emergency star only sorts and labels the list. If the star ever decides
    who gets texted, the notice changes (and its version) in the same commit.

  Where: CLAUDE.md "Contacts and profile data", [`privacy-and-consent.md`](privacy-and-consent.md)

---

## Features to build

- [ ] 🟩 **Band sends check-ins and alerts** · *firmware*
  The app already reads the payload in `SCHEMA.md`. The band has to write
  exactly that: a double-press within 2 seconds is a `CHECKIN` (FR-10), and
  every alert goes to `history/{sequenceId}` first, then `latest`.
  Where: `SCHEMA.md`, [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md)

---

## Fixes and cleanups

- [ ] 🔧 **Remove the stray `Guardband-MVVM` entry** · *Jehan, then app team*
  A nested copy of the repo in Jehan's folder got committed as a link to a
  commit (`9448b3c`) that only exists on his machine. Anything done inside
  that inner copy never reached GitHub, so check nothing is stranded there
  first, then remove the entry.

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

- [ ] 📱 **This week's changes (22–27 Oct)**
  - Contacts: search, the filter chips, the Emergency star (sorts to the top),
    long-press a row to delete.
  - The bell's inbox: real alerts by day, place names, Retry when offline.
  - Track: the target button re-centres on the band and zooms back in from
    far out; it is greyed out before the first fix. The layers button
    switches Auto, Street and Satellite, Auto follows dark mode, and the
    map reopens on the last one picked after the app is closed.
  - Account information: a password account sees Change password; a Google
    account sees "Managed by Google".
  - Sign out of a Google account, sign in with an email one: initials, never
    the old photo.

- [ ] 📱 **Dark mode**
  Settings → Display → Dark. Walk every screen, sign-up and login included,
  for white patches and unreadable text. The map turns dark; the credits stay
  readable and the MapTiler logo turns white (and is white on Satellite in
  light mode too). Close the app with Dark on a light phone: it
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

- [ ] 🗳️ **Design pass on the placeholders** · *Jul*
  - The dark palette (`values-night/colors.xml`). (The white MapTiler logo on
    dark maps is done, 27 Oct.)
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

---

## Done since 20 Oct

- [x] 🟩 **Notifications inbox with real data** · *21 Oct, jspdavis*
  The bell shows the band's alerts (tracking updates left out) by day, with
  place names. The sample "Welcome" card is gone.
- [x] 🟩 **Change password** · *22 Oct, Roxette; moved 27 Oct*
  `ChangePasswordActivity`, opened from **Account information** since 27 Oct:
  a password account sees the row, a Google one a "Managed by Google" note.
- [x] 🟩 **Contacts redesign** · *23 Oct, jspdavis; 27 Oct, Jehan*
  Search, relationship filter chips, the Emergency star, no upper limit,
  long-press to delete.
- [x] 🗳️ **Publish the hosted pages** · *24 Oct; notice 1.3 on 27 Oct*
  Published as final without the adviser review, at the team's decision, no
  DRAFT banners. 1.3 (27 Oct) removed the Google Maps row.
  `CONSENT_VERSION` moves with it.
  Where: [`privacy-and-consent.md`](privacy-and-consent.md) §2
- [x] 🟩 **Layers button on Track** · *26–27 Oct, jspdavis*
  Auto, Street, Satellite, Outdoor and Topo. Tiles checked on our key and plan.
- [x] 🟩 **Remember the map layer** · *27 Oct*
  `MapLayerPrefs` saves the picked layer, so the map reopens on it.
  (`MapTilerTileSource` provides Outdoor and Topo, offered in the picker.)
- [x] 🟩 **Re-centre button** · *27 Oct*
  The round target button re-centres on the band and zooms back in. The
  Google Maps hand-off is gone.
- [x] 🔧 **Mock sender lint errors** · *27 Oct*
  `./gradlew lint` passes with no errors.
- [x] 🔧 **Clear the Google photo cache on sign-out** · *27 Oct*
  `LoginNavigation` empties Coil's caches on every way out.
- [x] 🗳️ **Approve the release manifest** · *27 Oct, James (owner)*
  Seven permissions, including the three for alert notifications. Anything
  new needs James's approval again.
  Where: [`notifications.md`](notifications.md) §5
- [x] 🔧 **Remove `.vs/` from the repo** · *27 Oct*
  Visual Studio's workspace files are gone and `.vs/` is in `.gitignore`.
- [x] 🔧 **Changelog catch-up** · *27 Oct*
  Entries for 22, 23, 24, 25 and 27 Oct; 26 Oct gained the map-type layout.
