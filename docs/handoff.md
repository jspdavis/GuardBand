# Handoff

**As of 2026-11-11 · `mvvm-main` at `5f0b254` (jspdavis: connected accounts
and incident status, 10 Nov), after `653f29f` (Jehan: alert history by date
and Clear history, 5 Nov), plus this docs pass, all pushed · `main`
fast-forwarded to the same commit on 2026-11-11 · 990 JVM unit tests, 0 failures · `./gradlew lint` last clean
on 27 Oct, not re-run since · plus the Wi-Fi band prototype in `firmware/`.**
**The 10 Nov merge left the app showing an empty band by default; read §3,
"Next steps", before demoing anything.** Run `git status -sb` and
`git log origin/mvvm-main -1` before trusting any hash here.

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
  REST. **The real firmware does not exist yet.** A **Wi-Fi prototype**
  does (2026-10-28, [`../README.md`](../README.md#band-firmware-prototype)): an
  ESP32 with three LEDs and one button that writes the same payloads over
  Wi-Fi, with the Mock Sender's gestures and timings, its position fed from
  the laptop's Windows location over Serial (no GPS module, modem or SMS
  yet). A debug-only "Mock Sender" screen on a second phone also plays the
  band, using that phone's real GPS.
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
| **Track tab** (default) | A MapTiler map (five layers: Auto/Street/Satellite/Outdoor/Topo, dark map in dark mode, choice remembered) with the band's last known position as a face marker (Google photo or initials); small pins under it for the newest ten panics, check-ins and low-battery alerts (one per spot, a panic always keeping its spot; colour-coded; a panic pin opens its Incident view, the others show "Check-in, 5 min ago"); the face's bubble offers "View incident" after a panic; a re-centre button; a "Last check-in: 5 min ago" status card; a draggable bottom sheet with the newest three alerts and "See all". **Since 10 Nov** a **wearer switcher** at the top: "My Band" or a connected wearer, with Safe / Panic Alert / Offline ([`device-pairing.md`](device-pairing.md) §9). |
| **Alerts** | Alert history (pushed screen): latest alert card plus the list by day (Today, Yesterday, then each date), colour-coded by type (panic, check-in, low battery, unknown), with place names instead of coordinates (Android Geocoder), battery level and "5 min ago" times. Tracking updates are hidden from lists. **Incident view** (pushed screen): a panic row, a panic pin or the face's "View incident" opens the panic on a map with the tracking updates after it drawn as a path, plus its time, place, latest battery and update count. **Since 10 Nov** every panic has a **status chip** (Active / Acknowledged / Resolved), changeable from Alert history and the Incident view. **Since 5 Nov** Alert history has a **Clear history** button, which the rules refuse ([`alert-data.md`](alert-data.md) §13). |
| **Notifications** | The bell opens an inbox of the band's alerts by day. Opt-in **alert notifications** (Settings): a foreground service keeps the listener open and posts each new alert as a system notification, panic silent for the wearer's discretion, nothing identifying on the lock screen. |
| **Contacts tab** | Emergency contacts: add/edit (tap a card)/delete (trash or hold), search, relationship filter chips, an Emergency star that sorts and badges. At least one contact always; phone numbers stored as E.164. |
| **Device tab** (was Profile) | A compact header (photo or initials, name, email); the band: "Reporting" or "Last seen 5 min ago", battery (orange at 20%), last alert with time and place name, last known position as a place name ("On the move" while tracking); the connected account (sign-in method, email verification, "Emergency contacts: N, M starred" opening Contacts); "Pair a band", which since 10 Nov opens **Connect** (share an invite code, or redeem one to watch someone); how the band works. Since 10 Nov it shows the account's uid as its band, with no data ([`device-tab.md`](device-tab.md) §5). At the top, a **band viewer**: the band on a turntable to drag round, built to play rendered frames, **showing a still placeholder** until the frames exist (§3, item 14). [`band-viewer.md`](band-viewer.md). |
| **Settings** | Account information (details, verification status, Change password, Delete account), Alert notifications, Display (Light/Dark/System), About (version, Privacy Notice), Changelog, Log Out. |
| **Mock sender** (debug builds only) | Plays the band with its one button: hold 1.5 s = panic (then tracking every 5 s for 30 min), double-press = check-in, hold 5 s during a panic = cancel (sends a check-in). A battery slider sets the level reported and sends a low-battery alert at 20%. |

### Requirements coverage

| FR | Requirement | State |
|---|---|---|
| FR-01 | Auth (email/password, Google) | **Done** in the app. |
| FR-02 | Real-time location and incident status | Location **done** (live marker). Incident status: the **Incident view** shows a panic, its path since, how many tracking updates and how recent the last one is, and since 10 Nov a status chip (Active / Acknowledged / Resolved) that anyone can change, stored on the world-writable `devices/{id}/status` (§4). |
| FR-03 | Panic detected within 1 s | **Firmware.** Not started. |
| FR-04 | GPS fix within 5–10 s | **Firmware.** Not started. |
| FR-05 | Automated SMS with a Google Maps link | **Not built; waits for pairing.** The wearer's phone is to send it (§3, item 3). |
| FR-06 | Contact management | **Done.** |
| FR-07 | Real-time sync to Firebase | Payload contract (`SCHEMA.md` v1.0) and rules are done; the mock sender writes it; the firmware has to. |
| FR-08 | Incident history log | **Done** (Alert history, inbox). |
| FR-09 | Tracking every 60 s for 30 min after an alert | The app moves the marker on each `TRACKING_UPDATE`, and the **Incident view** draws the updates after a panic (30 min, by `sequenceId`) as a path. They stay hidden from every list. The firmware has to send them. |
| FR-10 | Discreet double-press check-in, no SMS | **Done** in the app (status card, history). Firmware has to send it. |
| FR-11 | Low-battery alert at 20% | **Done** in the app (alert type, orange battery icon, notification). Firmware has to send it. |

**In short:** the app side of nearly every requirement is built. What is
missing is the band, and the link between a band and an account (pairing).
Pairing was half-built on 10 Nov in a different shape from the plan, and in
doing so moved every account off the shared demo band (§3).

---

## 2. Constraints any new idea has to live within

These are hard rules, from CLAUDE.md and from decisions already taken.

- **No server code.** Spark plan: no Cloud Functions, no Admin SDK, no FCM
  sending. Anything that needs a trusted server (typed reset codes, push to
  phones, custom tokens) is out unless the plan changes, which costs money.
- **One shared demo band.** Every install was meant to watch
  `devices/guardband-001` (`DeviceConstants.DEFAULT_DEVICE_ID`) until a band
  can be claimed, and `devices/` reads are open to anyone until pairing
  exists. The Privacy Notice says so. **Since 10 Nov the code no longer does
  this by default** (§3, step A).
- **The app never writes to `devices/`.** Only the band (or the mock sender)
  does. **Broken twice in November**, both open: Clear history (refused by
  the rules) and the status chip (`devices/{id}/status`, world-writable).
  Adding a write is a design decision with rules work, not a precedent.
- **One rules file.** `RTDBS-RULES.md`, stripped, is the ruleset. A second
  copy (`RTDBS-RULES-CONSOLE.json`, 10 Nov) has already drifted from it.
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

### Next steps (2026-11-11)

The two November merges added real features but left the shared demo
broken and opened a privacy gap. The next demo is in December, so there is
time to do this properly, in this order. Each step names who decides.

**A. Put the demo band back (app only, small; decide: James).**
`FirebaseBandRepository` gives an account with no claimed band its own uid
as its first band, then `guardband-001`, and `BandSelection.watchedBandOf`
takes the first. Nothing writes to `devices/<account uid>`, so Track, Alert
history, the inbox, the Device tab and notifications all show an empty band,
and `guardband-001` cannot be picked. *Recommended:* the fallback becomes
`guardband-001` alone, and a connected wearer with no claimed band adds
nothing (or the same `guardband-001`, de-duplicated). Extract the list-building
into a pure function so it gets a test; the current bug is invisible to the
fakes. Also make `readOnce` resume on a read error, so the list cannot
stall. [`device-pairing.md`](device-pairing.md) §9.

**B. One rules file, and record what is live (Jul).** Copy the live rules
out of the Console. Fix `watcher_wearers`'s `.read` in `RTDBS-RULES.md` (it
must sit on `$watcher_uid`, as in the JSON copy), delete
`RTDBS-RULES-CONSOLE.json`, and note the publish date in
[`database-rules.md`](database-rules.md) §9. Until then nobody knows which
watcher rules the switcher is running against.

**C. Undo the two writes to `devices/` that should not be there (app; Jehan
and jspdavis).**
- *Clear history:* the rules refuse it, correctly, on a shared band.
  *Recommended:* remove the button now; if a per-account "hide alerts before
  this one" is wanted, design it under `users/{uid}` with a notice line.
- *Incident status:* keep the chips for the demo, but treat
  `devices/{id}/status` as temporary. Its proper home (a node only the band's
  wearer and watchers can write) comes with pairing (step E). Make the chip
  inert-looking where tapping does nothing (Track's sheet, the inbox).
- *The inbox* goes back to `DEFAULT_HISTORY_WINDOW`; reading the whole
  history live is a cost and battery problem that grows with every demo.
[`alert-data.md`](alert-data.md) §13.

**D. Close the watcher privacy gap before anyone real uses Connect (rules:
Jul; notice: James).**
- The rules let any signed-in account add itself as a watcher of anyone
  without a code, and a watcher can read the wearer's whole `users/{uid}`,
  emergency contacts included. Tie the watcher row to a live invite and
  narrow what a watcher reads to the name and the bands index. That needs a
  small app change (the redeem write carries the code) and emulator tests.
  [`database-rules.md`](database-rules.md) §10.
- Invite codes: create-only (no overwriting someone else's), single use or
  deleted once redeemed.
- The **Privacy Notice and `CONSENT_VERSION`** change in the same commit:
  watchers now see a wearer's name and alerts.
- Screens to **remove a watcher** (wearer) and **stop watching** (watcher);
  the repository methods exist.

**E. Then pairing proper: claim a band (step 3).** Decide first whether
watching stays **per account** (what was built) or moves **per band**
(`device_members`, the plan). *Recommended:* keep per account, since an
account wears one band; the claim writes `users/{uid}/bands/{deviceId}`
plus a band-to-wearer row the `devices/` read rule can check later. Then the
switcher shows real friends' bands, the alert watch follows the switcher,
"All" and the face marker per wearer follow (steps 5 and 6).
[`device-pairing.md`](device-pairing.md) §8 and §9.

**F. Team process (everyone).** Both merges skipped the Changelog entry, the
docs and the rules record, and committed IDE files (`.idea/.name`,
`.idea/deploymentTargetDropDown.xml`). Before pushing to `mvvm-main`: tests
pass, a Changelog entry for the day, the affected doc updated, any rules
change in `RTDBS-RULES.md` only (with "published on <date>" or "not
published" in [`database-rules.md`](database-rules.md)), no `.idea/` files,
and `main` fast-forwarded after (it had fallen 9 commits behind by
11 Nov).

### Candidate directions

Ordered roughly by how much they unlock. None of this is decided; each item
says what it depends on and what it would touch.

### Unlocks the most

1. **Device pairing. Started 2026-11-02; the plan is
   [`device-pairing.md`](device-pairing.md).** Link bands to accounts instead
   of the hard-coded id, many-to-many: anyone can wear a band and watch
   friends' at once, and Track gets a My band / friend / All selector. The
   goal is closing the world writes on `devices/`, then reads; it is also
   what SMS (FR-05) waits on.
   - Decided: each band is a Firebase **anonymous** user whose uid is its
     device id; the wearer claims it with a one-time code printed on the
     **Serial Monitor**; the wearer invites watchers.
   - **Step 1 is built:** `BandRepository` (the account's bands; every
     account still wears `guardband-001`), `BandSelection`, and a
     `WatchedBandAlertRepository` that follows them, with `AlertError.NoBand`.
     No visible change.
   - **Step 2 is built:** the prototype firmware and the mock sender sign in
     as anonymous users and write with `?auth=` (still to `guardband-001` by
     default); the firmware checks certificates (`gts_roots.h`);
     `RTDBS-RULES.md` closes writes on every band id but `guardband-001`
     (emulator-tested, published 2026-11-04). Waits on Anonymous sign-in in
     the Console. Next: claiming a band over Serial (step 3).
   - **Watchers and a switcher were built on 10 Nov (jspdavis), ahead of the
     claim and per account rather than per band:** invite codes, a Track
     switcher, `FirebaseBandRepository`. Not demo-ready: see "Next steps" A,
     B and D above, and [`device-pairing.md`](device-pairing.md) §9.
2. **Roles: wearer and guardian.** Today the signed-in user is implicitly the
   wearer (their face is on the marker) and also the watcher. A guardian
   watching someone else's band needs sharing or invites on top of pairing.
   `TrackViewModel.markerFace()` is already the one place that decides whose
   face is shown. **Roles stay fluid (decided 2026-11-02):** one person can
   watch someone and wear a band at the same time, so there is no stored
   GUARDIAN/DEPENDENT and no per-role dashboard. A roles build (`e97e15b`)
   was rolled back: everyone has the three tabs, sign-up has no role picker,
   and its Assigned Guardian card is now the top of the Contacts tab (the
   first starred contact, with Call). Who watches whom should come from
   pairing and sharing, not from a label on the account. Since 10 Nov it
   comes from invite codes between accounts (item 1).
3. **Panic SMS (FR-05).** Direction since 2026-11-02: the band writes the panic
   to Firebase and the wearer's phone sends the text, so a dead modem or a
   missing phone means no text. **No SMS in any build until pairing ships.**
   `SmsPanicSender` is parked with no caller and `SEND_SMS` is out of the
   manifest (§4, "Parked code"). What pairing must then deliver is in
   [`team-checklist.md`](team-checklist.md). The Privacy Notice, the README and
   §1 above still describe the band sending; they change in the commit that
   turns sending on.

### App-only, buildable now

4. **Incident status: acknowledge or resolve. Built 2026-11-10
   (jspdavis), on temporary storage.** A guardian marks a panic Acknowledged
   or Resolved, so other guardians (and the history) know. Chips on `PANIC`
   alerts only; stored at `devices/{id}/status/{sequenceId}` with
   `updateStatus` (10 s timeout; failures toast). No uid is stored
   (`updatedBy` removed), so no `CONSENT_VERSION` bump was needed. **Still
   open:** the node is world-writable and in the band's tree; Resolved does
   not end an incident; the payload's own `status` field is a second source
   nobody writes. §4 "Parked code"; [`alert-data.md`](alert-data.md) §13.

5. **A demo mode / demo script.** The project is presented without hardware.
   A scripted mock-sender run (panic → tracking → check-in → low
   battery) would make demos reliable.

### Quality and hardening

6. **The phone-check backlog** (§4): most screens since 2026-10-13 have unit
   tests but no recorded device pass, including alert notifications (47
   cases), dark mode and the consent gate.
7. **UI and instrumented tests.** `ExampleInstrumentedTest` is still the
    template; nothing tests a screen, the `MapView` or the Firebase calls.
8. **Offline behaviour.** RTDB disk persistence is off, so a cold start
    offline shows nothing until the network returns.
9. **Localisation and accessibility.** All copy is English; ViewModel
    messages are Kotlin `MSG_*` constants, not resources, which makes
    translation harder. No accessibility pass has been done; the new check-in
    card may be read twice by TalkBack.

### Bigger ideas that need a decision first

10. **Safe zones / geofences**: alert when the band leaves an area. Needs the
    band's positions processed somewhere: on the phone through the
    foreground service (no server), and a notice change.
11. **Push notifications without the foreground service**: needs FCM
    sending, so Blaze or a server.
12. **A Play Store release**: rename `com.example.guardband` (cannot change
    after a first release; means re-registering the Firebase app and every
    fingerprint), a release keystore, and a paid MapTiler plan.
13. **Journeys ("walk me home").** The wearer starts a journey on the band
    before a trip they want watched; the guardian sees "On a journey since
    9:40 PM" and the route so far, quietly (no SMS, no alarm); the wearer ends
    it with "arrived", and tracking stops. Optionally an **overdue** warning
    when no "arrived" comes in time or the band goes silent: the part that
    makes it worth having, and the hardest without a server (the alert
    watch's foreground service could do it on the guardian's phone). Needs:
    new message types (journey start and end) agreed in `SCHEMA.md` with the
    firmware, a rules change (the rules accept only the four types today), a
    gesture the band's one button does not use yet, app screens (much like
    the Incident view), and a **Privacy Notice change and `CONSENT_VERSION`
    bump**, since the band would share location outside emergencies.
    Discussed 2026-10-27 and backlogged; nothing built.

### Waiting on design

14. **Band model and turntable frames** (waiting on Jul: design and
    modelling; the viewer is built, the model is not). The Device tab's band
    viewer plays a placeholder. Jul models the band in Blender from the
    concept image (charcoal gray composite strap, neon green perforated
    liner, brushed steel double-ring clasp, three round buttons with green
    LEDs, OLED strip) and renders 36 to 72 transparent WebP frames into the
    viewer's frame folder; no code change is needed. Decide first what the
    three round elements on the concept are: the concept labels them an
    HR/SpO2 sensor array, but the requirements and SCHEMA.md contain no
    heart-rate or blood-oxygen data, and showing one would imply collecting
    health data (Privacy Notice change and a CONSENT_VERSION bump). A
    real-time .glb viewer is a possible later replacement behind the same
    BandViewer interface and would need a dependency check against
    compileSdk 34 and a release manifest re-check.
    - The folder is `app/src/main/assets/band_turntable/`, files
      `band_turn_000.webp` onwards; the spec (direction of turn, size,
      transparency) is [`band-viewer.md`](band-viewer.md) §2.

---

## 4. Backlog

### Waiting on a person

| | Who | Notes |
|---|---|---|
| **Rules: record what is live, then close the watcher gap** | Jul | A+ with band identity was published 2026-11-04 ([`database-rules.md`](database-rules.md) §9). On 10 Nov `5f0b254` added watcher rules to `RTDBS-RULES.md` and a second copy, `RTDBS-RULES-CONSOLE.json`, which differ; no publish was recorded. §3 "Next steps" B and D; [`database-rules.md`](database-rules.md) §10. `devices/{id}/status` is now used by the status chips; `command` still by nothing (ask the firmware teammate before deleting it). |
| **Privacy Notice for connected accounts** | James | Watchers see a wearer's name and alerts (and, under the current rules, their contacts). Notice and `CONSENT_VERSION` before Connect is used by real people. §3 "Next steps" D. |
| **MapTiler key** | Jul | A dedicated key restricted by **Allowed user-agent header** to `GuardBand/1.0 (+https://github.com/jspdavis/GuardBand)` (`OsmdroidConfig.USER_AGENT`), shared privately, in each `local.properties` as `MAPTILER_API_KEY`. Without it Track says "Map unavailable". [`osmdroid.md`](osmdroid.md) §4.4. |
| **The Privacy Notice still says "Profile tab"** | James | Its Google profile picture row says the picture shows "on the Profile tab and on the map". It is now the Device tab. Nothing about the data changed, so it is a wording fix for a human to make (and decide whether it needs a version bump); the agent was told not to edit the hosted notice. |
| **Ask MapTiler about the collapsing credit** | Jul | Before any public demo. The ⓘ collapse follows OSMF's guidelines, not a MapTiler statement. [`osmdroid.md`](osmdroid.md) §4.6. |
| **Design pass on the placeholders** | Jul | The dark palette (`values-night/colors.xml`), the low-battery icon (triangle or battery), everything marked `DESIGN: Jul` (94 places: 4 for the alert pins and their label, 6 for the Incident view and the face's bubble), and two older theme changes to confirm with Jehan: 28 dp outlined-button corners, 24 dp sign-up top padding. |
| **Check the old `Guardband-MVVM` copy** | Jehan | The stray gitlink (to `9448b3c`, which exists only on her machine) was removed from the repo on 2 Nov (`1132d70`). Still to do: check nothing is stranded in that inner copy on her machine ([`guardband-mvvm-folder.md`](guardband-mvvm-folder.md)). |
| **Each teammate's fingerprints** | each developer | Debug **SHA-1** in the Firebase Console (else Google sign-in fails on their build); debug **SHA-256** in `hosting/public/.well-known/assetlinks.json` and Hosting redeployed (else reset and verification links open the browser). Four SHA-256s are live (James's and three teammates'). Release keystore's before any signed build. |
| **Band model and turntable frames** | Jul (design and modelling) | The Device tab's band viewer plays a placeholder. Jul models the band in Blender from the concept image (charcoal gray composite strap, neon green perforated liner, brushed steel double-ring clasp, three round buttons with green LEDs, OLED strip) and renders 36 to 72 transparent WebP frames into the viewer's frame folder; no code change is needed. Decide first what the three round elements on the concept are: the concept labels them an HR/SpO2 sensor array, but the requirements and SCHEMA.md contain no heart-rate or blood-oxygen data, and showing one would imply collecting health data (Privacy Notice change and a CONSENT_VERSION bump). A real-time .glb viewer is a possible later replacement behind the same BandViewer interface and would need a dependency check against compileSdk 34 and a release manifest re-check. Folder and frame spec: [`band-viewer.md`](band-viewer.md) §2 and §3. Not started. |
| **Band firmware** | firmware teammate | Write exactly `SCHEMA.md`'s payload: `history/{sequenceId}` first, then `latest`; a double-press within 2 s is a `CHECKIN` (FR-10); a **1.5 s hold is a `PANIC`**, fired while still held; during a panic a **5 s hold cancels** it (stops tracking, sends a `CHECKIN`), and a check-in alone does not; tracking every 60 s for 30 min after an alert (FR-09); low battery at 20% (FR-11). The Mock Sender copies these gestures (`BandButtonDecoder`): change both together. **Started (2026-10-28):** the Wi-Fi prototype in [`../firmware/`](../README.md#band-firmware-prototype) does all of this (tracking at the Mock Sender's 5 s demo pace), with the laptop standing in for the GPS and battery. Since 2 Nov it signs in as its own band (anonymous auth, `?auth=`) and checks certificates. Still to do: the NEO-6M, the Air780E (4G and SMS, FR-05), a real battery reading, and claiming over Serial (pairing step 3). |

### Phone checks

Built and unit-tested, never recorded on a phone. The developer tests on
their own phone each session, so this is a reference list, not a queue.
[`team-checklist.md`](team-checklist.md) "Phone checks" has the current
list; the step-by-step checklists live in each doc:

- 5 and 10 Nov changes, once §3 step A is done: day headings by date; the
  status chips (all three places they show, and a second phone seeing the
  change); Connect (share, redeem, expired, your own code, wrong code); the
  switcher (My Band, a friend, the "Now watching" switch on first load).
  [`incident-view.md`](incident-view.md) §6, [`device-pairing.md`](device-pairing.md) §9.
- 22–27 Oct changes: Contacts (search, chips, star, tap to edit, hold to
  delete, the new dialog), the inbox, the re-centre button, the five map
  layers and their persistence, the check-in card, Change password in Account
  information, no stale Google photo after switching accounts.
- Dark mode, every screen.
- Alert notifications: [`notifications-test-cases.md`](notifications-test-cases.md) (47 cases).
- Alert rows and place names: [`alert-data.md`](alert-data.md) §12.
- Map, face marker, credits and logo links: [`osmdroid.md`](osmdroid.md) §5.4 and §8.
- Alert pins on Track (27 Oct): [`osmdroid.md`](osmdroid.md) §8, "Alert pins".
- Incident view, its four ways in and the panic-pin rule (27 Oct): [`incident-view.md`](incident-view.md) §5.
- Band viewer (28 Oct): the placeholder now; drag, fling, auto-turn, scrolling, TalkBack and "remove animations" once frames exist: [`band-viewer.md`](band-viewer.md) §8.
- Device tab (27 Oct): status chip, battery, last alert and position, contacts count and tap-through, Settings' row, both account kinds, dark mode, no band data: [`device-tab.md`](device-tab.md) §8.
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
| **Close `devices/`** | Needs pairing (§3, item 1; [`device-pairing.md`](device-pairing.md) steps 2 and 6). Close it before any public demo. |
| **Rename `applicationId` off `com.example.*`** | Only if publishing (§3, item 12). |
| **Device tab avatar is slow to come back** | Reported 2026-10-28. The square-corner bug is fixed (the avatar is a plain `ImageView` clipped to a circle outline in `ui/avatar/AvatarImage`, no longer a `ShapeableImageView`), and the avatar is now shaped properly. But after the Fragment or Activity is destroyed and rebuilt (checked by switching display modes) the Google photo takes a while to reappear, showing the initials meanwhile. **Not investigated.** Leads, unverified: `AvatarImage.show` puts the icon back first and Coil only checks its memory cache once the view has been measured, and the cache key includes the size and the circle-crop transformation; a key that misses means a disk read and a decode. Check whether the photo comes from Coil's memory cache on a recreate before changing anything. The contact rows' avatars (`item_contact.xml`) are still `ShapeableImageView`s and could show square corners after a theme switch the same way. |
| **No re-consent flow** | Accounts keep the version they agreed to. Building one means deciding what happens to an account that declines. `ConsentGate.needsConsent` is where a version rule would go. |
| **Server-side enforcement of verification** | Only if ever wanted. A rule requiring `auth.token.email_verified` on `users/$uid` would fail every sign-up, because the profile is written before the email can be opened. |
| **Sweep finished deletions' markers** | Optional. Each completed Delete account leaves `users/{uid}: { deletion_pending: true }` under a uid no account owns. A human can delete them from the Console. |
| **A Terms page** | Only if the course needs one: a hosted `/terms`, `HostedPages.terms`, and a `CONSENT_VERSION` bump. |

### Parked code

In the tree with no caller, kept on purpose so it can be reused. Both came in
with `e97e15b` and were parked on 2026-11-02. Nothing here runs; revive it
only through the decision it waits on, and fix what is listed first.

**Alert status** (Active, Acknowledged, Resolved; §3 item 4). **No longer
parked: wired up on 2026-11-10 (`5f0b254`).** Of the list that stood here:
- **Fixed:** no uid stored (`updatedBy` removed); chips on panics only;
  failures shown, with a 10 s timeout on the write; `observeStatuses` wired
  into `AlertViewModel`, `IncidentViewModel` and `NotificationsViewModel`,
  with tests.
- **Still open:** stored on `devices/{id}/status`, the old LED node, which is
  world-writable (anyone can resolve a panic) and in the band's tree, which
  the main app is not meant to write; two sources (the parser still reads a
  `status` from the history payload, which nothing writes); `SCHEMA.md` and
  `RTDBS-RULES.md` do not describe the `status/{seq}` shape (the rules only
  validate the old `led`, `online`, `lastUpdate`); who may change it should
  come from pairing; Resolved does not end an incident.
  [`alert-data.md`](alert-data.md) §13.
- `FakeAlertRepository`'s status helpers are now used by the tests.

**Panic SMS sender** (FR-05; §3 item 3). Waits on pairing.
- In the tree: `ui/notifications/SmsPanicSender.kt`, whose KDoc lists the
  conditions; `SmsPanicSenderTest` (one blank-number case); the
  `sms_panic_body` string. `SEND_SMS` is out of the manifest.
- Before it is called: pairing; James's approval of `SEND_SMS`; a runtime
  permission prompt; a guard so a Mock Sender panic cannot text real
  contacts; a real link through `HostedPages` (the hard-coded
  `guardband.web.app/track/…` is not this project's host, and `/track` does
  not exist); who gets texted; wording for a panic with no fix (it would send
  0.0, 0.0); handling for a failed send; and the Privacy Notice and
  `CONSENT_VERSION` in the same commit. [`team-checklist.md`](team-checklist.md)
  has what pairing must deliver.

### Small cleanups

- `ic_edit` (the contact pencil) is now hidden in `item_contact.xml` since tap
  to edit; delete the button and its binding if it stays unused.
- The mock sender has no launcher icon of its own (it differs by label only).
- Refresh this file after each merge: the header's hashes and test count,
  §1's tables and §4.

---

## 5. Done, by date

The full, user-facing record is `Changelog.entries`
(`app/src/main/java/com/example/guardband/ui/changelog/Changelog.kt`), shown
in the app under Settings → Changelog. Headlines:

- **2026-11-10 (jspdavis, `5f0b254`):** connected accounts: Device → Pair a band opens Connect (share or redeem a 6-character invite code, one hour); a wearer switcher on Track (My Band or a connected wearer, Safe / Panic Alert / Offline); `FirebaseBandRepository` and `BandSelectionStore` wired; panic status chips (Active / Acknowledged / Resolved) in Alert history, the Incident view, Track's sheet and the inbox; watcher rules in `RTDBS-RULES.md` plus `RTDBS-RULES-CONSOLE.json`. Left the app on an empty band by default (§3).
- **2026-11-05 (Jehan, `653f29f`):** Alert history and the inbox grouped by date again (Today, Yesterday, each date); Alert history's 50 cap removed; the inbox reads the whole history; a Clear history button (refused by the rules).
- **2026-11-04 / 11-02:** pairing steps 1 and 2 (bands sign in as anonymous users; rules close writes on any band id but `guardband-001`, published 4 Nov); roles rolled back; panic SMS and alert status parked; new Track and Contacts icons; the stray gitlink removed.
- **2026-10-28:** a band viewer at the top of the Device tab (a turntable of rendered frames behind a `BandViewer` interface; a still placeholder until Jul's frames exist); the Device tab's avatar kept round through a theme switch, its text a size smaller; Contacts' filter chips in brand green (white in dark mode); Settings' Device information row removed.
- **2026-10-27:** the Device tab replaces Profile (band status, battery, last alert, connected account, pairing placeholder; Settings' Device information opens it), the Mock Sender plays the band (one button: hold = panic + tracking, double-press = check-in, 5 s hold = cancel; battery slider), the Incident view (a panic and its tracking path, from four places), alert pins on the Track map (panics keep their spot), map layers (five, remembered), re-centre button replaces the
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
| The Incident view: grouping, its read, its ways in | [`incident-view.md`](incident-view.md) |
| The Device tab: where each value comes from, pairing's shape | [`device-tab.md`](device-tab.md) |
| **Device pairing:** the vision, band identity, claiming, watchers, the steps | [`device-pairing.md`](device-pairing.md) |
| The band viewer: frames, their folder and spec, the placeholder | [`band-viewer.md`](band-viewer.md) |
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
  **It happened again on 2026-11-10:** `RTDBS-RULES.md` puts
  `watcher_wearers`'s `.read` on each child, while the app reads the list.
- **Green unit tests do not mean the right band.** Every test that needs bands uses
  `FakeBandRepository`, never `FirebaseBandRepository`, so the 10 Nov fallback (each account
  reading `devices/<its own uid>`) passed all 990. Logic that decides which
  band is read belongs in a pure function with its own test.
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
- **`README.md` is UTF-8 since 2026-10-29.** It was UTF-16 LE before, so
  its older commits show in git only as `Bin … differ`. It now also holds
  the firmware docs and the demo day checklist.
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
