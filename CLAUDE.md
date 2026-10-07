# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

> Picking this up cold? Read [docs/handoff.md](docs/handoff.md) first — current state, open items and the traps that have already cost time.

GuardBand is an Android (Kotlin) companion app for an IoT wrist-worn safety band (ESP32 + Air780E 4G LTE + NEO-6M GPS). The band itself sends SMS to emergency contacts and writes alerts directly to Firebase Realtime Database over REST; the app reads that data so guardians can watch in real time. The firmware does not exist yet. Until it does, a phone acts as the band (see "Mock sender" below). This is an academic project; the requirements are FR-01..FR-11 in README.md.

## Build & test

Single-module Gradle project (`:app`). AGP 8.3.2, Kotlin 1.9.24, compileSdk/targetSdk 34, minSdk 24, Java/JVM target 1.8. Versions live in `gradle/libs.versions.toml`, but Firebase and CardView are declared inline in `app/build.gradle.kts`.

```bash
./gradlew assembleDebug                      # build debug APK
./gradlew installDebug                       # install on a connected device/emulator
./gradlew testDebugUnitTest                  # JVM unit tests
./gradlew testDebugUnitTest --tests "com.example.guardband.ExampleUnitTest"   # single test class
./gradlew connectedDebugAndroidTest          # instrumented tests (needs a device)
./gradlew lint
```

On Windows, use `gradlew.bat` (or `./gradlew` from Git Bash). The only tests right now are the template `ExampleUnitTest` and `ExampleInstrumentedTest`.

`app/google-services.json` is required for the `com.google.gms.google-services` plugin. It points at the Firebase project `guardband-aae65` (RTDB only, `asia-southeast1`, Spark plan, so there are no Cloud Functions).

## Branches matter

Each branch holds a deliberately different architecture. Check which one you are on before you change anything:

| Branch | Purpose |
|---|---|
| `mvvm-main` | The real product build (MVVM) |
| `mvp-checkpoint` (+ `-local-backup`, `-phone`) | Instructor-assigned checkpoint that must stay **strict MVP** (Login, Register, Forgot Password, Change Password, Dashboard). Kept separate so it doesn't mix with product work. |
| `mvvm-jedd`, `ishi` | Individual teammates' working branches |
| `feature/firebase-auth` | Cut from `mvvm-main`. Firebase Auth + the `users/{uid}` profile. |

Do not port MVVM code into the MVP checkpoint branches, or MVP code into the MVVM branches.

## Architecture (MVVM, on the `mvvm-*` and `feature/*` branches)

View ↔ ViewModel ↔ Repository. Pre-auth screens are Activities. After login, everything lives in one host Activity, `HomeActivity`, whose tabs and pushed screens are **Fragments**. That's the one deliberate exception to "every screen is an Activity" (see "Home host" below and `docs/home-host-and-bottom-nav.md`). There is no MVP code left (no Contracts, Presenters or `base/`).

```
com/example/guardband/
├── data/
│   ├── model/            User, EmergencyContact, Alert (+ AlertType), GoogleSignInOutcome (plain data classes; no passwords)
│   ├── repository/       AuthRepository (suspend, Result<T>)
│   │                     ContactRepository (observe = Flow<Result<T>>; CRUD = suspend)
│   │                     AlertRepository (Flow<Result<T>>, live reads)
│   │                     UserProfileRepository (reads/writes users/{uid}, incl. the atomic finalize)
│   │                     AuthError + FirebaseAuthErrorMapper (typed auth failures)
│   │                     ContactError + AlertError + FirebaseDatabaseErrorMapper (typed RTDB failures)
│   │                     AlertParser (wire map → Alert; pure, so it is unit-testable)
│   │                     ContactFields (the one on-disk shape of a contact)
│   │                     ProfileProvisioning (when a sign-in may write users/{uid}; both paths)
│   │                     Firebase* = live; InMemory* + InMemoryStore = test doubles
│   ├── DeviceConstants.kt      DEFAULT_DEVICE_ID = "guardband-001" (until pairing exists)
│   ├── FirebaseProvider.kt     the one FirebaseAuth / FirebaseDatabase instance
│   └── RepositoryProvider.kt   manual wiring; swap implementations here
├── ui/
│   ├── splash/         Splash → Home if signed in, else Login (2 s countdown)
│   ├── auth/           GoogleIdTokenProvider + GoogleIdTokenResult (the only androidx.credentials users)
│   ├── login/          email + password, and "Continue with Google"
│   ├── signup/         Account → Name → Contacts → Consent; Contacts → Consent in complete-profile mode
│   ├── forgot/         Request → Sent (Firebase emails the link; no OTP screens)
│   ├── loading/        1.8 s transition → Home (Back blocked)
│   ├── home/           HomeActivity: bottom-nav host + second auth gate (HomeViewModel)
│   ├── track/          Track tab (default): user chip, bell, gear, map placeholder, Check-in pill
│   ├── contacts/       Contacts tab: list + delete
│   ├── alert/          Alert tab: latest status + incident history
│   ├── profile/        Profile tab: welcome header + user info
│   ├── settings/       pushed from Track's gear; holds Log Out
│   └── notifications/  pushed from Track's bell; placeholder inbox
└── utils/InputValidator.kt     shared input predicates (no messages)
```

**Auth, the user profile, contacts and alerts are all on Firebase** (see "Firebase", "Contacts and profile data" and "Alert data" below). Nothing in the app reads `InMemoryStore` any more; it is the unit tests' backing store.

`InMemoryAuthRepository`, `InMemoryContactRepository` and `InMemoryAlertRepository` are still in the tree but are **not** wired into `RepositoryProvider` — they are the unit-test doubles for the Firebase implementations and return the same `AuthError`s / `ContactError`s / `AlertError`s, so each pair stays interchangeable. `InMemoryStore.replaceContacts` exists purely as a test seam, because `addContact` assigns its own id.

`ui/dashboard/` and `res/layout/activity_dashboard.xml` are gone, replaced by `ui/home/`. Two leftovers from them are still waiting to be removed: the `label_dashboard_*`/`label_your_contacts` strings and the CardView dependency, which now has no user at all.

Conventions (copy `ui/login/*` as the reference):
- **State:** one `XUiState` data class per screen, held in a `MutableStateFlow` and exposed as a `StateFlow`. Update it only with `_uiState.update { it.copy(...) }`.
- **Events:** one-shot events (navigate, toast) go in a sealed `XEvent` sent through `Channel(Channel.BUFFERED)` and exposed with `receiveAsFlow()`. Never encode navigation as state. A screen with no state of its own (Splash, Loading, Sign-up Name) has events only.
- **ViewModels** take repositories through the constructor and hold no `Context` or View. They expose `companion object { val Factory = viewModelFactory { initializer { XViewModel(RepositoryProvider.…) } } }`, and Activities use `by viewModels { XViewModel.Factory }`; plain `by viewModels()` is fine when there are no dependencies. User-facing messages are `MSG_*` constants in the ViewModel — including the wording for every `AuthError`, which each screen maps itself in a private `messageFor(error)`. Those messages stay in Kotlin rather than `strings.xml` precisely because a ViewModel holds no `Context`.
- **Activities** bind views with `findViewById`, forward raw input and clicks, render state, and execute events (Toast, `startActivity`, `finish`). They collect each flow in its own `lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`. Building `Intent`s stays in the Activity.
- **Wizard data** moves between screens as Intent extras. The Activity passes the extras into the ViewModel's click handler, and the navigation event carries the data back.
- **Exceptions:** `ForgotSuccessActivity` and `NotificationsFragment` have no ViewModel. Each is a static screen whose only action is navigation.

### Home host (Fragments)

- **No Navigation Component.** `HomeActivity` drives a `BottomNavigationView` (`menu_home_bottom_nav.xml`) with a manual `FragmentManager`: each Fragment is added once by tag, then shown or hidden, so tab state survives switches. Don't add `replace()`, a back stack, or a nav graph.
- **Tabs:** Track (default), Contacts, Alert, Profile. **Pushed screens:** Settings (gear) and Notifications (bell), both opened from Track's top bar. While one is showing, the bar stays visible with no tab checked, and Back (or the screen's back arrow) returns to the last tab. Back on a tab exits.
- **Auth gate (two checks):** `SplashViewModel` routes on `isLoggedIn()`, and `HomeViewModel` re-checks in `init` and emits `NavigateToLogin`. The second check used to be the load-bearing one, because the in-memory session died with the process; now that the session is `FirebaseAuth.currentUser` it rarely fires, and it is kept for a restore after the account was signed out or disabled elsewhere. Settings and Notifications are inside the host, so they're covered too.
- **Fragments follow the Activity conventions,** with these differences:
  - Use `Fragment(R.layout.x)`, and bind views in `onViewCreated` with `view.findViewById`.
  - Collect flows with `viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(STARTED) { … } }`.
  - Get the screen's own ViewModel with `by viewModels { XViewModel.Factory }`.
  - Host navigation goes through the shared `HomeViewModel` via `by activityViewModels { HomeViewModel.Factory }` (e.g. Track's gear calls `homeViewModel.onSettingsClicked()`). The Activity executes the resulting `HomeEvent`; Fragments never cast `requireActivity()` to `HomeActivity`.
  - A pushed screen's back arrow calls `requireActivity().onBackPressedDispatcher.onBackPressed()`.
- **Lists** use `ListAdapter` + `DiffUtil` with `findViewById` in the ViewHolder (`ContactAdapter`, `AlertHistoryAdapter`).

Declared dependencies: AppCompat, Material, ConstraintLayout, CardView (now unused; pending removal), activity-ktx, fragment-ktx 1.8.2, RecyclerView 1.3.2, lifecycle-viewmodel-ktx and lifecycle-runtime-ktx 2.8.7, kotlinx-coroutines-android 1.9.0, kotlinx-coroutines-play-services 1.9.0, and the Firebase BoM 32.8.1 with Analytics, **Auth** and Database. Tests add kotlinx-coroutines-test and Robolectric. OkHttp stays `debugImplementation`. Everything except CardView now lives in `gradle/libs.versions.toml`. Google sign-in adds **androidx.credentials 1.3.0**, **credentials-play-services-auth 1.3.0** and **googleid 1.1.1** — pinned there because newer versions need compileSdk 35 (see "Google sign-in" below).

## Firebase

Project `guardband-aae65` (RTDB only, `asia-southeast1`, Spark plan — so no Cloud Functions). Auth is **email + password** and **Google** (see "Google sign-in" below).

- **Firebase calls live only in repository classes.** No Firebase import belongs in an Activity, Fragment or ViewModel. `FirebaseProvider` owns the single `FirebaseAuth` and `FirebaseDatabase`; `FirebaseDatabase.getInstance()` takes the URL from `google-services.json`, so never hardcode it.
- **The session is `FirebaseAuth.currentUser`,** which the SDK persists, so it survives process death. `currentUser()` is synchronous and safe in a ViewModel's constructor, but it fills `id`, `name` (the Firebase display name, set at sign-up) and `email`, which is the whole of `User`. Profile still reads `users/{uid}` because the stored record is authoritative, but the screen is never blank while it waits.
- **Failures are typed.** Repositories return `Result.failure(AuthError)`, and `FirebaseAuthErrorMapper` maps `FirebaseAuthException.errorCode`, never the exception message — messages are localised and change between SDK versions. `AuthError` carries no message and suppresses its stack trace, so Firebase text cannot reach a log or a Toast.
- **Login must not distinguish a wrong password from an unknown account,** and forgot-password must confirm identically for an unregistered email. With email-enumeration protection on, Firebase returns the same code for both anyway; the UI must not undo that.
- **Password reset is Firebase's hosted flow.** `sendPasswordResetEmail` emails a link and the user finishes in a browser. The app never sees a reset code, so there is no verify-code or set-new-password screen, and nothing like `otpPlain` or `pendingPassword` is ever written.
- **No PII in logs.** Never log a uid, an email, a token or Firebase error text.
- **Never overwrite `app/google-services.json` from another branch,** and don't touch it, `.firebaserc` or any signing config as a side effect of other work.
- **The `users/{uid}` Security Rules are not deployed.** The proposal in the Prompt 10 report still has to be merged with the existing `devices/` rules in the Console, by hand. `RTDBS-RULES.md` in the repo root is a working draft, not what is live.

## Google sign-in

**Credential Manager only.** The legacy `GoogleSignIn` / `play-services-auth` sign-in API is never used. `play-services-auth` is on the classpath solely because `credentials-play-services-auth` needs it as its backend provider; nothing imports it. Do not copy the Google sign-in code, the `default_web_client_id` placeholder or `play-services-auth` from `mvp-checkpoint-local-backup` — that branch uses the legacy API.

**The token flow has three layers, and each one knows the least it can:**

1. **Activity layer.** `ui/auth/GoogleIdTokenProvider` is the *only* class in the app that touches `androidx.credentials`. Credential Manager renders a sheet, so it needs an Activity context — which is exactly why a ViewModel must not own it. It returns a typed `GoogleIdTokenResult`: `Success(idToken)`, `Cancelled`, `NoGoogleAccount`, `Unavailable`, `Network` or `Unknown`. It never swallows an exception into null, and it rethrows `CancellationException`.
2. **ViewModel.** Receives nothing but the token string (`onGoogleIdToken`) or a `GoogleIdTokenResult` (`onGoogleError`). No Context, no Credential Manager type. `Cancelled` is **silent** — the user dismissed the sheet on purpose and does not need to be told.
3. **Repository.** `AuthRepository.signInWithGoogle(idToken)` exchanges it with Firebase via `GoogleAuthProvider.getCredential(idToken, null)` + `signInWithCredential`, and returns a `GoogleSignInOutcome(user, isNewUser)`.

**`default_web_client_id` is generated**, not written. The Google Services plugin derives it from the web (`client_type: 3`) `oauth_client` entry in `google-services.json` and emits it into `app/build/generated/res/processDebugGoogleServices/values/values.xml`. Never hardcode a client ID, and never commit one to a source file.

**Every developer must register their own debug SHA-1** in the Firebase Console, then download the regenerated `google-services.json`. Google sign-in fails on an unregistered machine even though the app builds fine — the signing certificate is part of what Google checks. The release keystore's SHA-1 has to be added before any signed build. (`google-services.json` currently holds one Android `oauth_client` entry with one certificate hash.)

**`setFilterByAuthorizedAccounts(false)`** on `GetGoogleIdOption`, so the chooser offers every account on the device. With it true a first-time user is shown an empty sheet.

**Routing:** `isNewUser` decides. A returning user goes to Home. A first-time user goes through the remaining sign-up step (emergency contacts) in **complete-profile mode** — `SignUpContactsActivity` carries `EXTRA_COMPLETE_PROFILE`, the credential fields are hidden, nothing is validated against them, and `saveProfile` finishes `users/{uid}` instead of `register` creating an account. In that mode the uid and email come from the **live session**, never from the Intent extras, so a stale extra cannot write to the wrong record.

**The profile write is conditional.** A sign-in has no location to offer, so writing the profile on every sign-in would put an empty one over whatever the user had already set. `ProfileProvisioning` writes only when the account was just created or the record is confirmed absent; a *failed* existence read writes nothing, because it cannot tell "absent" from "unreachable". A failed write does **not** fail the sign-in — the session already exists by then. It serves **both** sign-in paths: email login provisions the same way, so a session always has a `users/{uid}` record behind it. (It was `GoogleProfileProvisioning` until the Alert-tab pass renamed it.)

**Error wording stays neutral.** `ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL` maps to its own `AuthError.AccountExistsWithDifferentCredential`, shown as "This email uses a different sign-in method." It must not name the other provider: saying "that address uses a password" confirms the account exists, which is the whole thing the shared invalid-credentials message exists to prevent.

**Log out clears the credential state.** `SettingsFragment` calls `GoogleIdTokenProvider.clearCredentialState()` before navigating, so the chooser reappears instead of silently reusing the last account. It is bounded by a 2 s timeout and its failures are ignored — the user is already signed out of Firebase, so nothing may keep them on that screen. `HomeViewModel`'s failed auth gate does **not** clear it: that is a dead session, not a user logging out.

**Never log a token**, an account name or an email, here or anywhere else.

**Not covered by tests:** the Firebase exchange inside `signInWithGoogle` and `GoogleIdTokenProvider` itself. Both need a real `FirebaseAuth` / Credential Manager, and the project declares no mocking library. The testable logic was extracted instead — `ProfileProvisioning` for the write rule, the ViewModels for routing and wording.

## Contacts and profile data

The signed-in user's own record, separate from the band's `devices/` tree:

```
/users/{uid}/name
/users/{uid}/email
/users/{uid}/emergency_contacts/{pushId}/{name, phone, relationship}
/users/{uid}/consent/{accepted, acceptedAt, version}
```

- **There is no `location`.** It was a city or region typed at sign-up, and it was dropped with the sign-up revamp: the band's GPS is the real location, and a stale self-reported city on the Track chip was worse than nothing. The field, its sign-up step and its Profile/Track rows are gone — don't reintroduce it without deciding what reads it.
- **`phone` is always E.164.** `InputValidator.normalizePhoneToE164` is the only thing that decides the stored form: it takes `09XXXXXXXXX`, `639XXXXXXXXX` and `+639XXXXXXXXX` and stores all three as `+639XXXXXXXXX`, and it accepts any other number that is already valid E.164 (`+` and 8–15 digits), stored as typed. It strips spaces, hyphens, parentheses and dots first. ViewModels pass the phone through **untouched** — normalising in a screen would mean two places could disagree. `relationship` is optional and stored as `""` when blank.
- **Minimum 3, cap 10.** `InputValidator.MIN_CONTACTS` / `MAX_CONTACTS`. Enforced in the **repository**, not just the dialog: a delete that would leave fewer than 3 fails with `ContactError.MinimumContacts`, and an add at 10 with `MaximumContacts`. Editing is always allowed — it is the way out of a wrong number. Security Rules **cannot** enforce these, because RTDB rules cannot count children, so the repository is the last line. The Contacts tab shows "n of 3 minimum" until the user reaches it, and so does sign-up step 3 — but **sign-up requires one, not three** (`SignUpContactsViewModel.MIN_AT_SIGNUP`). A band in use with one contact beats a sign-up abandoned at the third.
- **Every mutation is a transaction** on the `emergency_contacts` node, not a write to one child. The bounds are counts over siblings, so a check-then-write would let two devices both pass the cap or both delete below the minimum.
- **The sign-up wizard collects; only Consent writes.** Account → Name → Contacts → Consent. Steps 1–3 stage everything in memory and pass it on as Intent extras (contacts as `Parcelable`), so abandoning the wizard leaves nothing behind and never occupies an email address. The cost is that a taken address is not discovered until the final submit — deliberately, because checking earlier is the question Firebase's email-enumeration protection exists to refuse, and Login and forgot-password both already decline to answer it.
- **Consent is recorded, not just collected.** `finalizeSignUp` writes `accepted`, a server `acceptedAt` and `UserProfileRepository.CONSENT_VERSION` in the same atomic commit as the profile and contacts, so a profile can never exist without one. Bump the version whenever `label_signup_consent_statement` changes what is being agreed to.
- **Sign-up finalization is one atomic write.** `UserProfileRepository.finalizeSignUp` puts the profile and every contact into a single multi-path `updateChildren`, so sign-up cannot half-succeed. That is what makes the Retry safe: there is no partial state to reconcile. Registration is deliberately **two** calls — `AuthRepository.createAccount` then `finalizeSignUp` — and the `accountCreated` guard in `SignUpContactsViewModel` means a retry resumes at the write. When they were one call, a failed write left the account created and the only retry went back through account creation, which then failed as "email already in use", so that address could never finish registering.
- **`saveProfile` merges, never replaces.** It `updateChildren`s its three fields. A `setValue` on `users/{uid}` would delete every emergency contact under it.
- **Reads:** contacts come from a `callbackFlow` over a `ValueEventListener` and the list is never edited locally, so what is on screen is what is stored. The profile is a one-shot suspend read (`fetchProfile`); Profile falls back to the session's values when the record is missing or the read fails. Track reads no profile at all — its chip is the session's name. RTDB disk persistence is **not** enabled.
- **Failures are typed and mapped by code.** `ContactError` + `FirebaseDatabaseErrorMapper`, on `DatabaseError.getCode()`, never on a message. That code is only reachable through the SDK's completion-listener and `ValueEventListener` callbacks — an awaited `Task` fails with a bare `DatabaseException` carrying localised prose — which is why these repositories wrap the listener forms in `suspendCancellableCoroutine` instead of using `.await()`.
- **Contact phone numbers are never logged**, and neither are contact names. They are a third party's personal data, not the user's own. Nothing in `data/` logs at all.

## Firebase data contract

The band (or the mock sender) PUTs to the RTDB REST API, and Security Rules validate the payloads:

```
/devices/{deviceId}/latest                  → most recent alert
/devices/{deviceId}/history/{sequenceId}    → append-only history
```

`SCHEMA.md` is authoritative for the alert payload (v1.0). Its fields are:
- `schemaVersion`: the **string** `"1.0"`
- `deviceId`
- `type`: `PANIC | CHECKIN | LOW_BATTERY | TRACKING_UPDATE`
- `timestamp`: ISO 8601 UTC
- `location { lat, lng, accuracyMeters }`
- `battery { percent, isCharging }`
- `sequenceId`

`data/model/Alert.kt` mirrors it and `data/repository/AlertParser.kt` reads it — see "Alert data" below for how. Check the field names against the deployed Security Rules before relying on them.

## Alert data

The Alert tab reads `devices/{deviceId}` live. `SCHEMA.md` is the authority for the payload — if code, this file and `SCHEMA.md` disagree, `SCHEMA.md` wins and the disagreement gets reported rather than silently resolved.

**The locked decisions behind the tab:**
- **D1** — `PANIC`, `CHECKIN` and `LOW_BATTERY` are shown and colour-coded. `TRACKING_UPDATE` is hidden from the Alert list. An **unrecognised** type is *not* hidden: it is shown as "Unknown alert", so a type a future firmware adds still appears.
- **D2** — the latest-alert card is the newest **non-tracking** entry derived from history. It deliberately does **not** read `devices/{id}/latest`, because every payload overwrites that node, tracking updates included, so the card would read "Location update" while a panic sat one row below it. `observeLatestAlert` still exists, unsubscribed, for the Track tab.
- **D3** — read a window of 100 raw entries, drop tracking updates, show at most 50. The window is over *raw* entries, so a chatty band yields fewer than 50 displayable alerts. `AlertHistory` therefore carries `rawCount` and `malformedCount`, which is what lets the empty state say *which* kind of empty it is instead of implying the band is silent.
- **D4** — one device id, `DeviceConstants.DEFAULT_DEVICE_ID`, supplied through `RepositoryProvider`. No pairing yet, and ViewModels never see a device id.
- **D5** — `schemaVersion` is parsed as a String, so both `"1.0"` and a bare `1` work.

**Repository contract.** `AlertRepository` exposes `observeLatestAlert()` and `observeAlertHistory(limit)`, both `Flow<Result<T>>`. A failed read is `Result.failure(AlertError)` — never null, never an empty list — and the flow **stays open**, so a reconnect or a rules fix can make the next emission succeed. `AlertError` is mapped from `DatabaseError.getCode()` through `FirebaseDatabaseErrorMapper`, never from a message, and carries no message of its own.

**Ordering is `orderByKey().limitToLast(n)`, and needs no index.** History children are keyed by `sequenceId`, and RTDB sorts integer-like keys numerically and ahead of string keys, so this returns the newest entries. `orderByChild("sequenceId")` would need an `".indexOn": "sequenceId"` rule deployed; `orderByKey` needs none. The window is re-sorted by `sequenceId` in memory anyway, so a key that is *not* integer-like (a push id, a zero-padded number) degrades the window rather than the order within it.

**Parsing is a pure `Map` → `Alert` step in `AlertParser`,** not a method on the repository. A `DataSnapshot` cannot be built in a JVM test and the project declares no mocking library, so putting the logic behind one would have made all of it untestable. `sequenceId`, `type` and `timestamp` are required; a missing `schemaVersion`, `deviceId`, `battery` or `location` is salvaged rather than dropped, which is deliberately more forgiving than `SCHEMA.md`'s receiver-validation list — this is a *reader*, and refusing to show a panic because its battery node was missing would be the wrong failure. **A malformed history entry is skipped and counted, never fatal:** one bad row from a firmware bug must not hide every good row behind it. Only `latest` can fail as `AlertError.ParseFailure`.

**Locations are never logged.** An alert carries the wearer's coordinates; nothing in `data/` logs at all, and the Alert tab logs nothing either. Same rule as contact phone numbers — see "Contacts and profile data".

**Still in-memory:** `InMemoryStore`'s four seeded alerts, which only `InMemoryAlertRepository` reads, and only in tests.

## Mock sender

For demos without hardware, Phone A runs `MockSenderActivity` and Phone B runs the receiver screens. `MockSenderActivity` is intentionally *not* MVVM because it is a throwaway harness, so don't refactor it into the architecture. It lives in the **debug source set** (`app/src/debug/java/.../ui/mocksender/`, plus `app/src/debug/AndroidManifest.xml`, which gives it its own "GuardBand Mock Sender" launcher icon), so release builds contain neither the code nor the icon. OkHttp is `debugImplementation` for the same reason.

**`AlertSender` writes straight to the RTDB REST API,** as the band will: one `PUT` to `/devices/{id}/history/{sequenceId}.json`, then one to `/devices/{id}/latest.json`. There is no Cloud Function in the path — the Spark plan cannot host one, and the `devices` Security Rules already do the validation `ingestAlert` was going to do. (`SCHEMA.md`'s "Receiver validation requirements" still describes that Function; the payload shape it specifies is unchanged.)

- **History is written first, then `latest`.** The Alert tab derives everything from history, so a send that got as far as history is still visible there and only leaves the Track-facing `latest` stale. The reverse would show the alert nowhere in the Alert tab and leave a permanent hole in the history. The sequence id is consumed as soon as its history row lands, so a retry cannot overwrite a good row.
- **The sequence id is seeded from the server,** by reading the history keys with `?shallow=true` once per harness instance and taking the highest integer-parseable one, so a relaunched harness appends instead of overwriting the rows it wrote last run. `shallow` rather than an ordered `limitToLast(1)`: RTDB sorts integer-like keys *ahead* of string keys, so taking the last one would return a string key if the node ever picked one up from the Console — the one case this read exists to survive. A missing node reads as `null` (not an error) and starts at 1; a genuine read failure fails the send, because the `PUT` was about to fail the same way.
- **Walk mode** sends a `TRACKING_UPDATE` every 5 s around a ~165 m loop at Cebu City, which is what makes the Track marker move. The interval is deliberately far below FR-09's real 60 s so a demo shows movement in seconds and the band keeps reading as online. It stops in `onPause` — a walk still writing under a backgrounded harness would make a dead band look alive — and a failed step stops the walk rather than rewriting the same error every few seconds.
- **The database URL comes from `FirebaseProvider`,** not a constant, so the harness follows `google-services.json`. That is a Firebase import outside a repository, which the app's layering forbids — the harness sits outside that layering, and the alternative was a hardcoded URL, which is forbidden everywhere.
- The status line reports the history key each send landed under, so a demo can be checked against the Console.
- **Writes are unauthenticated.** They rely on `".write": true` under `devices/{id}` in the draft rules. If that is ever tightened to require auth, the harness needs an `?auth=` token.

The payload itself follows `SCHEMA.md` and satisfies the `devices` validation: all six required children, `type` one of the four enum names, `percent` 0–100, `isCharging` boolean, `sequenceId` numeric. Its timestamps carry milliseconds (`...SSS'Z'`) where `SCHEMA.md`'s example does not; both are valid ISO 8601 and `AlertFormatting` parses both. **The harness no longer duplicates anything.** `ui/mocksender/AlertType` used to be a second private copy of the enum, on the reasoning that de-duplicating it would mean editing `app/src/debug/`. That rationale did not hold — the debug source set imports from `main` freely — and a drifting enum or device id would have the harness writing alerts the app never reads. It now uses `data/model/AlertType` and `DeviceConstants.DEFAULT_DEVICE_ID` directly. The dependency only ever points one way: nothing in `main` knows the harness exists.

## UI workflow (agent vs. designer)
- Figma is the visual source of truth and is owned by Jul. The agent does not need the wireframes pasted in.
- The agent builds: layout skeletons with stable snake_case view IDs, binding to state, buttons and click handlers, state/event plumbing, strings in strings.xml, and accessibility labels. It uses theme tokens only.
- The agent does NOT: tune spacing, colors, typography, or icons beyond placeholders. Mark such spots with `<!-- DESIGN: Jul -->`.
- Each screen's KDoc lists its view IDs so the layout XML can be restyled without touching logic.

## Gotchas

- `README.md` is UTF-16 encoded. Plain `cat`/`grep` will show it with spaces between characters, so use `iconv -f UTF-16 -t UTF-8 README.md` to read it.
- Layouts use plain `findViewById` with snake_case IDs prefixed by screen (`et_login_email`, `btn_login`). There is no ViewBinding or Compose.
- Every Activity is locked to portrait in the manifest. New Activities must be added there.
- **An unescaped apostrophe in a string resource** fails `mergeDebugResources` with `Can not extract resource from ParsedResource`, naming neither the string nor the line. Write it `\'`.
- **A `--` inside an XML comment** fails `mergeDebugResources` with a `SAXParseException` — "The string “--” is not permitted within comments". Unlike the apostrophe it does name the file and line. Don’t rule off a comment with a row of dashes.
- **A duplicated `<string name="…">`** fails with "Found item String/x more than one time". Shared names like `cd_back` already exist — grep before adding one.
- JVM unit tests need **Robolectric** wherever `InputValidator` is reached, because it uses `android.util.Patterns`, and wherever a `FirebaseException` is constructed, because its constructor calls `android.text.TextUtils`.

## Working agreements

- Work happens in scoped passes: discovery (read-only) first, then decisions, then a scoped implementation, then build confirmation.
- Pass prompts contain explicit "do NOT touch" lists. Follow them, and put anything ambiguous in the report instead of improvising.
- The agent does not run builds, Gradle or the emulator unless the pass prompt explicitly asks for build confirmation (Prompt 08 did). The emulator is always the developer's.
- No state-changing git commands (commit, checkout, merge, reset, push, …) unless explicitly asked. Delete or move files with filesystem operations, not `git rm`/`git mv`.
- Never port MVP code into MVVM branches, or the reverse.
