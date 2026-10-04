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

Do not port MVVM code into the MVP checkpoint branches, or MVP code into the MVVM branches.

## Architecture (MVVM, on `mvvm-jedd`)

View ↔ ViewModel ↔ Repository. Pre-auth screens are Activities. After login, everything lives in one host Activity, `HomeActivity`, whose tabs and pushed screens are **Fragments**. That's the one deliberate exception to "every screen is an Activity" (see "Home host" below and `docs/home-host-and-bottom-nav.md`). There is no MVP code left (no Contracts, Presenters or `base/`).

```
com/example/guardband/
├── data/
│   ├── model/            User, EmergencyContact, Alert (+ AlertType) (plain data classes; no passwords)
│   ├── repository/       AuthRepository, ContactRepository (suspend, Result<T>)
│   │                     AlertRepository (Flow<Result<T>>, live reads)
│   │                     InMemory*Repository + InMemoryStore (current implementation)
│   ├── DeviceConstants.kt      DEFAULT_DEVICE_ID = "guardband-001" (until pairing exists)
│   └── RepositoryProvider.kt   manual wiring; swap implementations here
├── ui/
│   ├── splash/         Splash → Home if signed in, else Login (2 s countdown)
│   ├── login/
│   ├── signup/         Name → Location → Contacts
│   ├── forgot/         Request → Verify → NewPass → Success
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

Data lives in `InMemoryStore`, a process-local store with seed login `alex@guardband.com` / `password123`, reset code `123456`, four seeded alerts for `guardband-001` and a simulated 1.2 s latency. Nothing is persisted and nothing talks to Firebase yet. The session dies with the process.

`ui/dashboard/` and `res/layout/activity_dashboard.xml` are gone, replaced by `ui/home/`. Two leftovers from them are still waiting to be removed: the `label_dashboard_*`/`label_your_contacts` strings and the CardView dependency, which now has no user at all.

Conventions (copy `ui/login/*` as the reference):
- **State:** one `XUiState` data class per screen, held in a `MutableStateFlow` and exposed as a `StateFlow`. Update it only with `_uiState.update { it.copy(...) }`.
- **Events:** one-shot events (navigate, toast) go in a sealed `XEvent` sent through `Channel(Channel.BUFFERED)` and exposed with `receiveAsFlow()`. Never encode navigation as state. A screen with no state of its own (Splash, Loading, Sign-up Name/Location) has events only.
- **ViewModels** take repositories through the constructor and hold no `Context` or View. They expose `companion object { val Factory = viewModelFactory { initializer { XViewModel(RepositoryProvider.…) } } }`, and Activities use `by viewModels { XViewModel.Factory }`; plain `by viewModels()` is fine when there are no dependencies. User-facing messages are `MSG_*` constants in the ViewModel.
- **Activities** bind views with `findViewById`, forward raw input and clicks, render state, and execute events (Toast, `startActivity`, `finish`). They collect each flow in its own `lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`. Building `Intent`s stays in the Activity.
- **Wizard data** moves between screens as Intent extras. The Activity passes the extras into the ViewModel's click handler, and the navigation event carries the data back.
- **Exceptions:** `ForgotSuccessActivity` and `NotificationsFragment` have no ViewModel. Each is a static screen whose only action is navigation.

### Home host (Fragments)

- **No Navigation Component.** `HomeActivity` drives a `BottomNavigationView` (`menu_home_bottom_nav.xml`) with a manual `FragmentManager`: each Fragment is added once by tag, then shown or hidden, so tab state survives switches. Don't add `replace()`, a back stack, or a nav graph.
- **Tabs:** Track (default), Contacts, Alert, Profile. **Pushed screens:** Settings (gear) and Notifications (bell), both opened from Track's top bar. While one is showing, the bar stays visible with no tab checked, and Back (or the screen's back arrow) returns to the last tab. Back on a tab exits.
- **Auth gate (two checks):** `SplashViewModel` routes on `isLoggedIn()`, and `HomeViewModel` re-checks in `init` and emits `NavigateToLogin`. The second check covers Android restoring `HomeActivity` after process death, when the in-memory session is gone. Settings and Notifications are inside the host, so they're covered too.
- **Fragments follow the Activity conventions,** with these differences:
  - Use `Fragment(R.layout.x)`, and bind views in `onViewCreated` with `view.findViewById`.
  - Collect flows with `viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(STARTED) { … } }`.
  - Get the screen's own ViewModel with `by viewModels { XViewModel.Factory }`.
  - Host navigation goes through the shared `HomeViewModel` via `by activityViewModels { HomeViewModel.Factory }` (e.g. Track's gear calls `homeViewModel.onSettingsClicked()`). The Activity executes the resulting `HomeEvent`; Fragments never cast `requireActivity()` to `HomeActivity`.
  - A pushed screen's back arrow calls `requireActivity().onBackPressedDispatcher.onBackPressed()`.
- **Lists** use `ListAdapter` + `DiffUtil` with `findViewById` in the ViewHolder (`ContactAdapter`, `AlertHistoryAdapter`).

Declared dependencies: AppCompat, Material, ConstraintLayout, CardView (now unused; pending removal), activity-ktx, fragment-ktx 1.8.2, RecyclerView 1.3.2, lifecycle-viewmodel-ktx and lifecycle-runtime-ktx 2.8.7, kotlinx-coroutines-android 1.9.0, Firebase Analytics and Firebase Database. Firebase Database is declared but no code uses it yet. README also lists Firebase Auth, OkHttp and Credentials/googleid, but those are **not** declared.

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

## Gotchas

- `README.md` is UTF-16 encoded. Plain `cat`/`grep` will show it with spaces between characters, so use `iconv -f UTF-16 -t UTF-8 README.md` to read it.
- Layouts use plain `findViewById` with snake_case IDs prefixed by screen (`et_login_email`, `btn_login`). There is no ViewBinding or Compose.
- Every Activity is locked to portrait in the manifest. New Activities must be added there.

## Working agreements

- Work happens in scoped passes: discovery (read-only) first, then decisions, then a scoped implementation, then build confirmation.
- Pass prompts contain explicit "do NOT touch" lists. Follow them, and put anything ambiguous in the report instead of improvising.
- The agent never runs builds, Gradle or the emulator; the developer does.
- No state-changing git commands (commit, checkout, merge, reset, push, …) unless explicitly asked. Delete or move files with filesystem operations, not `git rm`/`git mv`.
- Never port MVP code into MVVM branches, or the reverse.
