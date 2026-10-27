# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

> Picking this up cold? Read [docs/handoff.md](docs/handoff.md) first — current state, open items and the traps that have already cost time.

GuardBand is an Android (Kotlin) companion app for an IoT wrist-worn safety band (ESP32 + Air780E 4G LTE + NEO-6M GPS). The band itself sends SMS to emergency contacts and writes alerts directly to Firebase Realtime Database over REST; the app reads that data so guardians can watch in real time. The firmware does not exist yet. Until it does, a phone acts as the band (see "Mock sender" below). This is an academic project; the requirements are FR-01..FR-11 in README.md.

## Build & test

Single-module Gradle project (`:app`). AGP 8.3.2, Kotlin 1.9.24, compileSdk/targetSdk 34, minSdk 24, Java/JVM target 1.8. Versions live in `gradle/libs.versions.toml`; only CardView is declared inline in `app/build.gradle.kts`.

```bash
./gradlew assembleDebug                      # build debug APK
./gradlew installDebug                       # install on a connected device/emulator
./gradlew testDebugUnitTest                  # JVM unit tests
./gradlew testDebugUnitTest --tests "com.example.guardband.ExampleUnitTest"   # single test class
./gradlew connectedDebugAndroidTest          # instrumented tests (needs a device)
./gradlew lint
```

On Windows, use `gradlew.bat` (or `./gradlew` from Git Bash), with `JAVA_HOME` pointing at Android Studio's bundled JBR. There are 880 JVM unit tests (2026-10-27), under `app/src/test` (and the debug-only Mock Sender's in `app/src/testDebug`); the test doubles are in `app/src/test/.../testing/`. Instrumented tests are still only the template. `./gradlew lint` passes with no errors (warnings only).

`app/google-services.json` is required for the `com.google.gms.google-services` plugin. It points at the Firebase project `guardband-aae65` (RTDB only, `asia-southeast1`, Spark plan, so there are no Cloud Functions).

## Branches matter

Each branch holds a deliberately different architecture. Check which one you are on before you change anything:

| Branch | Purpose |
|---|---|
| `main` | Kept on the **same commit** as `mvvm-main`: after `mvvm-main` is pushed, `main` is fast-forwarded to it (`git push origin mvvm-main:main`), never merged with `--no-ff`. Teammates push to `mvvm-main` only (since 2026-10-27). |
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
│   ├── model/            User, EmergencyContact, Alert (+ AlertType), CheckIn, ConsentRecord, ProfileState,
│   │                     SignInProvider, GoogleSignInOutcome, Place (plain data classes; no passwords)
│   ├── repository/       AuthRepository (suspend, Result<T>)
│   │                     ContactRepository (observe = Flow<Result<T>>; CRUD = suspend)
│   │                     AlertRepository (Flow<Result<T>>, live reads)
│   │                     UserProfileRepository (reads/writes users/{uid}, incl. the atomic finalize)
│   │                     AuthError + FirebaseAuthErrorMapper (typed auth failures)
│   │                     ContactError + AlertError + FirebaseDatabaseErrorMapper (typed RTDB failures)
│   │                     AlertParser (wire map → Alert; pure, so it is unit-testable)
│   │                     ContactFields (the one on-disk shape of a contact)
│   │                     ProfileProvisioning (when a sign-in may write users/{uid}; both paths)
│   │                     PlaceRepository → GeocoderPlaceRepository (alert coordinates → place names)
│   │                     Firebase* = live; InMemory* + InMemoryStore = test doubles
│   ├── DeviceConstants.kt      DEFAULT_DEVICE_ID = "guardband-001" (until pairing exists)
│   ├── FirebaseProvider.kt     the one FirebaseAuth / FirebaseDatabase instance
│   └── RepositoryProvider.kt   manual wiring; swap implementations here
├── ui/
│   ├── splash/         Splash → Home if signed in, else Login (2 s countdown)
│   ├── auth/           GoogleIdTokenProvider + GoogleIdTokenResult (the only androidx.credentials users)
│   ├── avatar/         AvatarPhoto (pure: which Google photo, if any) + AvatarImage (the Coil binder);
│   │                 the Device tab's avatar, photo or initials (Track's avatar was removed 2026-10-19)
│   ├── login/          email + password, and "Continue with Google"
│   ├── signup/         Account → Name → Contacts → Consent; Contacts → Consent in complete-profile mode;
│   │                 then Welcome, once per account (its only route is Consent's success)
│   ├── consent/        ConsentRequiredActivity: the consent step the Home gate sends an account with
│   │                 no consent record to (sign-up's layout, relabelled); ConsentGate (pure) decides
│   ├── forgot/         one screen: email → "check your email" + Resend (60 s cooldown)
│   ├── reset/          New Credentials, opened by the App Link in the reset email;
│   │                 EmailActionLink routes the other email links (verifyEmail) away first
│   ├── verify/         VerifyEmailActivity: applies the code from the verification email;
│   │                 VerifyRequiredActivity: the gate an unverified password account meets instead of Home
│   │                 ResetLinkParser (pure; the trust boundary) + ViewModel
│   ├── loading/        1.8 s transition → Home (Back blocked)
│   ├── home/           HomeActivity: bottom-nav host + second auth gate and the account gate (HomeViewModel)
│   ├── track/          Track tab (default): gear + bell (as on Contacts/Profile), osmdroid map, round re-centre (target) button,
│   │                 layers button (MapLayer: Auto / Street / Satellite / Outdoor / Topo), Check-in pill, and the recent-alerts bottom sheet (RecentAlertsPanel; See all → Alert history)
│   │                 BandStatus + OsmdroidConfig (pure helpers; see "Track and map")
│   │                 BandMarkerFace (pure: photo or initials) + BandMarkerIcon (draws the band's marker)
│   ├── contacts/       Contacts tab: search, relationship filter chips (in memory, ContactsUiState.filteredContacts),
│   │                 add/edit dialog, the Emergency star, delete (trash icon or long-press the row)
│   ├── alert/          AlertHistoryFragment (pushed from Track's See all): latest status + incident history;
│   │                 AlertViewModel is host-scoped and also feeds Track's recent-alerts panel
│   ├── device/         Device tab (the Profile tab until 2026-10-27): account header, band card,
│   │                 connected account + contacts count, pairing placeholder (see "Device tab")
│   ├── settings/       pushed from Track's gear; holds Log Out and the Account information row.
│   │                 A chevron means another screen of the app; Device information opens the Device tab
│   ├── display/        DisplayActivity, opened from Settings: the theme (ThemeChoice, DisplayPrefs)
│   ├── about/          AboutActivity, opened from Settings: version and the Privacy Notice row
│   │                 (a web page, so no chevron)
│   ├── account/        AccountActivity (Account information), opened from Settings;
│   │                 its Password section (PasswordOption) opens ChangePasswordActivity, or shows a
│   │                 Google account a "Managed by Google" empty state; Delete account sits in its
│   │                 danger zone (DeleteAccountViewModel)
│   ├── changelog/      ChangelogActivity, opened from Settings; Changelog.entries is the progress log
│   └── notifications/  pushed from Track's bell: the inbox, the band's non-tracking alerts by day with place
│                     names (NotificationsViewModel, Fragment-scoped, its own history subscription). Also the opt-in alert watch:
│                     AlertNotificationsActivity (the switch, opened from Settings),
│                     AlertWatchService (foreground service) + AlertWatch (start/stop), AlertNotifier
│                     (channels, wording), AlertNotificationDecider (pure) + AlertNotificationPrefs
└── utils/
    ├── InputValidator.kt       shared input predicates (no messages)
    ├── PasswordCriteria.kt     which password rules the typed pair satisfies
    └── HostedPages.kt          URLs of the pages served from the Hosting domain
```

**Auth, the user profile, contacts and alerts are all on Firebase** (see "Firebase", "Contacts and profile data" and "Alert data" below). Nothing in the app reads `InMemoryStore` any more; it is the unit tests' backing store.

`InMemoryAuthRepository`, `InMemoryContactRepository` and `InMemoryAlertRepository` are still in the tree but are **not** wired into `RepositoryProvider` — they are the unit-test doubles for the Firebase implementations and return the same `AuthError`s / `ContactError`s / `AlertError`s, so each pair stays interchangeable. `InMemoryStore.replaceContacts` exists purely as a test seam, because `addContact` assigns its own id.

`ui/dashboard/` and `res/layout/activity_dashboard.xml` are gone, replaced by `ui/home/`. Their leftover strings are gone too. (The CardView dependency is in use again: the 2026-10-13 redesign wraps its pill buttons in CardViews.)

Conventions (copy `ui/login/*` as the reference):
- **State:** one `XUiState` data class per screen, held in a `MutableStateFlow` and exposed as a `StateFlow`. Update it only with `_uiState.update { it.copy(...) }`.
- **Events:** one-shot events (navigate, toast) go in a sealed `XEvent` sent through `Channel(Channel.BUFFERED)` and exposed with `receiveAsFlow()`. Never encode navigation as state. A screen with no state of its own (Splash, Loading, Sign-up Name) has events only.
- **ViewModels** take repositories through the constructor and hold no `Context` or View. They expose `companion object { val Factory = viewModelFactory { initializer { XViewModel(RepositoryProvider.…) } } }`, and Activities use `by viewModels { XViewModel.Factory }`; plain `by viewModels()` is fine when there are no dependencies. User-facing messages are `MSG_*` constants in the ViewModel — including the wording for every `AuthError`, which each screen maps itself in a private `messageFor(error)`. Those messages stay in Kotlin rather than `strings.xml` precisely because a ViewModel holds no `Context`.
- **Activities** bind views with `findViewById`, forward raw input and clicks, render state, and execute events (Toast, `startActivity`, `finish`). They collect each flow in its own `lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`. Building `Intent`s stays in the Activity.
- **Wizard data** moves between screens as Intent extras. The Activity passes the extras into the ViewModel's click handler, and the navigation event carries the data back.
- **Exception:** `ui/reset/ResetSuccessActivity`, `ui/signup/WelcomeActivity` and `ui/changelog/ChangelogActivity` have no ViewModel. Each is a static screen whose only action is navigation. (`ForgotSuccessActivity` was another, and it is gone - see "Forgot password". `NotificationsFragment` was another until the inbox got real data.)

### Home host (Fragments)

- **No Navigation Component.** `HomeActivity` drives a `BottomNavigationView` (`menu_home_bottom_nav.xml`) with a manual `FragmentManager`: each Fragment is added once by tag, then shown or hidden, so tab state survives switches. Don't add `replace()`, a back stack, or a nav graph. The tags, and how a saved one is read back, are `HomeDestinations`. **The one exception is the Incident view** (`HomeDestinations.INCIDENT`, 2026-10-27): `HomeActivity.showIncident` adds a new `IncidentFragment` (panic number in its arguments) each time one opens, and `show()` **removes** it whenever anything else is shown, so its live RTDB listener ends with it. Back from it returns to the screen that opened it (`incidentReturnTag`: Track or Alert history), not to the last tab. Don't make it added-once-and-hidden: its listener would then run until Log Out.
- **Tabs (three, icon-only):** Track (default), Contacts, Device (Profile until 2026-10-27; a saved `home_profile` restores to Track). **Pushed screens:** Settings (gear) and Notifications (bell), opened from any tab's top bar, and **Alert history**, opened from "See all" on Track's recent-alerts panel. While one is showing, the bottom bar is hidden, and Back (or the screen's back arrow) returns to the last tab and brings the bar back. Back on a tab exits. There is no Alert tab (removed 2026-10-19): Track shows the newest alerts, and the full history is the pushed screen. A restored tag the host no longer has (the old `home_alert`) falls back to Track.
- **Auth gate (two checks):** `SplashViewModel` routes on `isLoggedIn()`, and `HomeViewModel` re-checks in `init` and emits `NavigateToLogin`. The second check used to be the load-bearing one, because the in-memory session died with the process; now that the session is `FirebaseAuth.currentUser` it rarely fires, and it is kept for a restore after the account was signed out or disabled elsewhere. Settings, Notifications and Alert history are inside the host, so they're covered too. Since 2026-10-14 the host also keeps the tabs covered until the account's record has been read (see "Consent gate").
- **Fragments follow the Activity conventions,** with these differences:
  - Use `Fragment(R.layout.x)`, and bind views in `onViewCreated` with `view.findViewById`.
  - Collect flows with `viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(STARTED) { … } }`.
  - Get the screen's own ViewModel with `by viewModels { XViewModel.Factory }`. There are two exceptions, both host-scoped with `by activityViewModels { … }` so there is one instance and one listener: `AlertViewModel` (Track's panel, Alert history and the Device tab) and, since 2026-10-27, `ContactsViewModel` (the Contacts tab and the Device tab's count). **Don't turn either back into `by viewModels`**: the Device tab would then need a second listener.
  - Host navigation goes through the shared `HomeViewModel` via `by activityViewModels { HomeViewModel.Factory }` (e.g. Track's gear calls `homeViewModel.onSettingsClicked()`). The Activity executes the resulting `HomeEvent`; Fragments never cast `requireActivity()` to `HomeActivity`.
  - A pushed screen's back arrow calls `requireActivity().onBackPressedDispatcher.onBackPressed()`.
- **Lists** use `ListAdapter` + `DiffUtil` with `findViewById` in the ViewHolder (`ContactAdapter`, `AlertHistoryAdapter`).

Declared dependencies: AppCompat, Material, ConstraintLayout, CardView (the redesigned pill buttons), activity-ktx, fragment-ktx 1.8.2, RecyclerView 1.3.2, lifecycle-viewmodel-ktx and lifecycle-runtime-ktx 2.8.7, kotlinx-coroutines-android 1.9.0, kotlinx-coroutines-play-services 1.9.0, and the Firebase BoM 32.8.1 with **Auth** and Database (Analytics was dropped: no code called it, and it collected an advertising id on its own). The Track map adds **osmdroid-android 6.1.20** (see "Track and map"). Tests add kotlinx-coroutines-test and Robolectric. The Profile avatar adds **Coil 2.7.0** (pinned; the 2.x line works on a plain `ImageView`). OkHttp is declared `debugImplementation` for the mock sender, but release builds still ship it, transitively, as Coil's network fetcher. Everything except CardView now lives in `gradle/libs.versions.toml`. Google sign-in adds **androidx.credentials 1.3.0**, **credentials-play-services-auth 1.3.0** and **googleid 1.1.1** — pinned there because newer versions need compileSdk 35 (see "Google sign-in" below).

## Firebase

Project `guardband-aae65` (RTDB only, `asia-southeast1`, Spark plan — so no Cloud Functions). Auth is **email + password** and **Google** (see "Google sign-in" below).

- **Firebase calls live only in repository classes.** No Firebase import belongs in an Activity, Fragment or ViewModel. `FirebaseProvider` owns the single `FirebaseAuth` and `FirebaseDatabase`; `FirebaseDatabase.getInstance()` takes the URL from `google-services.json`, so never hardcode it.
- **The session is `FirebaseAuth.currentUser`,** which the SDK persists, so it survives process death. `currentUser()` is synchronous and safe in a ViewModel's constructor, but it fills `id`, `name` (the Firebase display name, set at sign-up) and `email`, which is the whole of `User`. The Device tab's header still reads `users/{uid}` because the stored record is authoritative, but the screen is never blank while it waits.
- **Failures are typed.** Repositories return `Result.failure(AuthError)`, and `FirebaseAuthErrorMapper` maps `FirebaseAuthException.errorCode`, never the exception message — messages are localised and change between SDK versions. `AuthError` carries no message and suppresses its stack trace, so Firebase text cannot reach a log or a Toast.
- **Login must not distinguish a wrong password from an unknown account,** and forgot-password must confirm identically for an unregistered email. With email-enumeration protection on, Firebase returns the same code for both anyway; the UI must not undo that.
- **Password reset happens in the app, not in a browser.** `sendPasswordResetEmail` still sends Firebase's own email, but the link in it is an Android App Link onto `ResetPasswordActivity`, so the new password is set inside GuardBand. Nothing like `otpPlain` or `pendingPassword` is ever written, and there is still **no typed verification code** — see "Forgot password" below.
- **No PII in logs.** Never log a uid, an email, a token or Firebase error text.
- **Never overwrite `app/google-services.json` from another branch,** and don't touch it, `.firebaserc` or any signing config as a side effect of other work.
- **The Security Rules are deployed (2026-10-11), and `RTDBS-RULES.md` is the source of truth.** That file with every `"//"` key stripped *is* the live ruleset; the Console rejects the `"//"` keys. Change the file first, then paste the whole stripped file and publish, so the two cannot drift. **Since 2026-10-19 the file is ahead of the Console:** it proposes an append-only `devices/.../history` (Option A+), pending Jul's decision and not deployed; its STATUS paragraph says so, and [docs/database-rules.md](docs/database-rules.md) has the path inventory, the `devices/` options, the Rules Playground tests (the only test suite rules get; the project has no emulator suite), the rollout and the live text to roll back to. Its `devices/` block is deliberately world-readable and world-writable; closing it needs device pairing. **`history` needs its own `.read`**, not just `$sequenceId`'s: a child's read never grants the parent, and the alert screens and the mock sender both read the list. Before the deploy that line was missing and both got 401.

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

**Log out clears the credential state.** `ui/auth/LoginNavigation.clearCredentialsThenGoToLogin` calls `GoogleIdTokenProvider.clearCredentialState()` before navigating (Log Out in Settings and a finished Delete account in Account information both go through it), so the chooser reappears instead of silently reusing the last account. It is bounded by a 2 s timeout and its failures are ignored — the user is already signed out of Firebase, so nothing may keep them on that screen. It also empties Coil's memory and disk caches (2026-10-27), so the signed-out account's Google photo does not stay on the phone; Coil loads nothing else, and osmdroid's map tiles are a separate cache it leaves alone. `HomeViewModel`'s failed auth gate does **not** clear either: that is a dead session, not a user logging out.

**Never log a token**, an account name or an email, here or anywhere else.

**Not covered by tests:** the Firebase exchange inside `signInWithGoogle` and `GoogleIdTokenProvider` itself. Both need a real `FirebaseAuth` / Credential Manager, and the project declares no mocking library. The testable logic was extracted instead — `ProfileProvisioning` for the write rule, the ViewModels for routing and wording.

## Forgot password

> The whole flow, the one-place host/path constant, the hosting files and the
> manual steps: [docs/forgot-password.md](docs/forgot-password.md).

**No typed-code reset without a server. Ever.** A six-digit code has to be
generated somewhere trusted, kept somewhere the user can neither read nor
write, compared server-side, and exchanged for a password change through the
Admin SDK. The Spark plan has no Cloud Function to do any of it. A client-only
version - the code minted in the app, or held in RTDB - puts the thing that
authorises a password change exactly where an attacker already is. **Do not
build the wireframe's five-box "Code Verification" screen**, and treat the
world-readable `password_resets` node still sitting in the `RTDBS-RULES.md`
draft as the leftover of an earlier attempt at this, not as a design.

**What happens instead.** Firebase sends its own reset email, which proves the
user controls the inbox. The link is an **Android App Link** onto
`ui/reset/ResetPasswordActivity`, not Firebase's hosted page, so the password
is set in the app with `verifyPasswordResetCode` then `confirmPasswordReset`.
Neither call needs a signed-in user, and if one happens to be signed in the
flow neither reads nor disturbs that session.

**The link is Firebase's own default handler**, `https://guardband-aae65.firebaseapp.com/__/auth/action`,
because **the project cannot change the Console's action URL**: Firebase
refuses it with `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED`, from the Console and from
the API alike. So the app claims the link Firebase already sends rather than
moving it. `firebaseapp.com` serves the same Hosting site as `web.app`, so the
one deployed `assetlinks.json` verifies both. Without the app, the link opens
Firebase's own reset page, which completes the reset in the browser.

**The host and path are declared once**, as `resetLinkHost` / `resetLinkPath`
in `app/build.gradle.kts`, and pushed out as a `manifestPlaceholders` pair for
the intent filter and a `buildConfigField` pair for the parser. Three files
have to agree on them, the third being `hosting/public/.well-known/assetlinks.json`,
which is served rather than built.

**Never host a page of your own under `/__/`.** Firebase serves that prefix
itself, so a file deployed there is shadowed and never reached. Claiming
Firebase's own `/__/auth/action` is the one exception, and it is **an exact
`android:path`, never `pathPrefix`**: `/__/auth/handler`, the OAuth redirect
handler, is on the same host. Every Firebase email action (`verifyEmail`,
`recoverEmail`, ...) arrives at that one path, so the parser's
`mode=resetPassword` check is what keeps the others out. Since 2026-10-14
`ui/reset/EmailActionLink.routeOf` runs first in `ResetPasswordActivity` and
hands `verifyEmail` (and any mode the app does not handle) to
`VerifyEmailActivity`; every reset link, and every malformed one, still takes
exactly the old path. See "Email verification".

**`ResetPasswordActivity` is the one `exported="true"` Activity in the app**,
because an external click has to reach it. `ui/reset/ResetLinkParser` is
therefore the trust boundary: it re-checks scheme, host, path, `mode` and a
non-blank `oobCode`, because **an intent filter is a router, not a validator**.
It takes a `String` and uses `java.net.URI` rather than `android.net.Uri` so it
is a plain JVM test, takes the **first** occurrence of a repeated query
parameter so an appended `mode=` cannot override Firebase's, and returns a bare
null rather than a reason - the URL contains the code, so no reject path may
describe what it saw.

**The `oobCode` lives only in `ResetPasswordViewModel`** - never an Intent
extra, saved instance state, a log or analytics. The cost is that it does not
survive process death, and the user re-opens the email link.

**The request screen is one screen with two faces.** `ForgotSuccessActivity` is
gone: resending needs the address, so the confirmation became
`ForgotRequestUiState.sent` on the screen that still has it. Resend is on a 60 s
cooldown, recomputed from an injected `Clock` on each tick of an injected
`Flow<Unit>` - the same pattern as `TrackViewModel`'s staleness ticker, and for
the same reason, so a test never waits and is never left holding an endless
`delay`.

**The enumeration property survives, and must keep surviving.**
`AuthError.NoSuchUser` and `AuthError.InvalidEmail` are reported as success.
The sent wording, the cooldown and the resend confirmation are identical for a
registered and an unregistered address, and a test compares the two states
field for field.

**Password rules are `PasswordCriteria`'s, which are `InputValidator`'s.** The
criteria list the user reads and the gate on Submit are the same value, so the
screen can never refuse a password it just ticked green.
`ui/auth/PasswordCriteriaList` binds `view_password_criteria.xml` and does the
tinting - an `<include>` plus a binder object, the same shape as
`SignUpStepHeader`. Sign-up and a future change-password screen are the other
candidates for it; adding a rule means a predicate in `InputValidator`, a field
on `PasswordCriteria` and a row in that layout, never a length comparison
written out again.

**Hosting is deployed** (first 2026-10-11; last 2026-10-27, notice 1.3) with **four**
debug SHA-256s in `assetlinks.json`: James's and three teammates'. Any other
developer's debug SHA-256, and a release one, still has to be added and
Hosting redeployed. The doc's §1 is the checklist.

## Email verification

> The flow, the link routing, the traps and the manual pass:
> [docs/email-verification.md](docs/email-verification.md).

- **Sent once, best effort,** by `SignUpConsentViewModel` right after
  `createAccount` succeeds, in its own coroutine so it can neither delay nor
  fail sign-up. Not on a Retry, never for a Google account. Plain
  `sendEmailVerification()` with **no `ActionCodeSettings`**, like the reset.
- **The link reuses the reset App Link.** No manifest change: `ResetPasswordActivity`
  routes `mode=verifyEmail` to the non-exported `ui/verify/VerifyEmailActivity`,
  which re-checks the link and calls `AuthRepository.applyEmailVerification`.
  That checks the code's kind first (`checkActionCode` must say
  `VERIFY_EMAIL`), so it never applies another action. Other modes get "isn't
  supported", never "invalid reset link", and never a crash.
- **Status lives in Account information**: Verified / Not verified, from
  `EmailVerification.of(providers, isEmailVerified)`. Google accounts are
  always Verified and never see Resend. The user is **reloaded every time the
  screen comes to the front**, so a link opened elsewhere shows up without a
  sign-out.
- **Resend has a 60 s cooldown** on the forgot-password pattern (injected
  `Clock` + ticker). `AuthError.TooManyRequests` is worded kindly and starts
  the cooldown; offline does not.
- **The app is locked until it is verified (2026-10-14).** `HomeViewModel`'s
  gate sends an unverified **password** account to `ui/verify/VerifyRequiredActivity`
  (task cleared) instead of the tabs - `EmailVerification.requiredBeforeUse`.
  Google accounts, and accounts with no known provider, are never gated. The
  gate screen reloads on every `onStart` and lets them straight through once
  verified; it also offers "I've verified my email", Resend (same cooldown),
  Log Out, and "Wrong email? Delete this account" (Account information), so a
  mistyped address is never a dead end. Every route in - Splash, login, Google,
  sign-up via Welcome - ends at Home, which is why the gate lives there.
- **It is a product gate, not a security boundary.** The Security Rules do not
  check `email_verified`, and must not on `users/$uid`: sign-up's
  `finalizeSignUp` writes the profile **before** the user can have verified,
  so such a rule would fail every sign-up. `devices/` is open to everyone
  anyway until pairing.

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
- **Minimum 1, no upper cap** (cap was 3 until 2026-10-13, then raised to 10, then removed). `InputValidator.MIN_CONTACTS` / `MAX_CONTACTS` (now `Int.MAX_VALUE`; kept only so existing callers compile). Enforced in the **repository**, not just the dialog: deleting the last contact fails with `ContactError.MinimumContacts`; there is no add cap. Editing is always allowed — it is the way out of a wrong number. Security Rules **cannot** enforce counts, because RTDB rules cannot count children, so the repository is the last line. Sign-up requires the same minimum (`SignUpContactsViewModel.MIN_AT_SIGNUP` *is* `MIN_CONTACTS`).
- **No "other" (non-emergency) contacts yet, deliberately.** Everything under `emergency_contacts` is someone the band texts, and the Privacy Notice gives that as *the entire reason* the numbers are collected. Storing contacts the band never texts needs a stated purpose first, then the notice, `CONSENT_VERSION` and the notice's version changed in the same commit. The `emergency` flag (2026-10-23) does not change that: it only sorts the contact to the top and badges it, and notice 1.2 says exactly that. If it ever decides who the band texts, the notice changes with it.
- **Every mutation is a transaction** on the `emergency_contacts` node, not a write to one child. The bounds are counts over siblings, so a check-then-write would let two devices both pass the cap or both delete below the minimum.
- **The sign-up wizard collects; only Consent writes.** Account → Name → Contacts → Consent. Steps 1–3 stage everything in memory and pass it on as Intent extras (contacts as `Parcelable`), so abandoning the wizard leaves nothing behind and never occupies an email address. The cost is that a taken address is not discovered until the final submit — deliberately, because checking earlier is the question Firebase's email-enumeration protection exists to refuse, and Login and forgot-password both already decline to answer it.
- **Consent is recorded, not just collected.** `finalizeSignUp` writes `accepted`, a server `acceptedAt` and `UserProfileRepository.CONSENT_VERSION` in the same atomic commit as the profile and contacts, so a profile can never exist without one. Bump the version whenever `label_signup_consent_statement` changes what is being agreed to.
- **Sign-up finalization is one atomic write.** `UserProfileRepository.finalizeSignUp` puts the profile and every contact into a single multi-path `updateChildren`, so sign-up cannot half-succeed. That is what makes the Retry safe: there is no partial state to reconcile. Registration is deliberately **two** calls — `AuthRepository.createAccount` then `finalizeSignUp` — and the `accountCreated` guard in `SignUpContactsViewModel` means a retry resumes at the write. When they were one call, a failed write left the account created and the only retry went back through account creation, which then failed as "email already in use", so that address could never finish registering.
- **`saveProfile` merges, never replaces.** It `updateChildren`s its three fields. A `setValue` on `users/{uid}` would delete every emergency contact under it.
- **Reads:** contacts come from a `callbackFlow` over a `ValueEventListener` and the list is never edited locally, so what is on screen is what is stored. The profile is a one-shot suspend read (`fetchProfile`); the Device tab's header falls back to the session's values when the record is missing or the read fails. Track reads no profile at all and has had no avatar since 2026-10-19; its band marker shows the session's Google photo or initials ("Track and map"). RTDB disk persistence is **not** enabled.
- **Failures are typed and mapped by code.** `ContactError` + `FirebaseDatabaseErrorMapper`, on `DatabaseError.getCode()`, never on a message. That code is only reachable through the SDK's completion-listener and `ValueEventListener` callbacks — an awaited `Task` fails with a bare `DatabaseException` carrying localised prose — which is why these repositories wrap the listener forms in `suspendCancellableCoroutine` instead of using `.await()`.
- **Contact phone numbers are never logged**, and neither are contact names. They are a third party's personal data, not the user's own. Nothing in `data/` logs at all.

## Account deletion

A destructive row in a red-outlined **danger zone** at the bottom of
**Account information** (`ui/account/AccountActivity`, opened from the
Settings row of that name), driven by `ui/account/DeleteAccountViewModel` —
its own ViewModel, because `AccountViewModel` only shows the account and this
flow has six steps, two guards and a credential prompt. It was a row at the
foot of Settings until 2026-10-14; one level deeper it is much harder to hit
by accident. The path users are told is **Track → gear → Settings → Account
information → Delete account**, and the hosted `/delete-account` page and the
Privacy Notice must say the same.

**The order of operations is the design, and reversing it loses data for good:**

1. **Re-authenticate.** Firebase refuses `delete()` on a session that has been
   sitting around (`AuthError.RequiresRecentLogin`). A password account types
   its password; a Google account goes back through the chooser.
2. **Replace `users/{uid}` with the deletion marker** in **one** `setValue`
   (`UserProfileRepository.markDeletionPending`): profile fields, every
   emergency contact and the consent record go, and `{ "deletion_pending":
   true }` is all that is left — **while still signed in.** The rules
   authorise it with `auth.uid == $uid`, so once step 3 succeeds there is no
   session left that could make this write and the subtree is orphaned
   permanently. Data out and marker in are one commit, so there is never a
   moment when the data is gone and nothing says why.
3. **Delete the Auth user.**
4. **Go to Login** with the back stack cleared, through the same
   `LoginNavigation.clearCredentialsThenGoToLogin` that Log Out uses.

**Which credential it asks for comes from `AuthRepository.currentUserProviders()`,
and `PASSWORD` wins a mixed account.** Providers are read off
`FirebaseUser.providerData` — `getProviderId()` returns the literal
`"firebase"` for the user object and says nothing about how they sign in. A
mixed account cannot be created by this app anyway (Firebase's
one-account-per-email setting turns a second method into
`AccountExistsWithDifferentCredential` rather than a link), and for
re-authentication either credential satisfies Firebase equally, so the
tie-break is UX: a password needs no chooser and no second account picked by
mistake. An account whose providers are all unrecognised **refuses to start**
rather than guessing one.

**A half-finished delete must not strand anyone.** `profileDeleted` and
`reauthenticated` are the guards — the same idea as `accountCreated` in
`SignUpConsentViewModel` — and Retry re-runs only what has not landed.
`profileDeleted` is the point of no return: from there the state may only
reach `DONE` or sit in `FAILED` offering Retry, **Cancel is ignored**, and the
failure is a card in the layout rather than a dialog, because a dialog can be
dismissed into nothing. Tapping the row again resumes instead of re-confirming.
A `RequiresRecentLogin` on step 3 clears `reauthenticated`, so Retry goes back
through the prompt rather than into a call Firebase will refuse again.

**`devices/` is never touched.** `guardband-001` is shared by every install
until pairing exists, so one user's deletion must not take the band's history
with it — and the modal says so, rather than implying everything is gone.

**The deletion marker closes the "comes back thin" gap (2026-10-14).** If step
2 succeeds, step 3 fails and the user gives up, the marker is what survives:
- **`ProfileState`** is three-valued — `ABSENT`, `PRESENT`, `DELETION_PENDING`
  (`UserProfileRepository.profileState`) — and `ProfileProvisioning` writes
  only on `ABSENT`. A marker is never re-provisioned; that used to bring the
  account back with a name and email but no contacts and no consent.
- **`HomeViewModel`'s gate reads it.** Every way in — a restored session from
  Splash, an email login, a Google sign-in — lands on Home, which sends a
  pending account to Account information with the task cleared
  (`HomeEvent.ResumeDeletion`) instead of into tabs that would write a contact
  under a deleted account. A failed read used to let them in; since the
  consent gate it is a Retry cover instead (see "Consent gate").
- **`DeleteAccountViewModel` reads it on start** and opens in the same state a
  failure after step 2 leaves: `FAILED`, `profileDeleted`, no fresh
  credential, wording `MSG_RESUME_PENDING`. Retry asks for the password or
  the Google account again before anything is called. **Log Out** is offered
  beside Retry once the data is gone, because the marker keeps the deletion
  waiting for the next sign-in, on any phone.
- **It is per uid**, so another account on the same phone is untouched, and a
  later sign-up with the same email gets a new uid and no marker.

**The marker outlives a finished deletion, by necessity.** Once the Auth user
is gone no session can write `users/{uid}`, so `deletion_pending: true` stays
under a uid that no longer belongs to anyone — no name, no email, no contacts.
Any marker cleared before the Auth delete would reopen the gap. A human can
sweep them from the Console. Both hosted pages say so (2026-10-24): a marker
under the account's internal identifier, holding no name, email or contacts.

**No rules change was needed, and that was checked.** `users/$uid` has
`".write": "auth != null && auth.uid == $uid"`, nothing at that level refuses
an unlisted child, and RTDB skips `.validate` on the children a replace nulls.
On 2026-10-14 the stripped `RTDBS-RULES.md` was loaded into the local Database
Emulator (a `demo-` project id): the owner's replace was allowed and left only
the marker, a stranger's and an unauthenticated one were denied, the retry and
the gate's read were allowed, and Playground test 2 still passed. **If a
`$other: { ".validate": false }` is ever added at `users/$uid`, give
`deletion_pending` a rule of its own first**, or every Delete account fails at
step 2 — safely, with nothing deleted, but it fails.

**Not covered by tests:** `reauthenticate`, `delete` and the RTDB write
themselves, which need a real Firebase. The sequencing, the wording, both
guards and every marker path (written before the Auth delete, resume on a later
visit, offline, recent-login, Log Out, another account, a failed read) are
covered — `DeleteAccountViewModelTest` (39), `HomeViewModelTest` (37, with
the consent gate's) and
`ProfileProvisioningTest`.

## Consent gate

> The routes, the decisions and the manual pass:
> [docs/privacy-and-consent.md](docs/privacy-and-consent.md) §8 and §6.

**An account with no `users/{uid}/consent` never reaches the tabs
(2026-10-14).** `ProfileProvisioning` gives a Console-made account, and a
Google account whose record went missing, only a `name` and an `email`.
`HomeViewModel` now reads the record before anything is shown and sends such
an account to `ui/consent/ConsentRequiredActivity` (`HomeEvent.RequireConsent`,
task cleared).

- **Order:** no session → Login; unverified password account → the
  verification gate; `deletion_pending` → Account information; **then**
  consent. A pending deletion always wins. Provisioning has already finished
  on every sign-in path by then: it runs inside `login` and
  `signInWithGoogle`, before they return.
- **Only a missing record triggers it.** `ConsentGate.needsConsent(record)` is
  `record == null`, so `1.0` stays `1.0` and is let in. A re-consent flow
  would add its version rule there, not in the gate. A node without
  `accepted: true` reads as no record; the rules refuse to store one anyway.
- **Three outcomes, never two.** `UserProfileRepository.consentRecord` returns
  a record, null, or a failure. A failure, or no answer within
  `HomeViewModel.GATE_READ_TIMEOUT_MS` (10 s), is `HomeGateState.FAILED`: a
  Retry cover, **never** the consent screen and never the tabs. The timeout is
  load-bearing: with no disk persistence an RTDB read made offline does not
  fail, it waits forever. This ends the deletion check's old "a failed read
  lets them in".
- **No flash.** `HomeActivity` adds no tab Fragment until the gate is `OPEN`;
  the cover `fl_home_gate` (spinner, or Retry) sits over everything until
  then, including Fragments a restore re-added.
- **The same agreement as sign-up.** The screen inflates
  `activity_signup_consent.xml` itself and relabels only the title and the two
  bottom buttons (Decline, Agree), so the statement, the checkbox and the
  notice button are sign-up's own views and strings. Agree calls
  `recordConsent`, which writes the same three paths as `finalizeSignUp` from
  one shared builder, at `CONSENT_VERSION`, for the **live session's** uid.
  Rewording the statement still means bumping `CONSENT_VERSION`, and now it
  changes both routes at once.
- **Agree continues only after the write lands,** to `HomeActivity`, whose gate
  reads the record again. A failed write, or one that does not land within
  `ConsentRequiredViewModel.WRITE_TIMEOUT_MS` (an offline write queues rather
  than failing), stays on the screen with Try again.
- **Decline signs out and changes nothing else.** Nothing is written to
  `consent` and the profile and contacts are left alone. It leaves through
  `LoginNavigation.clearCredentialsThenGoToLogin`, with a toast saying why,
  and the next sign-in meets the screen again. There is no in-app delete from
  here, because Settings is behind the gate; the hosted `/delete-account`
  page's manual route covers it.
- **Sign-up is not prompted twice,** and no flag says so: its own atomic write
  stored consent before Home reads it, so a stale flag cannot wave anyone
  through.
- **No rules change.** Checked on 2026-10-14 in the local Database Emulator
  against the stripped `RTDBS-RULES.md`: the owner's late write is allowed on
  an existing profile and on an absent one; a stranger's, an unauthenticated
  one, a partial one and `accepted: false` are denied; Playground test 2 still
  passes. **Still do not add `users/$uid/.validate: newData.hasChildren(['consent'])`.**
- **A product gate, not a security boundary,** like the verification gate:
  nothing server-side requires consent.
- **Known limits.** An account that reaches the gate with no record at all
  (`ABSENT`: its provisioning write failed) gets only `consent` written, and
  the Device tab falls back to the session's name and email. A first-time
  Google user who abandons the contacts step now meets this screen on their
  next launch and can agree with **no emergency contacts**: the gate checks
  consent, not contacts.

**Not covered by tests:** the RTDB read and write themselves and the two
Activities. The decision, every outcome, the timeouts, Agree, Retry and
Decline are covered: `HomeViewModelTest` (37), `ConsentGateTest`,
`ConsentRequiredViewModelTest`.

## Privacy, consent and the hosted pages

> The notice, the delete-account page, the consent version log, the rules'
> phone pattern and the human checklist:
> [docs/privacy-and-consent.md](docs/privacy-and-consent.md).

**Two static pages are served from the Firebase Hosting domain**:
`/privacy` (the Privacy Notice) and `/delete-account`, plus a root page that
links both and `/.well-known/assetlinks.json`. Both are **hosted rather than
in-app screens** on purpose - somebody who has uninstalled GuardBand still
needs to read what they agreed to and find out how to get their data removed.
Nothing there runs a script.

**One host constant, two paths, declared once** at the top of
`app/build.gradle.kts`: `hostingHost` (`guardband-aae65.web.app`),
`privacyPath`, `deleteAccountPath`. They reach the code as `buildConfigField`s.
`utils/HostedPages` is the **only** place a URL on that domain is built;
nothing else may spell one out. The reset link has its own
`resetLinkHost` (`firebaseapp.com`) - see "Forgot password".

**The notice's version and `CONSENT_VERSION` move together.** Bumping
`UserProfileRepository.CONSENT_VERSION` means editing
`hosting/public/privacy/index.html`'s displayed version and adding a row to
the log in `docs/privacy-and-consent.md`, **in the same commit**. The point of
storing a version with somebody's consent is being able to say which words
they agreed to, and that fails the moment the number cannot be matched to a
document. It is a **String** (`"1.3"`), not a number, so `"1.10"` can follow
`"1.9"`.

**The consent statement stays plain text and the link is a separate button.**
The statement is the `MaterialCheckBox`'s own `android:text` so that tapping
the words toggles the box and TalkBack reads the two as one control. A
`ClickableSpan` inside that label fights the toggle and loses that reading, so
the link is `btn_signup_consent_privacy` directly beneath it. Don't "improve"
it into a span.

**Opening either page is a plain `ACTION_VIEW`** - no custom-tabs dependency,
no permission. A phone with nothing registered for `https` throws
`ActivityNotFoundException`, which goes back through the ViewModel so the
wording stays with every other `MSG_*`.

**The Privacy Notice is a row of About** (Settings → About,
`ui/about/AboutActivity`, since 2026-10-20), which opens the hosted
document; it was a row of Settings itself before that. A "Privacy &
security" placeholder only said "Coming soon" and was removed on
2026-10-19. If a privacy settings screen is ever built, give it its own row
rather than pointing the notice row at it.

**The draft rules' `phone` `.validate` and `InputValidator` must stay in
step.** The app normalises to E.164 **before** writing
(`ContactFields.validated`), so the rule matches the *stored* form, never what
was typed. Its three branches are a PH mobile plus the generic E.164 case split
by first digit, because the app refuses anything starting `+63` that is not a
PH mobile and RTDB's regex has no negative lookahead. Change one, change the
other.

**The notice states only what the code does.** Undecided facts carry a
`DECISION: Jul` rather than a plausible default, and it claims compliance with
nothing. **Both pages are published without a DRAFT banner (2026-10-24, notice
1.2)**: the team decided to publish without the adviser review, so the pages
are the final word on what the app does, and a change to what the app
collects or who receives it must change the notice (and bump the version) in
the same commit. **Firebase Analytics was dropped** because no app code called it, yet it
collected an advertising id on its own; the notice now says the app has no
analytics, so **adding any analytics SDK back means changing the notice in the
same commit.** And **the map's tile provider sees the phone's IP and the area
being viewed** on every tile, which the notice discloses. That is MapTiler
since 2026-10-19, in the notice since 1.2.

**The "profile with no consent" gap is closed in the app (2026-10-14).**
`ProfileProvisioning` still writes only `name` and `email`, so `users/{uid}`
can still exist with no `consent` subtree (an account made in the Console, a
Google account whose record went missing), but such an account now meets the
consent step before the tabs - see "Consent gate". The failed account deletion
route is closed by the deletion marker - see "Account deletion". **Do not add
`users/$uid/.validate: newData.hasChildren(['consent'])`** anyway: it would
fail that legitimate provisioning write and leave the user with no profile at
all. The original options are in the doc.

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

**A check-in (FR-10) is a `CHECKIN` alert, and the main app never writes one.** It is the wearer's double-press on the band, sent with the payload above to `history` and `latest`; there is no separate check-in node. Nothing under `app/src/main` may write a check-in, or anything else, to `devices/` — only the band does, and the debug mock sender stands in for it. See "Check-in status" below.

## Alert data

Track's recent-alerts panel and the Alert history screen read `devices/{deviceId}/history` live, through **one** host-scoped `AlertViewModel`: the panel shows `AlertUiState.recent` (the newest `RECENT_ALERTS_LIMIT`, 3), the history screen `AlertUiState.history`, both cut from the same emission. `SCHEMA.md` is the authority for the payload — if code, this file and `SCHEMA.md` disagree, `SCHEMA.md` wins and the disagreement gets reported rather than silently resolved.

**The locked decisions behind the alert screens:**
- **D1** — `PANIC`, `CHECKIN` and `LOW_BATTERY` are shown and colour-coded. `TRACKING_UPDATE` is hidden from the Alert list. An **unrecognised** type is *not* hidden: it is shown as "Unknown alert", so a type a future firmware adds still appears.
- **D2** — the latest-alert card is the newest **non-tracking** entry derived from history. It deliberately does **not** read `devices/{id}/latest`, because every payload overwrites that node, tracking updates included, so the card would read "Location update" while a panic sat one row below it. `observeLatestAlert` still exists, unsubscribed, for the Track tab.
- **D3** — read a window of 100 raw entries, drop tracking updates, show at most 50. The window is over *raw* entries, so a chatty band yields fewer than 50 displayable alerts. `AlertHistory` therefore carries `rawCount` and `malformedCount`, which is what lets the empty state say *which* kind of empty it is instead of implying the band is silent.
- **D4** — one device id, `DeviceConstants.DEFAULT_DEVICE_ID`, supplied through `RepositoryProvider`. No pairing yet, and ViewModels never see a device id.
- **D5** — `schemaVersion` is parsed as a String, so both `"1.0"` and a bare `1` work.

**Repository contract.** `AlertRepository` exposes `observeLatestAlert()` and `observeAlertHistory(limit)`, both `Flow<Result<T>>`. A failed read is `Result.failure(AlertError)` — never null, never an empty list — and the flow **stays open**, so a reconnect or a rules fix can make the next emission succeed. `AlertError` is mapped from `DatabaseError.getCode()` through `FirebaseDatabaseErrorMapper`, never from a message, and carries no message of its own.

**Ordering is `orderByKey().limitToLast(n)`, and needs no index.** History children are keyed by `sequenceId`, and RTDB sorts integer-like keys numerically and ahead of string keys, so this returns the newest entries. `orderByChild("sequenceId")` would need an `".indexOn": "sequenceId"` rule deployed; `orderByKey` needs none. The window is re-sorted by `sequenceId` in memory anyway, so a key that is *not* integer-like (a push id, a zero-padded number) degrades the window rather than the order within it.

**Parsing is a pure `Map` → `Alert` step in `AlertParser`,** not a method on the repository. A `DataSnapshot` cannot be built in a JVM test and the project declares no mocking library, so putting the logic behind one would have made all of it untestable. `sequenceId`, `type` and `timestamp` are required; a missing `schemaVersion`, `deviceId`, `battery` or `location` is salvaged rather than dropped, which is deliberately more forgiving than `SCHEMA.md`'s receiver-validation list — this is a *reader*, and refusing to show a panic because its battery node was missing would be the wrong failure. **A malformed history entry is skipped and counted, never fatal:** one bad row from a firmware bug must not hide every good row behind it. Only `latest` can fail as `AlertError.ParseFailure`.

**Locations are never logged.** An alert carries the wearer's coordinates; nothing in `data/` logs at all, and neither alert screen logs anything either. Same rule as contact phone numbers — see "Contacts and profile data". A place name is a location too.

**Rows show place names, not coordinates (2026-10-20).** `AlertViewModel` looks up each shown alert's point through `PlaceRepository` (Android's `Geocoder`: no key, no dependency, no permission; Google's service on Play-services phones), once per point rounded to 4 decimals, and keeps the names in memory only (`AlertUiState.placeNames`). Coordinates show until a name arrives, and for good when none exists. This sends the band's rounded positions to the geocoder's service, which the notice discloses (since 1.2). Details: [docs/alert-data.md](docs/alert-data.md) §7, "Place names".

**Every alert card is bound by `ui/alert/AlertCardViews` (2026-10-20)**, the
rows and Alert history's latest card alike: the type's symbol on a pale badge,
a panic's card pale red, a pin and a battery symbol (`AlertFormatting.batteryIcon`,
orange at FR-11's 20%). The panel and the card say "5 min ago"; Alert history
lists `AlertListItem`s, day headings (`AlertDays`, the phone's zone) over
clock times. `AlertUiState.nowMillis` ages them on a 30 s injected ticker,
empty by default so tests never hold a `delay`. [docs/alert-data.md](docs/alert-data.md) §8.

**Still in-memory:** `InMemoryStore`'s four seeded alerts, which only `InMemoryAlertRepository` reads, and only in tests.

## Track and map

> The map library itself - why osmdroid, the user agent, the MapTiler tile
> source and its key, attribution, and the blocked-tile trap:
> [docs/osmdroid.md](docs/osmdroid.md).

The Track tab reads `devices/{deviceId}/latest` live and puts the band's last
known position on a map drawn from **MapTiler** raster tiles (OpenStreetMap
data). `SCHEMA.md` is the authority for the payload, as everywhere else.

**osmdroid, not the Maps SDK.** `osmdroid-android 6.1.20`, chosen because it
needs no billing-enabled GCP project. It is safe on compileSdk
34 without a bump: its POM declares **zero dependencies** and it publishes no
Gradle module metadata, so there is none of the `minCompileSdk` constraint that
pins androidx-credentials at 1.3.0.

**`ui/track/OsmdroidConfig` is the one place it is configured.** Two things must
be right before a single tile is requested, and the Fragment therefore calls it
in `onCreate`, before the layout inflates and builds the `MapView`:
- **The user agent is a fixed, app-specific string**, `OsmdroidConfig.USER_AGENT`
  — **not** the package name. OSM's usage policy wants a unique, identifiable
  User-Agent, and their servers block shared ones: both `osmdroid` (the library
  default) and the `com.example.*` prefix the project template generates, which
  is still this app's `applicationId`. The block arrives as **HTTP 200 with a
  PNG** reading "access blocked", not as an error, so osmdroid renders and
  caches it like a good tile and the log says nothing. The MapTiler key is now
  **restricted to this exact agent**, so changing it refuses every tile until
  the MapTiler dashboard matches. Deriving the agent from the package or the
  version is what `ui/track/MapTilerTileSource`'s policy exists to prevent —
  see below.
- **Both cache paths point at `cacheDir`**, so tiles live in app-specific
  storage and need **no storage permission** on any API level. osmdroid's own
  default wants external storage.

**`ui/track/MapTilerTileSource` is the one tile source class (2026-10-19), and the
only place a MapTiler URL, the key, the zoom range (0-22) or a credit is
spelled out.** It replaced `GuardBandTileSource` (OSM's public server). Never
fall back to `TileSourceFactory.MAPNIK` or any `tile.openstreetmap.org`
source, key or no key; MAPNIK is also the `MapView`'s default, which is why
the Fragment sets this source even without a key.
- **The key is `MAPTILER_API_KEY` in `local.properties`** (gitignored), into
  `BuildConfig`. Never put the real key in a tracked file; docs use
  `YOUR_MAPTILER_API_KEY`. A build without it succeeds; the Fragment then
  turns the data connection off, logs one warning and shows "Map
  unavailable", and no tile is requested from anywhere. The key ships in the
  APK and rides in every tile URL: never log a tile URL, and never turn on
  osmdroid's `isDebugMode` / `isDebugMapTileDownloader`.
- **The tile cache stays on; no bulk downloading, pre-fetching or offline
  regions.** `FLAG_NO_BULK` and `FLAG_NO_PREVENTIVE` stay in the policy.
- **The credits** ("© MapTiler © OpenStreetMap contributors", each linked)
  are `track_map_attribution`, from `MapTilerTileSource.attribution()`, above
  the collapsed sheet. `ui/track/MapAttribution` shows them on every Track
  view creation and collapses them to an ⓘ (`track_map_attribution_info`)
  after 5 s or the first pan: the OSMF attribution guidelines' rule, which
  MapTiler has not confirmed (ask before a public release). Never hide them
  by default, and keep them readable (10 sp is the floor).
- **The MapTiler logo** (the Free plan requires it) is `track_map_logo`,
  bottom left in the same row, linked to `MapTilerTileSource.LOGO_URL` by
  `MapAttribution`. **It never collapses**; only the text goes to the ⓘ.
  It sits straight on the map, transparent with no chip (2026-10-27), so
  **it follows the tiles, not the theme**: `logo_maptiler_white` on a style
  whose `MapTilerTileSource.drawsDark` is true (Streets Dark, Satellite),
  `logo_maptiler` (dark lettering) on the rest. `TrackFragment.applyLayer`
  swaps it with the tile source.
  Keep the row's fixed height and the ⓘ's 48 dp touch target. When the
  credit does not fit beside the logo (narrow screen, large font),
  `MapAttribution` moves it onto its own line above the logo, measured from
  its real width; the pill stacks on `barrier_track_credits`, so it moves
  only for a credit that wraps. Never shrink the credit below 10 sp to fit
  (`docs/osmdroid.md` §4.6).

**Its policy drops `FLAG_USER_AGENT_NORMALIZED`, and must keep dropping it.**
`MAPNIK` is built with `TileSourcePolicy(2, 15)`, and 15 is all four flags
including `FLAG_USER_AGENT_NORMALIZED`; with that flag osmdroid's
`TileDownloader` ignores `userAgentValue` and sends `packageName/versionCode`
instead — `com.example.guardband/1`, which OSM blocked and the MapTiler key's
user-agent restriction would refuse. The policy keeps every `MAPNIK`
restriction **except** normalization. Two consequences worth knowing:
- **Never call `Configuration.getInstance().load(context, prefs)`.** It is the
  only thing that computes the normalized agent, and it would re-break the map
  on any tile source that normalizes. Dropping the flag and avoiding `load()`
  are independent guards; keep both.
- **Its name, `MapTilerStreets`, is the cache key**, because osmdroid keys
  cache rows by `(key, provider)` where `provider` is the source name. A new
  name starts on clean rows rather than re-serving the OSM tiles (or the
  blocked images) cached under `GuardBandOsm` and `Mapnik`. Every style has
  its own name, so no two styles ever share cache rows.

**The layers button picks the base map (D7, 2026-10-27).** `track_layers_button`
opens `dialog_map_type.xml`, a bottom sheet with five choices, `ui/track/MapLayer`:
**Auto** (Streets, dark in dark mode; the default), **Street** (always the light
Streets), **Satellite** (`MapTilerSatellite`, no labels), **Outdoor**
(`MapTilerOutdoor`, terrain and trail data) and **Topo** (`MapTilerTopo`, elevation
contours and hill shading). `MapTilerTileSource.forLayer` is the one place a layer
becomes a tile source; the Fragment never picks one. The choice is
`TrackUiState.selectedLayer`, so it survives a theme change, and
`ui/track/MapLayerPrefs` saves it (SharedPreferences `map`, per phone, like
the display setting), so the map reopens on it; a name it does not know reads
as Auto. All styles are served on this key and plan (checked 2026-10-27: real 256 px tiles over Cebu City).

**No phone-location permission in the main app, ever.** The band supplies the
location, not the handset. osmdroid adds only four *optional*
`uses-feature` entries to the merged manifest (`location.network`, `location.gps`,
`telephony`, `wifi`, all `required="false"`) and **no** `uses-permission` at
all; `ACCESS_NETWORK_STATE` was already there via Firebase. (The release
permissions are `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_GSERVICES`, the
app's own receiver permission, and since 2026-10-19 the three for alert
notifications; see "Alert notifications".) Never add
`ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` to the main manifest, and
never use osmdroid's `MyLocationNewOverlay` — that is the class that would drag
them back in.

**The one deliberate exception: `app/src/debug/AndroidManifest.xml`.** The mock
sender stands in for the ESP32 + NEO-6M hardware, and it is correct for it to
report Phone A's own GPS fix — that is what the real band will do. Both
`ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are declared there and
**only** there, so a release build contains neither permission. `MockSenderActivity`
requests them at runtime via `ActivityCompat.requestPermissions` and falls back
to the Cebu City random position if denied, so the permission refusal is always
survivable. If this exception is ever removed, remove the runtime request, the
`LocationListener` and the GPS badge from `MockSenderActivity` in the same
commit.

**The band status panel was removed from the screen (2026-10-14).** The
Check-in pill is now Track's one status line, so the Reporting badge, "Last
seen" and battery are no longer shown there. `TrackViewModel` still computes
`isOnline`, `elapsed` and `battery`; the **Device tab** shows them (2026-10-27).
**`ui/track/BandReport` is the one place these rules live** (retained
position and battery, reporting at under 180 s, last seen, low battery at
20%), used by Track (fed `latest`) and the Device tab (fed the history
window's newest entry). Don't re-derive any of them in a ViewModel.

**"Reporting", not "Online".** `BandStatus.ONLINE_THRESHOLD_MS` is **180 s**,
three of FR-09's 60 s tracking intervals, so one dropped update does not flip
the badge. Be careful what it can mean: FR-09 tracking is *post-alert* and
stops after 30 minutes, so an **idle band legitimately sends nothing and will
read as stale**. The badge therefore says whether the band is reporting *right
now* — which is all a timestamp can honestly support — and the screen leads with
"Last seen X ago". Calling it "Online" would read as a claim about the wearer.
A future timestamp counts as reporting, not stale: the band's clock can run
ahead of the phone's.

**Staleness is recomputed on a timer, not only when data arrives.** A band that
goes quiet emits nothing, so without the ticker the badge could never leave
"Reporting" — it would hold whatever it said when the last report landed.
`TrackViewModel.TICK_INTERVAL_MS` is 30 s, and the ticker is **injected as a
`Flow<Unit>`** rather than looped internally so a test can drive it and never be
left holding an endless `delay`.

**The last known position is retained** across reports that carry none. A
tracking ping with no fix means the band is talking but cannot see the sky, and
blanking the map in that moment throws away the most useful thing on screen.
Battery follows the same rule. `hasReported` is separate from `hasFix` so the
screen can tell "the band has never said anything" from "it is reporting without
a fix" — both have no coordinates and read very differently.

**Recentring belongs to the Fragment, not the ViewModel.** Where the camera
points is view state, like a scroll offset. Following is switched off by a touch
listener on `ACTION_MOVE`, not a `MapListener`: a listener cannot tell the
user's pan from the camera moves the screen makes itself, and a tap gives only
DOWN and UP, so tapping the map keeps following. **A tap on the round target
button** (`track_navigate_button`, `ic_my_location`, "Center on band") turns it
back on and animates to the band's last known position (2026-10-27). If the
user has zoomed out past `FIX_ZOOM` (16, street level) it zooms back in to
that in the same animation; zoomed in closer, their zoom stays. **There is no
Google Maps hand-off any more:** D6 (a tap opened a maps app on the band's
coordinates, re-centring was a long-press) was removed on 2026-10-27, with
`NavigationUrl`, `TrackEvent.OpenNavigation` and the notice's Google Maps row
(notice 1.3). The button is disabled without a fix; `onRecenter` still falls
back to the default Cebu City view if it is ever reached without.

**The recent alerts are a draggable bottom sheet over the map (2026-10-19).**
`fragment_track.xml` is a CoordinatorLayout; `view_track_recent_alerts.xml` is
its `BottomSheetBehavior` child (not hideable), bound by
`ui/track/RecentAlertsPanel`: Material's drag handle, a header with "See all"
to Alert history, and the newest three alerts in `item_alert` rows. Collapsed,
it shows `@dimen/track_sheet_peek_height` (96dp: the handle and the header
only); dragged up, the three rows. The map's controls stack on a `Space` of that
same height, so the collapsed sheet never covers them; the expanded one does,
on purpose. The card is `clickable` so a tap on it never reaches the
`MapView`; drags are the behavior's. The CoordinatorLayout has an id
(`track_root`), which is what saves the sheet's state across a restore.
Don't remove either.

**The band's marker is a face (2026-10-19).** `ui/track/BandMarkerIcon` draws
a round photo or initials in a white ring with a pointer whose tip is the
anchor; `BandMarkerFace` decides photo or initials, reusing
`ui/avatar/AvatarPhoto` and Coil. **`TrackViewModel.markerFace()` is the one
place that decides whose face it is** (the signed-in user's until roles
exist). It is drawn only when the face changes, never per position; never log
the photo link. Details: `docs/osmdroid.md` §5.4.

**Recent alerts are pins under the face (2026-10-27).** `ui/track/AlertPinSelector`
(pure) picks the newest 10 panics, check-ins and low-battery alerts with
usable coordinates, one per 10 m and none under the face, from the list
`TrackFragment` hands over from `AlertViewModel` (`TrackViewModel.onAlertsChanged`):
**no listener of their own**. **Panics choose their spots first**, so a newer
check-in or low-battery alert never takes a panic's pin; the dedupe runs
before the cap. A panic pin opens its Incident view; other pins open a label
("Check-in, 5 min ago"). **The face's bubble is `BandBubbleWindow`**, not
osmdroid's stock one, because it offers "View incident" while
`TrackUiState.bandPanic` is set (the latest non-tracking alert is a panic):
the way to a panic whose pin the face hides. Don't swap it back. **The
overlay order is load-bearing:** the pins' `FolderOverlay` at index 0, the face
above it, and a pass-through tap watcher last, which closes the label on every
tap and never consumes one. Add any new overlay under the watcher
(`addUnderTapWatcher`). Details: `docs/osmdroid.md` §5.5.

**Every way into an Incident view goes through `HomeViewModel.onOpenIncident(alert)`
(2026-10-27)**: a panic row in Track's sheet or Alert history, a panic pin, the
face's "View incident". It ignores non-panics (`IncidentEntry.opensIncident`);
add a new entry point by calling it, never by sending `HomeEvent.ShowIncident`
yourself. The view reads **its own** query, `AlertRepository.observeIncident`
(`history` from the panic on, `startAt` + `limitToFirst(400)`), because the
alert screens' 100-entry window loses a panic within minutes of a walking
band; it is the only reader of tracking updates, which stay out of every list
and every pin. `IncidentGrouper` ties them to the panic by `sequenceId` (after
it, before the next panic) and 30 minutes: SCHEMA.md has no incident id.
Details: `docs/incident-view.md`.

**The `MapView` is detached by hand** in `onDestroyView` (plus `onResume` /
`onPause`). osmdroid holds a tile cache, a worker and overlay references, so
without it the Fragment's view leaks.

**Coordinates are never logged.** Same rule as contact phone numbers and
alerts — see "Alert data". Neither the Track tab nor anything in `data/` logs.

**The two ISO-8601 patterns live once, in `AlertFormatting.ISO_PATTERNS`.**
`BandStatus` reads them through `AlertFormatting.newIsoUtcParser`, which hands
out a **new** parser per call: `SimpleDateFormat` is not thread-safe, and
`BandStatus` runs from a ViewModel. `AlertFormatting`'s own cached parsers are
main-thread only, so never share those instances with the Track side.

**Not for production as-is:** MapTiler's Free plan is for non-commercial use
and pauses at its monthly limit, and the key in the APK is protected only by
the user-agent restriction and that cap. A real release needs a paid plan.

**Not covered by tests:** `OsmdroidConfig`, the `MapView` lifecycle, the marker
and the D4 follow logic. All of it needs a real `Context`, a running map or an
instrumented test, and the project declares no mocking library. The testable
logic was lifted out instead — `BandStatus` and the ViewModel.

## Device tab

> What it shows, where each value comes from, the pairing shape and the
> phone checks: [docs/device-tab.md](docs/device-tab.md).

The third tab since 2026-10-27 (`ui/device/`, `home_device`), in place of
Profile. Four things are easy to break:

- **No listener of its own.** `DeviceFragment` hands `DeviceViewModel` the
  state of the host-scoped `AlertViewModel` (status, battery and last alert,
  from `AlertUiState.newestReport` and `latest`) and `ContactsViewModel` (the
  count). Its only call is the one-shot `fetchProfile`. "Now" is
  `AlertUiState.nowMillis`, so it has no ticker either.
- **No coordinates, and no geocoding of a tracking update.** The last known
  position is named only when it is an alert's, with the name the alert
  screens already found (`DeviceViewModel.positionOf`); a tracking position
  reads "On the move". The Privacy Notice covers the geocoder for alert
  positions only, so naming a tracking point needs a notice change and a
  `CONSENT_VERSION` bump first.
- **Pairing is a shape, not logic.** `DevicePairing` is `Paired` or
  `Unpaired`; it is always `Paired` with `RepositoryProvider.watchedBandId`,
  which the Factory passes for display only. No band nickname: new stored
  data needs the notice first.
- **Settings' Device information row and the contacts row** navigate through
  `HomeViewModel` (`ShowDevice`, `ShowContacts`), which `show()` a tab.

## Check-in status

The Track tab's Check-in pill (`track_checkin_pill`) shows the wearer's latest
check-in (FR-10): "Last check-in: 5 minutes ago", or "No recent check-ins".
**It is a status, never an action.** It is a `TextView`, not a `Button`, so it
has no click, no ripple and TalkBack reads it as text. The app never sends a
check-in; only the band does (see "Firebase data contract").

- **Read from history, not `latest`.** `AlertRepository.observeLatestCheckIn()`
  is the newest `CHECKIN` in the same 100-entry history window the alert
  screens read, picked by `CheckInSelector` from what `AlertParser` already parsed.
  `AlertParser` stays the one reader of the payload; there is no second
  check-in parser, because there is no second payload.
- **"None" means none recently.** A check-in older than the window drops out,
  which is why the wording is "No recent check-ins" and not "never".
- **It ages on the existing ticker.** `TrackViewModel.recomputeStaleness`
  recomputes the check-in's `Elapsed` on the same injected `staleTicks` as the
  band status, and the Fragment words it with the same plurals pattern.
- **Independent of the map.** A failed check-in read never touches the map,
  and a failed `latest` read never touches the pill. A failure keeps whatever
  the pill already says; only a pill with nothing yet becomes "Check-ins
  unavailable".

## Alert notifications

> The design, the rules, the channels, the permissions and the device
> checklist: [docs/notifications.md](docs/notifications.md).

**Opt-in, from Settings → Alert notifications (2026-10-19).** The switch,
on its own screen since 2026-10-20 (`AlertNotificationsActivity`, an
Activity like Account information so Back returns to Settings), starts `ui/notifications/AlertWatchService`, a `specialUse` foreground
service that keeps the band's history listener open and posts each new
alert as a system notification on this phone. **No server, no FCM, no paid
plan:** sending FCM needs a service-account key or Blaze, so it waits for
pairing and roles.

- **`AlertNotificationDecider` decides what is new** (pure, tested): a stored
  high-water `sequenceId` per device, a baseline with no posts the first
  time, a 30-minute window on the first emission after a restart, a
  re-baseline when history is reset, never `TRACKING_UPDATE`, at most 3 per
  emission plus a summary.
- **The wording never carries a location, a name, the photo or the device
  id.** Every alert is `VISIBILITY_PRIVATE` with an empty public version and
  one shared status-bar icon (`ic_stat_alert`). **The panic channel is
  silent** (one short vibration, no heads-up), because the wearer may be next
  to the person they fear. Channels cannot be changed after creation; a
  change means a new channel id.
- **Started only from foreground code** (`AlertWatch.start`): the switch, and
  `HomeActivity` once its gate opens. **Stopped** by the switch and every
  sign-out (`LoginNavigation`). No boot receiver: after a restart it resumes
  when the app is opened.
- **A tap goes through the Home gate.** `HomeActivity.launchIntent` carries a
  `HomeLaunchTarget`; `HomeViewModel` holds it until the gate is `OPEN` and
  drops it on every other outcome.
- **Three release permissions came with it:** `POST_NOTIFICATIONS` (asked
  from the switch, never at launch), `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE`. **James, the owner, approved the release
  manifest with these three on 2026-10-27** (seven permissions in all; list in
  `docs/notifications.md` §5). Any permission beyond those needs his approval
  again, first: don't add `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`,
  `USE_FULL_SCREEN_INTENT` or `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` without it.
- **The notice has a paragraph** for it, published in notice 1.2
  (2026-10-24).

## Mock sender

> Operating manual for both launchers, end to end: [docs/mock-sender-and-receiver.md](docs/mock-sender-and-receiver.md).

For demos without hardware, Phone A runs `MockSenderActivity` and Phone B runs the receiver screens. Both icons belong to **one install** — same `applicationId` — so a single phone can play both ends. `MockSenderActivity` is intentionally *not* MVVM because it is a throwaway harness, so don't refactor it into the architecture. It lives in the **debug source set** (`app/src/debug/java/.../ui/mocksender/`, plus `app/src/debug/AndroidManifest.xml`, which gives it its own "GuardBand Mock Sender" launcher icon), so release builds contain neither the code nor the icon. OkHttp is `debugImplementation` for the same reason (release still gets it through Coil, but nothing in `main` calls it).

**`AlertSender` writes straight to the RTDB REST API,** as the band will: one `PUT` to `/devices/{id}/history/{sequenceId}.json`, then one to `/devices/{id}/latest.json`. There is no Cloud Function in the path — the Spark plan cannot host one, and the `devices` Security Rules already do the validation `ingestAlert` was going to do. (`SCHEMA.md`'s "Receiver validation requirements" still describes that Function; the payload shape it specifies is unchanged.)

- **History is written first, then `latest`.** The alert screens (Track's recent-alerts panel and Alert history) derive everything from history, so a send that got as far as history is still visible there and only leaves the Track-facing `latest` stale. The reverse would show the alert on neither and leave a permanent hole in the history. The sequence id is consumed as soon as its history row lands, so a retry cannot overwrite a good row.
- **The sequence id is seeded from the server,** by reading the history keys with `?shallow=true` once per harness instance and taking the highest integer-parseable one, so a relaunched harness appends instead of overwriting the rows it wrote last run. `shallow` rather than an ordered `limitToLast(1)`: RTDB sorts integer-like keys *ahead* of string keys, so taking the last one would return a string key if the node ever picked one up from the Console — the one case this read exists to survive. A missing node reads as `null` (not an error) and starts at 1; a genuine read failure fails the send, because the `PUT` was about to fail the same way.
- **It is one band button and a battery slider (2026-10-27), like the hardware.** `BandButtonDecoder` (pure, tested in `app/src/testDebug/`) holds the firmware's timings: **hold 1.5 s = PANIC**, fired while still held; **double-press within 2 s = CHECKIN**; **during a panic, hold 5 s = cancel**, which stops tracking and sends a CHECKIN; a single press sends nothing. A check-in deliberately does **not** cancel a panic, because it can be forced. Change a timing there and only there, to match the firmware. The six old buttons (Simulate Panic/Check-in/Low Battery/Tracking, Double-press, Start walk) are gone.
- **A panic starts tracking:** a `TRACKING_UPDATE` every 5 s for 30 min (FR-09's span; the interval is deliberately far below its real 60 s so a demo shows a path in seconds), at Phone A's GPS fix, or around a ~165 m synthetic loop without one. It starts once the panic has landed, and stops after 30 min, on a cancel, on a failed step, or in `onPause` (a loop still writing under a backgrounded harness would make a dead band look alive). There is no tracking without a panic.
- **The battery slider** is the level every message reports (never charging). Dropping it to 20% or below sends a `LOW_BATTERY` by itself (FR-11), once until it goes back above 20%.
- **`AlertSender` sends one message at a time** (a `Mutex`): a check-in pressed while a tracking step is in flight would otherwise read the same `nextSequenceId` and overwrite that step's history row.
- **The database URL comes from `FirebaseProvider`,** not a constant, so the harness follows `google-services.json`. That is a Firebase import outside a repository, which the app's layering forbids — the harness sits outside that layering, and the alternative was a hardcoded URL, which is forbidden everywhere.
- The status line reports the history key each send landed under, so a demo can be checked against the Console.
- **Writes are unauthenticated.** They rely on `".write": true` under `devices/{id}` in the draft rules. If that is ever tightened to require auth, the harness needs an `?auth=` token.

The payload itself follows `SCHEMA.md` and satisfies the `devices` validation: all six required children, `type` one of the four enum names, `percent` 0–100, `isCharging` boolean, `sequenceId` numeric. Its timestamps carry milliseconds (`...SSS'Z'`) where `SCHEMA.md`'s example does not; both are valid ISO 8601 and `AlertFormatting` parses both. **The harness no longer duplicates anything.** `ui/mocksender/AlertType` used to be a second private copy of the enum, on the reasoning that de-duplicating it would mean editing `app/src/debug/`. That rationale did not hold — the debug source set imports from `main` freely — and a drifting enum or device id would have the harness writing alerts the app never reads. It now uses `data/model/AlertType` and `DeviceConstants.DEFAULT_DEVICE_ID` directly. The dependency only ever points one way: nothing in `main` knows the harness exists.

## Dark mode

**The theme is DayNight (2026-10-20)**, chosen in **Settings → Display**
(`ui/display/DisplayActivity`): Use phone setting (the default), Light or
Dark. `ThemeChoice` maps each to AppCompat's night mode; `DisplayPrefs`
saves it (SharedPreferences `display`, per phone), and
`GuardBandApplication`, the app's one Application class, re-applies it on
every start, because AppCompat keeps it in memory only.

- **Colours are names, and `values-night/colors.xml` gives each its dark
  value.** A layout never picks a mode. A screen, a card, a top bar or the
  bottom bar is `surface_bg`; a control floating over the map (the round
  buttons, the pills) and the tabs' top buttons are `surface_elevated`.
  **`white` and `black` are literal in both modes**: only for text and icons
  on a green or dark button, and a badge's icon. A new `@color/white`
  background is a dark-mode bug.
- **The dark palette is a placeholder** on Material's dark guidance, marked
  `DESIGN: Jul`; retune it in `values-night/colors.xml` alone. The brand and
  alert colours are a step lighter there so they keep their contrast.
- **The map goes dark too** (on the Auto layer, the default): `MapTilerTileSource.forNightMode` picks
  MapTiler's `streets-v2-dark` (checked on this key and plan, 2026-10-20),
  cached apart as `MapTilerStreetsDark`. A theme change recreates the
  Fragment, which picks again. **The credits stay a light chip** in dark
  mode (`map_attribution_bg`, `map_attribution_text`); the MapTiler logo has
  no chip and turns white on the dark map (see "Track and map"). See
  [docs/osmdroid.md](docs/osmdroid.md) §4.8.
- The status bar follows: `surface_bg`, with `@bool/theme_light_status_bar`
  for its icons. The green sign-up screens are the brand in both modes.

## UI workflow (agent vs. designer)
- Figma is the visual source of truth and is owned by Jul. The agent does not need the wireframes pasted in.
- The agent builds: layout skeletons with stable snake_case view IDs, binding to state, buttons and click handlers, state/event plumbing, strings in strings.xml, and accessibility labels. It uses theme tokens only.
- The agent does NOT: tune spacing, colors, typography, or icons beyond placeholders. Mark such spots with `<!-- DESIGN: Jul -->`.
- Each screen's KDoc lists its view IDs so the layout XML can be restyled without touching logic.

## Changing the launcher icon

The 2026-10-13 icons were generated from `res/drawable-nodpi/logo_mark.png` by a
PowerShell script that was never committed. **That script is gone; don't try to
recreate it** — a guess at its sizing would silently change the icons. Use
Android Studio's **Image Asset** tool instead (right-click `res` → New → Image
Asset → Launcher Icons, name `ic_launcher`):

- **Foreground:** `logo_mark.png`, resized so the logo stays inside the safe
  zone and no launcher mask clips it.
- **Background:** colour, white (`ic_launcher_background`, `#FFFFFF`).
- **No monochrome layer.** Themed icons are drawn from a layer's transparency,
  and the logo is an opaque picture on white, so it would theme as a plain
  square. Without one, Android shows the colour icon.

Image Asset rewrites `mipmap-anydpi-v26/ic_launcher.xml` and
`ic_launcher_round.xml`, so check afterwards that neither gained a
`<monochrome>` line, and put back their header comment. The mock sender has no
icon of its own; it shows the same one under a different label.

## Changelog

Settings → Changelog opens `ui/changelog/ChangelogActivity`, a progress log
for the team and the instructor, built from the `mvvm-main` history. The
content is `Changelog.entries`: one entry per day that work landed, newest
first, each with the short hash of that day's last commit. **When work merges
into `mvvm-main`, add (or extend) the entry for that day at the top, in the
same merge.** `ChangelogTest` checks the order, the dates and the hashes.

It is an Activity rather than a Fragment pushed over the Home host because the
host's Back always returns to a tab, and Back from here must return to
Settings. It is outside the host's auth gate, which is fine: it shows nothing
about the user.

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
- **The developer tests on their own phone in every development session.** James checks each change on a personal phone while the session runs, so don't ask whether something has been tried on a device, and don't end reports with repeated "needs a phone pass" reminders. When a change adds device behaviour, still write its manual checklist into the relevant doc once; just don't chase it.
