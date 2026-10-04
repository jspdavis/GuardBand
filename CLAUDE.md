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

## Architecture (current tree)

The code is partway through an MVP → MVVM migration, so both patterns exist side by side:

- **MVP layer (wired up, runs).** These screens are Activities: `ui/splash`, `ui/auth` (Login plus the 3-step sign-up Name → Location → Contacts), `ui/forgot` (Request → Verify → NewPass → Success), `ui/loading`, and `ui/dashboard/DashboardActivity`. All of them are registered in `AndroidManifest.xml`. Each screen pairs a `*Contract` interface (View + Presenter) with a presenter that extends `base/BasePresenter<V : BaseView>`. The Activity calls `attachView(this)` in `onCreate` and `detachView()` in `onDestroy`. Presenters call `data/MockRepository`, an in-memory singleton that runs callbacks on the main thread after a 1.2 s delay. The seed login is `alex@guardband.com` / `password123`, and the reset code is `123456`. Sign-up data moves between steps as Intent extras.
- **MVVM layer (not wired up).** `ui/login`, `ui/signup`, `ui/forgotpass`, `ui/changepass`, `ui/dashboard/*ViewModel`, and `ui/splash/SplashViewModel` use `ViewModel` + `StateFlow<*UiState>` + `viewModelScope`. They call `suspend` extensions in `data/repository/*Ext.kt`, which wrap callback-style repository methods with `suspendCancellableCoroutine`. No Activity or Fragment uses these ViewModels yet.
- **Placeholder stubs.** Many files contain only an empty class, for example `AuthRepository`, `UserRepository`, `core/auth/*`, `core/navigation/*`, `core/ui/*`, `data/remote/*`, `data/model/User.kt`, `utils/*`, and most Fragments under `ui/home`, `ui/profile`, `ui/settings`, `ui/notifications`, and `ui/main`. The `*Ext.kt` files and the ViewModels call methods and types (`AuthRepository.login(...)`, `ContactRepository`, `EmergencyContact`) that don't exist yet, so expect compile errors in that layer until the stubs are filled in.
- **Legacy.** `ui/main/MainActivity` is the old launcher. It is still in the manifest but not exported, and it is the only place that touches `FirebaseDatabase` directly.

Dependencies actually declared are AppCompat, Material, ConstraintLayout, CardView, Firebase Analytics, and Firebase Database. Lifecycle and coroutines come in transitively. README also lists Firebase Auth, OkHttp, and Credentials/googleid as planned libraries, but they are **not** in `build.gradle.kts`.

## Firebase data contract

The band (or the mock sender) PUTs to the RTDB REST API, and Security Rules validate the payloads:

```
/devices/{deviceId}/latest                  → most recent alert
/devices/{deviceId}/history/{sequenceId}    → append-only history
```

The alert payload (`schemaVersion: 1`) has these fields: `deviceId`, `type` (`PANIC | CHECKIN | LOW_BATTERY | TRACKING_UPDATE`), `timestamp` (ISO 8601 UTC), `location { lat, lng }`, `battery`, and `sequenceId`. README says to check the `location` field names against the deployed Security Rules before relying on them.

## Mock sender

For demos without hardware, Phone A runs `MockSenderActivity`, which sends OkHttp PUTs to the RTDB endpoints, and Phone B runs the receiver screens. `MockSenderActivity` is intentionally *not* MVVM because it is a throwaway harness, so don't refactor it into the architecture. It doesn't exist in the current tree yet.

## Gotchas

- `README.md` is UTF-16 encoded. Plain `cat`/`grep` will show it with spaces between characters, so use `iconv -f UTF-16 -t UTF-8 README.md` to read it.
- Layouts use plain `findViewById` with snake_case IDs prefixed by screen (`et_login_email`, `btn_login`). There is no ViewBinding or Compose.
- Every Activity is locked to portrait in the manifest. New Activities must be added there.
