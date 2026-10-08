# Handoff

**As of 2026-10-06 · `mvvm-main` @ `c40e01d` · 253 unit tests, 0 failures.**

Two pieces of work landed together: the **Alert tab on real Firebase data**, and
the **revamped sign-up flow**. This file is the orientation for whoever picks it
up — what is done, what is deliberately not, and what needs a human.

`mvvm-main` is **22 commits ahead of `origin/mvvm-main`** and has not been
pushed. 13 of those predate this work.

**Uncommitted, in the working tree:** the mock sender now writes to the RTDB
REST API (§10). `assembleDebug` + `testDebugUnitTest` green, 253 tests.

---

## 1. Read these first

| For | Read |
|---|---|
| The agent's working rules, conventions, gotchas | [`../CLAUDE.md`](../CLAUDE.md) |
| How alerts are read from RTDB | [`alert-data.md`](alert-data.md) |
| The post-login host and its tabs | [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md) |
| **Running a demo with no hardware** | [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md) — both launchers, end to end |
| The alert payload contract | [`../SCHEMA.md`](../SCHEMA.md) — **authoritative** |
| Draft Security Rules (**not deployed**) | [`../RTDBS-RULES.md`](../RTDBS-RULES.md) — committed in `3ccf153` |

The sign-up flow has no deep-dive of its own; §3 below is it.

---

## 2. What landed

Nine commits, oldest first:

| Commit | |
|---|---|
| `fb69971` | PH mobile validation tightened; `GoogleProfileProvisioning` → `ProfileProvisioning` |
| `ec5ccb3` | Alert model + `FirebaseAlertRepository` |
| `919f815` | Alert tab wired to real data |
| `7fa3328` | Alert data documented in CLAUDE.md |
| `81a6c97` | `docs/alert-data.md` |
| `3fccbc1` | Location step dropped; shared sign-up step design |
| `c259861` | Sign-up Step 1; wizard carries credentials forward |
| `bdf6d3a` | Sign-up Step 3 + the Consent commit |
| `c40e01d` | Step indicator hidden on the Google path |

Merged to `mvvm-main` as a **fast-forward**, so there is no merge commit marking
the feature boundary. `feature/alert-tab` still points at `c40e01d`.

---

## 3. The sign-up flow

### The journey

```
Login ──"Create an Account"──> Account ──> Name ──> Contacts ──> Consent ──> Loading ──> Home
                               step 1/3   step 2/3   step 3/3   (no step indicator)

Login ──"Continue with Google", first-time user──> Contacts ──> Consent ──> Loading ──> Home
                                                   (indicator hidden)
```

### Who writes what — the single most important fact

**Steps 1–3 write nothing.** They collect and pass along. **Consent is the only
screen that touches Firebase.** It calls `createAccount`, then `finalizeSignUp`,
which commits the profile, every contact and the consent record in **one atomic
multi-path write**.

That is why:

- abandoning the wizard at any point leaves **nothing** behind, and never
  occupies an email address;
- `SignUpContactsViewModel` takes **no repositories at all**;
- a **taken email address is not discovered until the final submit**. This is
  deliberate — see §3.3.

### 3.1 Files

| Screen | Classes | Layout |
|---|---|---|
| Step 1 Account | `SignUpAccountActivity` / `ViewModel` / `Event` | `activity_signup_account.xml` |
| Step 2 Name | `SignUpNameActivity` / `ViewModel` / `Event` | `activity_signup_name.xml` |
| Step 3 Contacts | `SignUpContactsActivity` / `ViewModel` / `UiState` / `Event` | `activity_signup_contacts.xml` |
| Consent | `SignUpConsentActivity` / `ViewModel` / `UiState` / `Event` | `activity_signup_consent.xml` |
| Shared header | `SignUpStepHeader` | `view_signup_step_header.xml` |

Steps 1 and 2 have **no UiState** — they do no async work, matching the
project's "events only" convention for such screens.

### 3.2 The shared design

Restyling should touch **one place**, not four layouts:

- `view_signup_step_header.xml` — back arrow, centred "Step n/3", three bars.
  No Skip: every step is required.
- `SignUpStepHeader.bind(activity, step)` fills the right bars. An `<include>`
  cannot vary its children per use site, so this is set in code.
  `bindBackOnly(activity)` hides the indicator entirely.
- `themes.xml` — `Widget.GuardBand.SignUpStep` (the page frame),
  `.SignUpInput`, `.SignUpContinue`, and `TextAppearance.GuardBand.SignUpTitle`
  / `.SignUpSubtitle` / `.SignUpFieldLabel`.

Step 3 **reuses the Contacts tab's** `ContactAdapter`, `item_contact.xml` and
`dialog_contact_editor.xml`. Don't fork them — the two screens drifting is
exactly what the reuse prevents.

### 3.3 Decisions, and why — please don't silently reverse these

**Location was removed entirely.** `User.location`, `users/{uid}/location`, the
whole `SignUpLocation*` screen, the Profile row and the Track chip's location
line are gone. A city typed once is worse than nothing beside the band's own
GPS, and a stale one on the Track chip actively misleads. CLAUDE.md says not to
reintroduce it without deciding what reads it.

**The account is created at the final submit, not at Step 1.** Trade-off
accepted knowingly: no orphan accounts, at the cost of late "email already in
use".

**Step 1 does not check whether an address is taken.** It would be the kinder
place to find out, but that is precisely the question Firebase's
email-enumeration protection exists to refuse, and Login and forgot-password
both already decline to answer it. A sign-up screen that answered it would undo
that for all three.

**Sign-up requires one contact; the banner asks for three.**
`SignUpContactsViewModel.MIN_AT_SIGNUP = 1`, while the banner counts toward
`InputValidator.MIN_CONTACTS = 3`. A band in use with one contact beats a
sign-up abandoned at the third. The Contacts tab's repository-level minimum of
3-on-delete is unchanged and still protects the **stored** record.

**Consent is recorded, not just ticked.** `finalizeSignUp` writes
`users/{uid}/consent/{accepted, acceptedAt, version}` in the same atomic commit,
so a profile can never exist without one. `acceptedAt` is
`ServerValue.TIMESTAMP`, not the handset clock, because a device clock can be
wrong or deliberately set. **Bump `UserProfileRepository.CONSENT_VERSION`
whenever `label_signup_consent_statement` changes what is being agreed to.**

**No Skip.** Every step is required, so a Skip control would always refuse.

**Registration is two calls, and Retry re-runs only the second.** The
`accountCreated` guard in `SignUpConsentViewModel` is load-bearing: when these
were one call, a failed write left the address unable to finish registering at
all, because the only retry went back through account creation and failed as
"email already in use". **If you refactor Consent, keep that split.**

### 3.4 How the wizard carries its data

Intent extras, matching the project convention. `EmergencyContact` is
`Parcelable` (via `kotlin-parcelize`) so the staged list can ride along.

This means the password and the staged contacts' names and numbers sit in
Intents between the four screens. Explicit intents to in-app components do not
leave the process, and nothing logs them. **If you want that tightened**, a
small in-memory sign-up session object replaces all five extras — one contained
pass. The one thing it would lose is surviving process death, which extras do.

---

## 4. The Alert tab

Reads `devices/{deviceId}/history` live. Full detail — ordering, parsing,
the error/empty/stale split, what is and is not tested — in
[`alert-data.md`](alert-data.md). The short version:

- `orderByKey().limitToLast(100)` is correct and **needs no `.indexOn`**.
- Tracking updates are hidden; the latest card comes from history, not
  `devices/{id}/latest`.
- Parsing is a pure `Map → Alert` step in `AlertParser` so it is testable
  without a mocking library. **Don't "simplify" it into
  `getValue(Alert::class.java)`** — that reintroduces the `isCharging` →
  `charging` mapper bug, silently.

---

## 5. Deliberately unfinished

| | Where | Notes |
|---|---|---|
| **Privacy Notice is not a link** | `activity_signup_consent.xml` | Marked `DESIGN: Jul`. Until it points at a real notice, the consent record references wording the user cannot read. **This is the one that matters most** — it undermines the consent record. |
| **`users/{uid}` Security Rules not deployed** | `RTDBS-RULES.md` (committed draft) | Must be merged with the existing `devices/` rules in the Console **by hand**. Now also needs a `consent` subtree and a `phone` `.validate`. |
| **`devices/` history is world-readable** in the draft | `RTDBS-RULES.md` | `".read": true` on wearer coordinates. Close before any public demo. Note the **write** side is also open, and the mock sender depends on it — tightening `".write"` to require auth means teaching `AlertSender` to send an `?auth=` token (§10). |
| **Delete account** | — | Not started. Privacy law and Play expect it for apps with accounts. |
| **`SignUpNameViewModel.onGoogleSignInClicked`** | `ui/signup/` | Unreachable — no Google button on the name screen since the redesign. Kept on purpose, flagged in its KDoc. |
| **`bg_signup_continue.xml`** | `res/drawable/` | Committed but unused; `Widget.GuardBand.SignUpContinue` replaced it. Fold it into the style or delete it. |
| **`item_contact.xml` edit icon** | `res/layout/` | Placeholder — there is no pencil drawable. |

---

## 6. Needs a human, not an agent

- **Deploying Security Rules** — Console only, and the draft must be merged with
  what is already live. Nobody has seen the deployed `devices/` rules from the
  tooling side; everything written about them is inferred from the draft.
- **Registering your own debug SHA-1** in the Firebase Console, then
  re-downloading `google-services.json`. **Google sign-in fails on an
  unregistered machine even though the app builds fine.** The release keystore's
  SHA-1 must be added before any signed build.
- **Manual testing of the Alert tab** — the checklist in
  [`alert-data.md` §12](alert-data.md) works from the Console.
- **Figma** is the visual source of truth and is Jul's. Everything above uses
  theme tokens and placeholder spacing; `DESIGN: Jul` marks the spots.

---

## 7. Building and verifying

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # JDK 17
./gradlew assembleDebug testDebugUnitTest
```

**The `java` on PATH is JDK 25 and Gradle 8.4 cannot run on it.** Set
`JAVA_HOME` explicitly; it is not reliably inherited.

Run **one** Gradle build at a time — two concurrent builds write the same
`app/build/tmp/kotlin-classes/debug/` and fail with a bare directory listing
that looks like a compile error but is not.

To build a commit without disturbing a dirty working tree, use a throwaway
worktree. `local.properties` is gitignored, so copy it in or the build fails
with "SDK location not found". Remove the worktree with PowerShell +
`robocopy /MIR` from an empty directory — `git worktree remove` fails with
"Filename too long" on Gradle output.

### What is not covered by tests

`FirebaseAlertRepository`'s `callbackFlow`, its live listener, and **whether
`awaitClose` actually removes it**. That needs a real `FirebaseDatabase` and the
project declares no mocking library. Everything testable was lifted out instead.
Worth one manual check: open the Alert tab, background the app, confirm RTDB
traffic stops.

Nothing in the UI layer is instrumented-tested. `ExampleInstrumentedTest` is
still the template.

---

## 8. Traps that have already cost time

- **A view removed from a layout with only its `findViewById` line deleted.**
  The field and its use remain, and it crashes at runtime with
  `UninitializedPropertyAccessException`, *not* at compile time. This happened
  twice (`LoginActivity.progressBar`, `SignUpNameActivity.btnGoogle`). If you
  remove a view, remove the field and every use of it.
- **An unescaped apostrophe in `strings.xml`** fails `mergeDebugResources`
  naming neither the string nor the line. Write `\'`.
- **A `--` inside an XML comment** fails the same task with a
  `SAXParseException`. Don't rule off comments with rows of dashes.
- **A duplicated `<string name="…">`** fails with "Found item String/x more than
  one time". Shared names like `cd_back` already exist — grep first.
- **`README.md` is UTF-16 LE with CRLF.** A naive script read collapses all 205
  CRLFs to LF — a whole-file change that git shows only as `Bin … differ`, so
  there is no diff to catch it. Read it with `newline=''`.
- **A `StateFlow` drops an emission equal to the previous one.** This made "same
  data, but this time the read failed" invisible to a collector in a test fake;
  `FakeAlertRepository` uses a replaying `MutableSharedFlow` for that reason.
  Don't convert it back.

---

## 9. Suggested next steps

1. **Give the Privacy Notice a URL and make it a link.** Everything else in the
   consent flow is built; this is what makes it mean something.
2. **Deploy the Security Rules**, including the new `consent` subtree and the
   phone `.validate`, and close the world-readable `devices/` read.
3. **Delete account** in Settings.
4. **A real tile source** before anything public. The map is on OpenStreetMap's
   public servers, whose usage policy is written for light use (§11).

(The mock sender fix and the Track tab + map that used to be items 3 and 4 are
both done — §10 and §11.)

---

## 10. The mock sender (now writes)

`app/src/debug/.../AlertSender.kt` no longer POSTs to a Cloud Function that
does not exist. It writes straight to the RTDB REST API, the way the band will,
so **the Firebase Console is no longer the only way to inject a test alert**.
Install the debug build and open the "GuardBand Mock Sender" launcher icon.

Per send, in this order:

```
PUT {databaseUrl}/devices/guardband-001/history/{sequenceId}.json
PUT {databaseUrl}/devices/guardband-001/latest.json
```

### The four decisions

**History before `latest`.** The Alert tab derives everything from history
(decision D2 in `alert-data.md`), so a send that got as far as history is still
visible there and only leaves the Track-facing `latest` node stale. The reverse
would show the alert nowhere in the Alert tab *and* leave a permanent hole in
the history. The sequence id is consumed the moment its history row lands, so a
retry cannot overwrite a good row.

**The sequence id is seeded from the server,** by reading the history keys
with `?shallow=true` once per `AlertSender` instance and taking the highest
integer-parseable one. The old `sequenceId = 1` field meant a relaunched
harness overwrote the rows it wrote last run, which would have made the Alert
tab's own history unreliable to test against.

`shallow` rather than ordering by key and taking the last one: RTDB sorts
integer-like keys *ahead* of string keys, so a `limitToLast(1)` would hand back
a **string** key if the node ever picked one up from the Console — which is the
one case this read exists to survive. A max over the integer-parseable keys
cannot be fooled that way, and it skips downloading a payload. A missing node
reads as the body `null` — not an error — and starts at 1; a genuine read
failure **fails the send**, deliberately, because the `PUT` was about to fail
the same way.

**Walk mode** sends a `TRACKING_UPDATE` every 5 s around a ~165 m loop centred
on Cebu City, with the battery draining as it goes. The interval is far below
FR-09's real 60 s on purpose: a demo should show the marker moving within
seconds, and anything under the Track tab's staleness threshold keeps the band
reading as online. Two deliberate stops: it cancels in `onPause`, because a
walk still writing under a backgrounded harness would make a dead band look
alive, and a failed step stops the whole walk rather than rewriting the same
error every 5 s and hiding which step first broke.

**The database URL comes from `FirebaseProvider`,** so the harness follows
`google-services.json` to whichever project the build points at. This is a
Firebase import outside a repository, which the app's layering forbids — the
harness is explicitly outside that layering, and the alternative was a
hardcoded URL, which is forbidden everywhere.

**The duplicated `AlertType` is gone.** It was kept on the reasoning that
de-duplicating it would mean editing `app/src/debug/`; that does not hold, since
the debug source set imports from `main` freely. The harness now uses
`data/model/AlertType` and `DeviceConstants.DEFAULT_DEVICE_ID`, because a
drifting enum or device id would have it writing alerts the app never reads.
The dependency still points one way only: nothing in `main` knows the harness
exists.

**Writes are unauthenticated,** relying on `".write": true` under
`devices/{$deviceId}` in `RTDBS-RULES.md`. See §5 — tightening that is a
reasonable thing to want, and it means teaching `AlertSender` to append an
`?auth=` token.

### Verifying it

Not covered by tests, and cannot be: it is debug-only code whose whole job is a
network call, and the project declares no mocking library. It needs one manual
pass — the fuller version, with the receiver end alongside it, is in
[`mock-sender-and-receiver.md`](mock-sender-and-receiver.md):

1. Open the Mock Sender, tap **PANIC**. The status line should read
   `✓ Sent PANIC as #N` — the `#N` is the history key, so it can be checked
   against the Console directly.
2. Confirm `devices/guardband-001/history/N` and `.../latest` both appeared.
3. Open the Alert tab on the receiver and confirm the panic shows.
4. Tap **TRACKING_UPDATE**. It must update `latest` and **not** appear in the
   Alert list (decision D1).
5. Kill and reopen the Mock Sender, send again, and confirm the new id
   continues the sequence rather than restarting at `1` — that is the
   server-seeding working.
6. Tap **Start walk**. The status line should count up (`Walking: n sent,
   latest #N`) every 5 s, and `latest.location` should trace a circle. Tap
   **Stop walk**, or background the harness, and it must stop.

If a send fails, the status line carries the HTTP code and path. A **401** means
the `devices` rules are tighter than the draft; a **400** means the payload
failed a `.validate`.

---

## 11. The Track tab and its map

`feature/track-map`, three commits on top of the mock sender work. The tab reads
`devices/{deviceId}/latest` live — the one screen that should, since it wants the
newest ping whatever kind it was, which is exactly why the Alert tab derives its
card from history instead (D2). Full rules in
[`../CLAUDE.md`](../CLAUDE.md) under "Track and map"; what matters on pickup:

**osmdroid 6.1.20, no API key and no billing.** Verified safe on compileSdk 34:
zero dependencies in its POM, no Gradle module metadata, so no `minCompileSdk`
constraint. The merged manifest gains **no permission at all** from it — only
four optional `uses-feature` entries.

**Do not add a phone-location permission, and do not use
`MyLocationNewOverlay`.** The band supplies the position. That overlay is the one
thing in osmdroid that would require `ACCESS_FINE_LOCATION`.

**The badge says "Reporting", not "Online", on purpose.** The threshold is 180 s
(3 × FR-09's 60 s), but FR-09 tracking is *post-alert* and stops after 30
minutes — so an idle, perfectly healthy band sends nothing and reads as stale.
The honest signal is "Last seen X ago", which is why that line leads. Don't
relabel the badge without deciding what a band heartbeat would look like, which
is firmware that does not exist.

**The staleness ticker is load-bearing.** A band going quiet emits nothing, so
without it the badge could never leave "Reporting". It is injected as a
`Flow<Unit>`; tests pass `emptyFlow()`. **Don't convert it to an internal
`while (true) { delay(…) }`** — any test then calling `advanceUntilIdle()` hangs
forever instead of failing.

**Three unfinished or deliberate gaps:**

| | |
|---|---|
| **The layers button and the Check-in pill are UI-only stubs** | D7. Check-in is FR-10's band double-press; the app must never send one. |
| **`BandStatus` duplicates `AlertFormatting`'s two ISO patterns** | Sharing them means editing the Alert tab, which the pass was scoped out of. One-line fix when that tab is next open. |
| **OSM's public tile servers** | Volunteer-funded, usage policy written for light use. Needs a real tile source before any public demo. |

### Verifying it

Nothing in the UI layer is instrumented-tested, so the map needs a manual pass.
`OsmdroidConfig`, the `MapView` lifecycle, the marker and the D4 follow logic
are **not** covered — all need a real Context or a running map. Walkthroughs for
all of it are in
[`mock-sender-and-receiver.md`](mock-sender-and-receiver.md) §6.

1. Open the Track tab with an empty `devices/guardband-001`. Expect the Cebu
   City view at zoom 13 and "Your band hasn't reported yet." No status panel.
2. Send one **PANIC** from the Mock Sender (§10). A marker appears, the camera
   moves to it at street zoom, and the panel reads "Reporting · Last seen just
   now · Battery n%".
3. **Start walk.** The marker should trace a circle every 5 s and the battery
   should tick down.
4. Stop the walk and wait **3 minutes**. The badge must flip to "Not reporting"
   on its own, with no app interaction — that is the ticker.
5. **Pan the map,** then send another alert: the camera must *not* jump. Tap
   recenter: it should move to the band and resume following.
6. Tap **Navigate** — a maps app opens on the band's position. It is disabled
   whenever there is no fix.
7. Background the app with the Track tab open and confirm RTDB traffic stops
   (the same check `alert-data.md` asks for).
