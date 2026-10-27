# Handoff

**As of 2026-10-27 · `mvvm-main` at `003bfdc` (alert pins) plus its
Changelog/handoff commit, pushed · `main` fast-forwarded to the same commit
(`git push origin mvvm-main:main`) · 777 JVM unit tests, 0 failures ·
`./gradlew lint` passes with no errors · nothing in flight.** Run `git status -sb` and `git log origin/mvvm-main -1` before
trusting any hash here.

This file is the entry point for anyone picking the project up cold,
including a chat that is deciding **what to build next**. §1 says what the
product is and what it can already do; §2 lists the constraints any new idea
has to live within; §3 sets out the candidate directions; §4 onwards is the
backlog, the reading list and the traps. What is already built is documented
in detail in the files under §6, not here.

---

## 1. The product in one page

**GuardBand** is a wrist-worn personal safety band plus an Android companion
app. It is an academic project (requirements FR-01..FR-11 in `README.md`).

- **The band** (ESP32 + Air780E 4G LTE modem + NEO-6M GPS) is meant to detect
  a panic press, take a GPS fix, text the wearer's emergency contacts by SMS
  itself, and write each alert straight to Firebase Realtime Database over
  REST. **The firmware does not exist yet.** A debug-only "Mock Sender"
  screen on a second phone plays the band: it writes the same payloads,
  using that phone's real GPS.
- **The app** is for the people watching: a guardian sees the band's
  position on a map, its alerts as they arrive, the wearer's check-ins, and
  manages the emergency contacts the band will text.
- **Backend:** Firebase only, project `guardband-aae65`, on the free **Spark
  plan**: Auth (email/password + Google), Realtime Database, Hosting (two
  static pages and the App Link file). **No Cloud Functions, no server, no
  FCM.**
- **Team:** James (owner; approves release permissions), Jul (Figma/design,
  MapTiler key, the database-rules decision), jspdavis, Jehan Ishi and
  Roxette (app features and UI), a firmware teammate. An AI coding agent
  does much of the app work under [`../CLAUDE.md`](../CLAUDE.md).

### What the app does today

| Area | What exists |
|---|---|
| **Accounts** | Email/password sign-up wizard (Account → Name → Contacts → Consent), Google sign-in, login, in-app password reset via the email link (App Link), email verification with a gate that locks unverified password accounts out, Change password, Delete account (re-auth, then a resumable two-step delete), Log Out. |
| **Consent and privacy** | A recorded consent (`accepted`, timestamp, version `1.3`) for every account; a gate that sends any account without one to the consent screen. Hosted Privacy Notice (1.3) and delete-account page, published as final. No analytics. |
| **Track tab** (default) | A MapTiler map (five layers: Auto/Street/Satellite/Outdoor/Topo, dark map in dark mode, choice remembered) with the band's last known position as a face marker (Google photo or initials); small pins under it for the newest ten panics, check-ins and low-battery alerts (one per spot, colour-coded; a tap shows "Panic, 5 min ago"); a re-centre button; a "Last check-in: 5 min ago" status card; a draggable bottom sheet with the newest three alerts and "See all". |
| **Alerts** | Alert history (pushed screen): latest alert card plus the list by day, colour-coded by type (panic, check-in, low battery, unknown), with place names instead of coordinates (Android Geocoder), battery level and "5 min ago" times. Tracking updates are hidden from lists. |
| **Notifications** | The bell opens an inbox of the band's alerts by day. Opt-in **alert notifications** (Settings): a foreground service keeps the listener open and posts each new alert as a system notification, panic silent for the wearer's discretion, nothing identifying on the lock screen. |
| **Contacts tab** | Emergency contacts: add/edit (tap a card)/delete (trash or hold), search, relationship filter chips, an Emergency star that sorts and badges. At least one contact always; phone numbers stored as E.164. |
| **Profile tab** | Avatar, welcome line and email. Thin. |
| **Settings** | Account information (details, verification status, Change password, Delete account), Alert notifications, Display (Light/Dark/System), Device information (**"Coming soon"**), About (version, Privacy Notice), Changelog, Log Out. |
| **Mock sender** (debug builds only) | Sends panic, check-in (double-press), low battery and tracking updates; a walk mode moves the marker around a loop. |

### Requirements coverage

| FR | Requirement | State |
|---|---|---|
| FR-01 | Auth (email/password, Google) | **Done** in the app. |
| FR-02 | Real-time location and incident status | Location **done** (live marker). "Incident status" is only the latest alert's type and age: there is no notion of an incident being active, acknowledged or resolved. |
| FR-03 | Panic detected within 1 s | **Firmware.** Not started. |
| FR-04 | GPS fix within 5–10 s | **Firmware.** Not started. |
| FR-05 | Automated SMS with a Google Maps link | **Firmware** sends it. The app stores the contacts, but nothing gets them onto the band yet (needs pairing). |
| FR-06 | Contact management | **Done.** |
| FR-07 | Real-time sync to Firebase | Payload contract (`SCHEMA.md` v1.0) and rules are done; the mock sender writes it; the firmware has to. |
| FR-08 | Incident history log | **Done** (Alert history, inbox). |
| FR-09 | Tracking every 60 s for 30 min after an alert | The app moves the marker on each `TRACKING_UPDATE`; **no trail or path** is drawn, and tracking updates are hidden from every list. The firmware has to send them. |
| FR-10 | Discreet double-press check-in, no SMS | **Done** in the app (status card, history). Firmware has to send it. |
| FR-11 | Low-battery alert at 20% | **Done** in the app (alert type, orange battery icon, notification). Firmware has to send it. |

**In short:** the app side of nearly every requirement is built. What is
missing is the band, and the link between a band and an account (pairing).

---

## 2. Constraints any new idea has to live within

These are hard rules, from CLAUDE.md and from decisions already taken.

- **No server code.** Spark plan: no Cloud Functions, no Admin SDK, no FCM
  sending. Anything that needs a trusted server (typed reset codes, push to
  phones, custom tokens) is out unless the plan changes, which costs money.
- **One hard-coded band.** Every install watches `devices/guardband-001`
  (`DeviceConstants.DEFAULT_DEVICE_ID`), and `devices/` is open to anyone,
  read and write, until pairing exists. The Privacy Notice says so.
- **The app never writes to `devices/`.** Only the band (or the mock sender)
  does. No check-in, acknowledgement or command goes from the main app to the
  band's tree today; adding one is a design decision with rules work.
- **No phone-location permission in the release app, ever.** The band's GPS
  is the location. (Only the debug mock sender asks.)
- **Release permissions are fixed at seven**, approved by James on
  2026-10-27. Any new one (`SEND_SMS`, boot receiver, wake lock, full-screen
  intents, battery-optimisation exemption) needs his approval first.
- **The Privacy Notice states only what the code does.** Collecting a new kind
  of data, sending data to a new third party, or adding analytics means
  changing the hosted notice and bumping `CONSENT_VERSION` in the same commit.
- **Contacts are only for the band to text.** Storing non-emergency contacts
  needs a stated purpose and a notice change first.
- **Nothing logs PII**: no uid, email, token, phone number, contact name,
  coordinate or place name.
- **Architecture is fixed:** MVVM, one host Activity with Fragment tabs, no
  Navigation Component, `findViewById`, Firebase only inside repositories.
  compileSdk 34; some libraries are pinned because newer ones need 35.
- **Design belongs to Jul** (Figma). The agent builds layout skeletons and
  plumbing and marks visual choices `DESIGN: Jul`.
- **Maps:** MapTiler's Free plan, non-commercial, pauses at its monthly cap.
  No bulk downloading or offline regions.

---

## 3. What could come next

Ordered roughly by how much they unlock. None of this is decided; each item
says what it depends on and what it would touch.

### Unlocks the most

1. **Device pairing.** Link a band to an account instead of the hard-coded
   id. Unlocks closing the open `devices/` rules, multiple bands, the Device
   information screen, and getting contacts onto the band for SMS (FR-05).
   - Needs: a device id per account (`users/{uid}/devices` or similar), a way
     to add a band (QR code or typed id), and **a way for the band to
     authenticate** without a server. One Spark-compatible option is a
     Firebase Auth account per band, signed in through the Auth REST API, so
     rules can say `auth.uid == band's uid`. Custom tokens would need a server.
   - Touches: `RTDBS-RULES.md` (Jul's pending `devices/` decision,
     [`database-rules.md`](database-rules.md) §4), the mock sender (send
     `?auth=`), `RepositoryProvider`, the notice.
2. **Roles: wearer and guardian.** Today the signed-in user is implicitly the
   wearer (their face is on the marker) and also the watcher. A guardian
   watching someone else's band needs sharing or invites on top of pairing.
   `TrackViewModel.markerFace()` is already the one place that decides whose
   face is shown.
3. **Getting contacts to the band (FR-05).** After pairing: a band-readable
   copy of the emergency numbers, plus an agreed SMS wording. A location link
   in a text is sensitive, so the wording is a privacy decision. SMS stays on
   the band; the phone sending SMS would need `SEND_SMS` and James's approval.

### App-only, buildable now

4. **An incident view.** Alert rows are not tappable today. Tapping a panic
   could open the map at that point, with the FR-09 tracking updates that
   followed it drawn as a path (the data is already in `history`, just
   hidden). That would give FR-02's "incident status" and FR-09 something to
   show. **The map half has a start (2026-10-27):** recent alerts are pins on
   Track, and `TrackViewModel.onAlertPinTapped(alert)` is the empty hook a pin
   tap would open it from ([`osmdroid.md`](osmdroid.md) §5.5).
5. **Incident status: acknowledge or resolve.** A guardian marks a panic "I'm
   on it" or "resolved", so other guardians (and the history) know. Needs a
   place to write that is **not** `devices/` (e.g. under the user, or a new
   node with its own rules), and a decision on who can see it.
6. **Device information screen.** The Settings row says "Coming soon".
   `TrackViewModel` already computes "reporting / last seen" (180 s
   threshold) and battery; the panel showing them was removed from Track on
   2026-10-14. They could live here, with the device id, before pairing.
7. **A fuller Profile tab.** It shows only an avatar, a greeting and an email.
   Candidates: edit display name; wearer details useful to a responder.
   Anything medical or identifying is new personal data, so the notice
   changes with it.
8. **A demo mode / demo script.** The project is presented without hardware.
   A scripted mock-sender run (panic → tracking walk → check-in → low
   battery) would make demos reliable.

### Quality and hardening

9. **The phone-check backlog** (§4): most screens since 2026-10-13 have unit
   tests but no recorded device pass, including alert notifications (47
   cases), dark mode and the consent gate.
10. **UI and instrumented tests.** `ExampleInstrumentedTest` is still the
    template; nothing tests a screen, the `MapView` or the Firebase calls.
11. **Offline behaviour.** RTDB disk persistence is off, so a cold start
    offline shows nothing until the network returns.
12. **Localisation and accessibility.** All copy is English; ViewModel
    messages are Kotlin `MSG_*` constants, not resources, which makes
    translation harder. No accessibility pass has been done; the new check-in
    card may be read twice by TalkBack.

### Bigger ideas that need a decision first

13. **Safe zones / geofences**: alert when the band leaves an area. Needs the
    band's positions processed somewhere: on the phone through the
    foreground service (no server), and a notice change.
14. **Push notifications without the foreground service**: needs FCM
    sending, so Blaze or a server.
15. **A Play Store release**: rename `com.example.guardband` (cannot change
    after a first release; means re-registering the Firebase app and every
    fingerprint), a release keystore, and a paid MapTiler plan.

---

## 4. Backlog

### Waiting on a person

| | Who | Notes |
|---|---|---|
| **Decide `devices/` and publish the rules** | Jul | Pick A, A+ (recommended), B or C ([`database-rules.md`](database-rules.md) §4). B breaks the mock sender; C breaks every receiver until pairing. `RTDBS-RULES.md` is ahead of the Console until then: do not describe it as the live ruleset. Also ask the firmware teammate whether anything uses `devices/{id}/command` or `status`; if not, delete both blocks. |
| **MapTiler key** | Jul | A dedicated key restricted by **Allowed user-agent header** to `GuardBand/1.0 (+https://github.com/jspdavis/GuardBand)` (`OsmdroidConfig.USER_AGENT`), shared privately, in each `local.properties` as `MAPTILER_API_KEY`. Without it Track says "Map unavailable". [`osmdroid.md`](osmdroid.md) §4.4. |
| **Ask MapTiler about the collapsing credit** | Jul | Before any public demo. The ⓘ collapse follows OSMF's guidelines, not a MapTiler statement. [`osmdroid.md`](osmdroid.md) §4.6. |
| **Design pass on the placeholders** | Jul | The dark palette (`values-night/colors.xml`), the low-battery icon (triangle or battery), everything marked `DESIGN: Jul` (88 places, 4 of them the alert pins and their label), and two older theme changes to confirm with Jehan: 28 dp outlined-button corners, 24 dp sign-up top padding. |
| **Remove the stray `Guardband-MVVM` gitlink** | Jehan, then anyone | A nested clone in Jehan's tree was committed as a gitlink to `9448b3c`, which exists only on her machine. Check nothing is stranded in that inner copy ([`guardband-mvvm-folder.md`](guardband-mvvm-folder.md)), then remove the entry. |
| **Each teammate's fingerprints** | each developer | Debug **SHA-1** in the Firebase Console (else Google sign-in fails on their build); debug **SHA-256** in `hosting/public/.well-known/assetlinks.json` and Hosting redeployed (else reset and verification links open the browser). Four SHA-256s are live (James's and three teammates'). Release keystore's before any signed build. |
| **Band firmware** | firmware teammate | Write exactly `SCHEMA.md`'s payload: `history/{sequenceId}` first, then `latest`; a double-press within 2 s is a `CHECKIN` (FR-10); tracking every 60 s for 30 min after an alert (FR-09); low battery at 20% (FR-11). |

### Phone checks

Built and unit-tested, never recorded on a phone. The developer tests on
their own phone each session, so this is a reference list, not a queue.
[`team-checklist.md`](team-checklist.md) "Phone checks" has the current
list; the step-by-step checklists live in each doc:

- 22–27 Oct changes: Contacts (search, chips, star, tap to edit, hold to
  delete, the new dialog), the inbox, the re-centre button, the five map
  layers and their persistence, the check-in card, Change password in Account
  information, no stale Google photo after switching accounts.
- Dark mode, every screen.
- Alert notifications: [`notifications-test-cases.md`](notifications-test-cases.md) (47 cases).
- Alert rows and place names: [`alert-data.md`](alert-data.md) §12.
- Map, face marker, credits and logo links: [`osmdroid.md`](osmdroid.md) §5.4 and §8.
- Alert pins on Track (27 Oct): [`osmdroid.md`](osmdroid.md) §8, "Alert pins".
- Settings, About, Display, Alert notifications screens.
- The older pass since 13 Oct: [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md)
  section A, the consent gate ([`privacy-and-consent.md`](privacy-and-consent.md) §6
  steps 9–16), a pending deletion resuming (set
  `users/{uid}/deletion_pending: true` on a throwaway account in the Console),
  the mock sender's GPS on a second phone (allow and refuse).
- Never done: confirm RTDB traffic stops after Log Out. Backgrounding alone
  does not stop the alerts listener, by design ([`alert-data.md`](alert-data.md) §9).

### Engineering, roughly in order

| | Notes |
|---|---|
| **Close `devices/`** | Needs pairing (§3, item 1). Close it before any public demo. |
| **Rename `applicationId` off `com.example.*`** | Only if publishing (§3, item 15). |
| **No re-consent flow** | Accounts keep the version they agreed to. Building one means deciding what happens to an account that declines. `ConsentGate.needsConsent` is where a version rule would go. |
| **Server-side enforcement of verification** | Only if ever wanted. A rule requiring `auth.token.email_verified` on `users/$uid` would fail every sign-up, because the profile is written before the email can be opened. |
| **Sweep finished deletions' markers** | Optional. Each completed Delete account leaves `users/{uid}: { deletion_pending: true }` under a uid no account owns. A human can delete them from the Console. |
| **A Terms page** | Only if the course needs one: a hosted `/terms`, `HostedPages.terms`, and a `CONSENT_VERSION` bump. |

### Small cleanups

- `ic_edit` (the contact pencil) is now hidden in `item_contact.xml` since tap
  to edit; delete the button and its binding if it stays unused.
- The mock sender has no launcher icon of its own (it differs by label only).
- The face marker's "Your band" bubble is still osmdroid's stock white bubble,
  which ignores dark mode. The alert pins' label beside it uses theme colours
  (`view_alert_pin_label.xml`). Give the face the same label window, or leave
  it for Jul's design pass.
- Refresh this file after each merge: the header's hashes and test count,
  §1's tables and §4.

---

## 5. Done, by date

The full, user-facing record is `Changelog.entries`
(`app/src/main/java/com/example/guardband/ui/changelog/Changelog.kt`), shown
in the app under Settings → Changelog. Headlines:

- **2026-10-27:** alert pins on the Track map, map layers (five, remembered), re-centre button replaces the
  Google Maps hand-off (notice 1.3), check-in card, alert cards restyled,
  contacts tap to edit / hold to delete and one contact dialog, Change
  password moved into Account information, Coil cache cleared on sign-out,
  MapTiler logo without a chip, release manifest approved, lint clean.
- **2026-10-26:** Privacy Notice 1.2 and the delete-account page published
  as final; a hanging sign-up test fixed; the map-type picker layout.
- **2026-10-21..25:** real Notifications inbox, double-press check-in on the
  mock sender, Change password, Contacts redesign (search, chips, Emergency
  star, no upper limit), dark-mode button fixes.
- **2026-10-20:** place names on alerts, alert rows redesigned, Settings
  reorganised (About, Display, Alert notifications screens), dark mode.
- **2026-10-19:** Alert tab folded into Track (bottom sheet + Alert history),
  MapTiler tiles and logo, face marker, alert symbols, alert notifications.
- **2026-10-14:** account safety (Delete account in Account information, the
  deletion marker, email verification), verification gate, consent gate,
  check-in status.
- **2026-10-11..13:** Hosting, Security Rules and the reset App Link
  deployed; the teammate UI redesign; real GPS in the mock sender.

---

## 6. Read these first

| For | Read |
|---|---|
| The agent's working rules, conventions, gotchas | [`../CLAUDE.md`](../CLAUDE.md) |
| What is left, by person, in short | [`team-checklist.md`](team-checklist.md) |
| **Running a demo with no hardware** | [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md) |
| The map library and its configuration | [`osmdroid.md`](osmdroid.md): **read before touching the map** |
| How alerts are read from RTDB | [`alert-data.md`](alert-data.md) |
| Alert notifications | [`notifications.md`](notifications.md), [`notifications-test-cases.md`](notifications-test-cases.md) |
| The sign-up wizard | [`signup-flow.md`](signup-flow.md) |
| The post-login host and its tabs | [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md) |
| The in-app password reset | [`forgot-password.md`](forgot-password.md) |
| Email verification | [`email-verification.md`](email-verification.md) |
| Account information and Delete account | [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md) and CLAUDE.md "Account deletion" |
| The 2026-10-13 teammate UI pass | [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md) |
| Privacy Notice, consent versions, hosted pages | [`privacy-and-consent.md`](privacy-and-consent.md) |
| The alert payload contract | [`../SCHEMA.md`](../SCHEMA.md): **authoritative** |
| Security Rules | [`../RTDBS-RULES.md`](../RTDBS-RULES.md) (source of truth) and [`database-rules.md`](database-rules.md) (paths, `devices/` options, Playground tests, rollout) |

**Branches.** Push to `mvvm-main` only; `main` is then fast-forwarded to the
same commit (`git push origin mvvm-main:main`), never merged with `--no-ff`.
`mvp-checkpoint*` stay strict MVP: never port code between them and the MVVM
branches. `git branch --no-merged mvvm-main` should list only `main` and the
MVP checkpoint branches.

---

## 7. Needs a human, not an agent

- **Changing the Security Rules.** Edit `RTDBS-RULES.md`, strip every `"//"`
  key, paste the whole file over the Console's, run the Playground tests in
  [`database-rules.md`](database-rules.md) §5 (or the emulator recipe in §6),
  publish. Copy the live rules out first. If anyone adds
  `"$other": { ".validate": false }` at `users/$uid`, give `deletion_pending`
  a rule of its own in the same change, or Delete account fails at step 2.
- **Registering fingerprints** (§4).
- **Firebase Console settings.** Email templates' sender name and subject can
  be edited; the action URL cannot on this project, so leave it at the default.
- **The Privacy Notice.** Published as final without an adviser review, at
  the team's decision. It describes how real people's data is handled,
  including emergency contacts who never signed up, and an agent drafted it
  from the code: a reason for a human to re-read it whenever it changes.
- **Figma** is the visual source of truth and is Jul's.
- **The payload** is a contract with the band firmware: the agent can propose
  changes, a human agrees them.
- **New release permissions**: James approves first.

---

## 8. Building, deploying and verifying

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
`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/`.
Expect exactly the seven approved permissions (`INTERNET`,
`ACCESS_NETWORK_STATE`, `READ_GSERVICES`, the app's own
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, `POST_NOTIFICATIONS`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) and no location
permission. [`notifications.md`](notifications.md) §5 says why each is there.

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
`VerifyEmailActivity` and say the link "has already been used";
`mode=recoverEmail` should say the link "isn't supported".

**Testing email flows with one Gmail inbox.** `yourname+test1@gmail.com`,
`yourname+test2@gmail.com` and so on all arrive in the same inbox. Firebase's
default sender sometimes lands in spam.

**Test rules without touching production.** The project has no emulator suite,
but the CLI's Database Emulator works ad hoc from a scratch directory: a
`firebase.json` pointing `database.rules` at a stripped copy of
`RTDBS-RULES.md`, then `firebase emulators:exec --only database --project
demo-guardband "node test.mjs"`. Use a `demo-` project id so nothing can
reach the real project. Fake users are unsigned JWTs passed as `?auth=`;
`Authorization: Bearer owner` bypasses rules for seeding. See §9 for its traps.

**Not covered by tests:** `FirebaseAlertRepository`'s listener and whether
`awaitClose` removes it, the Firebase calls in auth, reset, verification and
the deletion marker's write, the `MapView`, and anything in the UI layer.

---

## 9. Traps that have already cost time

**Firebase**

- **The Console's password-reset action URL cannot be changed** on this
  project: `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED`, from the Console and the API
  alike. The app claims the default handler instead, with an exact
  `android:path`, because `/__/auth/handler` shares the host.
- **A child's `.read` never grants the parent.** `history/$sequenceId` alone
  let each entry be read but denied the list, which 401'd the alert screens
  and the mock sender until 2026-10-11. `history` now has its own `.read`.
- **The Console rejects `RTDBS-RULES.md`'s `"//"` keys.** Strip them before
  pasting.
- **Firebase Hosting's default `ignore` drops `.well-known/`.** The committed
  `firebase.json` omits that glob on purpose; don't tidy it back.
- **Hosting caches a 404 for an hour** (`max-age=3600`). Hard-refresh or use a
  private tab after a deploy.
- **The emulator needs Java 21+**, the opposite of Gradle: unset `JAVA_HOME`
  so the PATH's Java 25 is used. On Windows its `java` process outlives
  `emulators:exec` and holds port 9000. Rules from `firebase.json` apply to
  the `<project>-default-rtdb` namespace, so querying `?ns=<project>` silently
  runs with open rules; load them with a `PUT /.settings/rules.json` instead.
- **With no disk persistence, an RTDB read made offline never fails; it
  waits.** That is why the Home gate has a 10 s timeout.
- **A new library can add permissions silently.** Re-run
  `:app:processReleaseManifest` after any dependency bump (§8).

**Maps**

- **OSM and MapTiler block by user agent, and the block is HTTP 200 with a
  PNG** reading "access blocked", which osmdroid caches like a good tile.
  Keep `OsmdroidConfig.USER_AGENT` fixed, never call
  `Configuration.getInstance().load(...)`, and never put
  `FLAG_USER_AGENT_NORMALIZED` back into the tile policy. [`osmdroid.md`](osmdroid.md).

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
- **`README.md` is UTF-16 LE with CRLF.** Read it with
  `iconv -f UTF-16 -t UTF-8`; a naive script rewrite turns into a whole-file
  change git shows only as `Bin … differ`.
- **A `StateFlow` drops an emission equal to the previous one.**
  `FakeAlertRepository` uses a replaying `MutableSharedFlow` for that reason.
- **Don't "simplify" `AlertParser` into `getValue(Alert::class.java)`.** It
  reintroduces the `isCharging` → `charging` mapper bug, silently.
- **Don't turn an injected ticker into an internal `while (true) { delay(…) }`.**
  Tests calling `advanceUntilIdle()` then hang instead of failing. A
  `repeat(Int.MAX_VALUE)` in a test did the same on 2026-10-26 and locked
  `app/build`.
- **A ViewModel that launches in `init` breaks a test that builds it in a
  field initializer.** The field runs before `MainDispatcherRule` swaps in
  Main. Build it `by lazy` or inside the test.

**Git**

- **`git merge -F -` does not read stdin** ("could not read file '-'"). Write
  the message to a file. `git commit -F -` does.
- **Teammates commit IDE files** (`.idea/`, once `.vs/`) and once a nested
  clone as a gitlink. Stage explicit paths, never `git add -A`.
