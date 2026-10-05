# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

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
│   ├── repository/       AuthRepository, ContactRepository (suspend, Result<T>)
│   │                     AlertRepository (Flow<Result<T>>, live reads)
│   │                     UserProfileRepository (writes users/{uid})
│   │                     AuthError + FirebaseAuthErrorMapper (typed auth failures)
│   │                     GoogleProfileProvisioning (when Google sign-in may write users/{uid})
│   │                     Firebase* = live; InMemory* + InMemoryStore = test doubles
│   ├── DeviceConstants.kt      DEFAULT_DEVICE_ID = "guardband-001" (until pairing exists)
│   ├── FirebaseProvider.kt     the one FirebaseAuth / FirebaseDatabase instance
│   └── RepositoryProvider.kt   manual wiring; swap implementations here
├── ui/
│   ├── splash/         Splash → Home if signed in, else Login (2 s countdown)
│   ├── auth/           GoogleIdTokenProvider + GoogleIdTokenResult (the only androidx.credentials users)
│   ├── login/          email + password, and "Continue with Google"
│   ├── signup/         Name → Location → Contacts; Location → Contacts in complete-profile mode
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

**Auth and the user profile are on Firebase** (see "Firebase" below). **Contacts and alerts are still in-memory:** `InMemoryStore` is a process-local store with two seeded contacts, four seeded alerts for `guardband-001` and a simulated 1.2 s latency, and it loses everything when the process dies. Contacts move to the RTDB next; alerts follow when the `devices/{deviceId}` reader lands.

`InMemoryAuthRepository` is still in the tree but is **not** wired into `RepositoryProvider` — it is the unit-test double for the Firebase implementation and returns the same `AuthError`s, so the two stay interchangeable.

`ui/dashboard/` and `res/layout/activity_dashboard.xml` are gone, replaced by `ui/home/`. Two leftovers from them are still waiting to be removed: the `label_dashboard_*`/`label_your_contacts` strings and the CardView dependency, which now has no user at all.

Conventions (copy `ui/login/*` as the reference):
- **State:** one `XUiState` data class per screen, held in a `MutableStateFlow` and exposed as a `StateFlow`. Update it only with `_uiState.update { it.copy(...) }`.
- **Events:** one-shot events (navigate, toast) go in a sealed `XEvent` sent through `Channel(Channel.BUFFERED)` and exposed with `receiveAsFlow()`. Never encode navigation as state. A screen with no state of its own (Splash, Loading, Sign-up Name/Location) has events only.
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
- **The session is `FirebaseAuth.currentUser`,** which the SDK persists, so it survives process death. `currentUser()` is synchronous and safe in a ViewModel's constructor, but it can only fill `id`, `name` (the Firebase display name, set at sign-up) and `email` — **`location` is always empty**, because it lives at `users/{uid}` and needs an async read. Profile and Track therefore show "Location not set" until that read exists.
- **Failures are typed.** Repositories return `Result.failure(AuthError)`, and `FirebaseAuthErrorMapper` maps `FirebaseAuthException.errorCode`, never the exception message — messages are localised and change between SDK versions. `AuthError` carries no message and suppresses its stack trace, so Firebase text cannot reach a log or a Toast.
- **Login must not distinguish a wrong password from an unknown account,** and forgot-password must confirm identically for an unregistered email. With email-enumeration protection on, Firebase returns the same code for both anyway; the UI must not undo that.
- **Password reset is Firebase's hosted flow.** `sendPasswordResetEmail` emails a link and the user finishes in a browser. The app never sees a reset code, so there is no verify-code or set-new-password screen, and nothing like `otpPlain` or `pendingPassword` is ever written.
- **No PII in logs.** Never log a uid, an email, a token or Firebase error text.
- **Never overwrite `app/google-services.json` from another branch,** and don't touch it, `.firebaserc` or any signing config as a side effect of other work.
- **The `users/{uid}` Security Rules are not deployed.** The proposal in the Prompt 08 report still has to be merged with the existing `devices/` rules in the Console, by hand.

## Google sign-in

**Credential Manager only.** The legacy `GoogleSignIn` / `play-services-auth` sign-in API is never used. `play-services-auth` is on the classpath solely because `credentials-play-services-auth` needs it as its backend provider; nothing imports it. Do not copy the Google sign-in code, the `default_web_client_id` placeholder or `play-services-auth` from `mvp-checkpoint-local-backup` — that branch uses the legacy API.

**The token flow has three layers, and each one knows the least it can:**

1. **Activity layer.** `ui/auth/GoogleIdTokenProvider` is the *only* class in the app that touches `androidx.credentials`. Credential Manager renders a sheet, so it needs an Activity context — which is exactly why a ViewModel must not own it. It returns a typed `GoogleIdTokenResult`: `Success(idToken)`, `Cancelled`, `NoGoogleAccount`, `Unavailable`, `Network` or `Unknown`. It never swallows an exception into null, and it rethrows `CancellationException`.
2. **ViewModel.** Receives nothing but the token string (`onGoogleIdToken`) or a `GoogleIdTokenResult` (`onGoogleError`). No Context, no Credential Manager type. `Cancelled` is **silent** — the user dismissed the sheet on purpose and does not need to be told.
3. **Repository.** `AuthRepository.signInWithGoogle(idToken)` exchanges it with Firebase via `GoogleAuthProvider.getCredential(idToken, null)` + `signInWithCredential`, and returns a `GoogleSignInOutcome(user, isNewUser)`.

**`default_web_client_id` is generated**, not written. The Google Services plugin derives it from the web (`client_type: 3`) `oauth_client` entry in `google-services.json` and emits it into `app/build/generated/res/processDebugGoogleServices/values/values.xml`. Never hardcode a client ID, and never commit one to a source file.

**Every developer must register their own debug SHA-1** in the Firebase Console, then download the regenerated `google-services.json`. Google sign-in fails on an unregistered machine even though the app builds fine — the signing certificate is part of what Google checks. The release keystore's SHA-1 has to be added before any signed build. (`google-services.json` currently holds one Android `oauth_client` entry with one certificate hash.)

**`setFilterByAuthorizedAccounts(false)`** on `GetGoogleIdOption`, so the chooser offers every account on the device. With it true a first-time user is shown an empty sheet.

**Routing:** `isNewUser` decides. A returning user goes to Home. A first-time user goes through the remaining sign-up steps (location, emergency contact) in **complete-profile mode** — `SignUpLocationActivity` and `SignUpContactsActivity` carry `EXTRA_COMPLETE_PROFILE`, the credential fields are hidden, nothing is validated against them, and `saveProfile` finishes `users/{uid}` instead of `register` creating an account. In that mode the uid and email come from the **live session**, never from the Intent extras, so a stale extra cannot write to the wrong record.

**The profile write is conditional.** `saveProfile` replaces the whole `users/{uid}` record, so writing it on every Google sign-in would wipe a location the user had already set. `GoogleProfileProvisioning` writes only when the account was just created or the record is confirmed absent; a *failed* existence read writes nothing, because it cannot tell "absent" from "unreachable". A failed write does **not** fail the sign-in — the session already exists by then.

**Error wording stays neutral.** `ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL` maps to its own `AuthError.AccountExistsWithDifferentCredential`, shown as "This email uses a different sign-in method." It must not name the other provider: saying "that address uses a password" confirms the account exists, which is the whole thing the shared invalid-credentials message exists to prevent.

**Log out clears the credential state.** `SettingsFragment` calls `GoogleIdTokenProvider.clearCredentialState()` before navigating, so the chooser reappears instead of silently reusing the last account. It is bounded by a 2 s timeout and its failures are ignored — the user is already signed out of Firebase, so nothing may keep them on that screen. `HomeViewModel`'s failed auth gate does **not** clear it: that is a dead session, not a user logging out.

**Never log a token**, an account name or an email, here or anywhere else.

**Not covered by tests:** the Firebase exchange inside `signInWithGoogle` and `GoogleIdTokenProvider` itself. Both need a real `FirebaseAuth` / Credential Manager, and the project declares no mocking library. The testable logic was extracted instead — `GoogleProfileProvisioning` for the write rule, the ViewModels for routing and wording.

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

`data/model/Alert.kt` mirrors it. Check the field names against the deployed Security Rules before relying on them.

## Mock sender

For demos without hardware, Phone A runs `MockSenderActivity`, which sends OkHttp PUTs to the RTDB endpoints, and Phone B runs the receiver screens. `MockSenderActivity` is intentionally *not* MVVM because it is a throwaway harness, so don't refactor it into the architecture. It lives in the **debug source set** (`app/src/debug/java/.../ui/mocksender/`, plus `app/src/debug/AndroidManifest.xml`, which gives it its own "GuardBand Mock Sender" launcher icon), so release builds contain neither the code nor the icon. OkHttp is `debugImplementation` for the same reason. `INGEST_ALERT_URL` in `AlertSender.kt` is still a placeholder, and it POSTs to a Cloud Function, which doesn't match the RTDB-REST design above. The payload follows `SCHEMA.md`.

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
- JVM unit tests need **Robolectric** wherever `InputValidator` is reached, because it uses `android.util.Patterns`, and wherever a `FirebaseException` is constructed, because its constructor calls `android.text.TextUtils`.

## Working agreements

- Work happens in scoped passes: discovery (read-only) first, then decisions, then a scoped implementation, then build confirmation.
- Pass prompts contain explicit "do NOT touch" lists. Follow them, and put anything ambiguous in the report instead of improvising.
- The agent does not run builds, Gradle or the emulator unless the pass prompt explicitly asks for build confirmation (Prompt 08 did). The emulator is always the developer's.
- No state-changing git commands (commit, checkout, merge, reset, push, …) unless explicitly asked. Delete or move files with filesystem operations, not `git rm`/`git mv`.
- Never port MVP code into MVVM branches, or the reverse.
