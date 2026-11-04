# GuardBand

**IoT-Based Personal Safety Band with Manual Emergency Alert and Real-Time Tracking**

GuardBand is a discreet, wrist-worn safety device paired with an Android companion app. The wearer holds a hidden tactile button to signal an emergency. The band captures GPS, texts a live Google Maps link to emergency contacts, and syncs the incident to Firebase, **all over its own cellular connection, with no smartphone needed**. Guardians watching in the app see the alert update in real time.

> Eldroid elective project · Section 4B · Instructor: Roche A. Plando · Team: USC Evictees

**Contents:** [Why GuardBand](#why-guardband) · [Features](#features) · [System overview](#system-overview) · [Branches](#repository-branches) · [Getting started](#getting-started) · [Project structure](#project-structure) · [Band firmware (prototype)](#band-firmware-prototype) · [Demo day checklist](#demo-day-checklist) · [Roadmap](#roadmap) · [Limitations](#limitations) · [Team](#team) · [Documentation](#documentation)

---

## Why GuardBand

Phone-based safety apps and smartwatch SOS features have two weaknesses: they look like safety tools, and they assume the phone is within reach. GuardBand is built around two differentiators:

| Differentiator | What it means |
|---|---|
| **Discretion** | A single-purpose band with a hidden tactile button. Nothing about it reads as a safety device. |
| **Phone independence** | An onboard 4G LTE module handles SMS and data sync, so alerts work even if the phone is left behind. |

## Features

| ID | Feature | Priority |
|---|---|---|
| FR-01 | User authentication (email/password, Google Sign-In) | High |
| FR-02 | Real-time location and incident status display | High |
| FR-03 | Panic button detection within 1 second | High |
| FR-04 | GPS capture within 5–10 seconds of activation | High |
| FR-05 | Automated emergency SMS with Google Maps link | High |
| FR-06 | Emergency contact management (add / edit / remove) | Medium |
| FR-07 | Real-time sync of location and timestamp to Firebase | High |
| FR-08 | Incident history log | Medium |
| FR-09 | Continuous post-alert location tracking (every 60 s for 30 min, or until stopped) | High |
| FR-10 | Discreet safe check-in via double-press (does not trigger SMS) | Medium |
| FR-11 | Low-battery alert to the app at 20% | Medium |

Full requirements, non-functional requirements, and traceability are in the **Functional Requirements Specification (FRS)**.

## System Overview

```
 ┌──────────────────────── Wristband ────────────────────────┐
 │  Hidden button ─► ESP32 ─UART─► NEO-6M GPS                 │
 │                     │                                      │
 │                     └─UART/AT─► Air780E 4G LTE Cat-1       │
 │  Vibration motor + LED (discreet trigger confirmation)     │
 └─────────────────────────┬──────────────────┬───────────────┘
                           │ SMS              │ HTTPS (cellular data)
                           ▼                  ▼
                  Emergency contacts   Firebase Realtime Database
                                              │ Firebase SDK
                                              ▼
                                   GuardBand Android app
                                   (location view · history · contacts)
```

### Hardware

- **Microcontroller:** ESP32 DevKit (38-pin, CP2102 USB-UART, WROOM-32)
- **Cellular:** Air780E 4G LTE Cat-1 (SMS + HTTPS data). Chosen over 2G modules because the Philippine NTC is phasing out 2G/3G by the end of 2026.
- **GPS:** NEO-6M
- **Input/feedback:** hidden tactile button, vibration motor, LED
- **Power:** 3.7 V Li-Po with TP4056 charging module and regulator

> **Note:** The FRS lists a CH9102 USB variant of the ESP32. The board actually in use is the CP2102 variant. The FRS should be reconciled with this.

### Backend

- **Firebase project:** `guardband-aae65` (Realtime Database only, `asia-southeast1`, Spark plan)
- **Auth:** Firebase Authentication with email/password and Google Sign-In (Credential Manager API)
- **Writes:** The wristband (and the mock sender during development) writes directly to the RTDB REST API. Security Rules validate the payloads. No Cloud Functions are used, so no Blaze plan is required.
- **Security Rules:** deployed since 2026-10-11; `RTDBS-RULES.md` is the source of truth. `devices/` stays open to everyone until device pairing exists.
- **Hosting:** `guardband-aae65.web.app` serves the Privacy Notice (`/privacy`), the delete-account page (`/delete-account`) and the App Link file that opens password-reset and verification emails in the app.

```
/devices/{deviceId}/latest              ← most recent alert (PUT)
/devices/{deviceId}/history/{sequenceId} ← append-only history (PUT)
```

**Alert payload schema (locked, v1.0; `SCHEMA.md` is authoritative):**

```json
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC | CHECKIN | LOW_BATTERY | TRACKING_UPDATE",
  "timestamp": "ISO 8601 UTC",
  "location": { "lat": 0.0, "lng": 0.0, "accuracyMeters": 0.0 },
  "battery": { "percent": 0, "isCharging": false },
  "sequenceId": 0
}
```

> Confirm the field names and types against the deployed Security Rules before relying on them.

### Android App

- **Language / platform:** Kotlin 1.9.24, AGP 8.3.2, min SDK API 24 (Android 7.0), compile and target SDK 34
- **Architecture:** MVVM (View ↔ ViewModel ↔ Repository). Sign-in screens are Activities; after login, one host Activity (`HomeActivity`) holds the tabs and pushed screens as Fragments, with a manual `FragmentManager` and no Navigation Component.
- **Key libraries:** AndroidX Lifecycle 2.8.7, Kotlin Coroutines 1.9.0, Firebase BoM 32.8.1 (Auth + Realtime Database), AndroidX Credentials 1.3.0 / googleid 1.1.1 (Google sign-in), osmdroid 6.1.20 (map, drawing MapTiler tiles), Coil 2.7.0 (Google profile photos). OkHttp 4.12.0 is for the debug-only mock sender.
- **What the app does today:** sign-up with recorded consent, email and Google sign-in, email verification, in-app password reset, and account deletion; a live map of the band's last position with alert pins and an Incident view that draws the path after a panic; alert history with place names; opt-in alert notifications; emergency contacts; a Device tab with the band's status and battery; light and dark themes. The full list is in [docs/handoff.md](docs/handoff.md) §1.

## Repository Branches

| Branch | Purpose |
|---|---|
| `mvvm-main` | The real GuardBand build (MVVM). Teammates push here. |
| `main` | Kept on the same commit as `mvvm-main` (fast-forwarded to it, never merged separately) |
| `mvp-checkpoint` | Instructor-assigned MVP checkpoint (strict MVP pattern: Login, Register, Forgot Password, Change Password, Dashboard). Kept separate to avoid contaminating product work. |
| `mvvm-jedd`, `ishi` | Individual teammates' working branches |

## Getting Started

### Prerequisites

- Android Studio (Iguana 2023.2.1 or later)
- JDK matching the project's Gradle configuration
- Access to the `guardband-aae65` Firebase project
- A physical Android device (API 24+) or emulator

### Setup

```bash
git clone <repo-url>
cd <repo-folder>
git checkout mvvm-main
```

1. `app/google-services.json` is in the repo and points at `guardband-aae65`. Don't replace it with a copy from another branch.
2. Add the map key to your own `local.properties` (gitignored): `MAPTILER_API_KEY=…`. Get it from James or Jul in a private message, and never commit it. Without it the app builds, but the Track tab shows "Map unavailable".
3. Register your debug **SHA-1** in the Firebase Console, or Google sign-in fails on your build. For password-reset and verification links to open in the app, your debug **SHA-256** also has to be in `hosting/public/.well-known/assetlinks.json` and Hosting redeployed ([docs/forgot-password.md](docs/forgot-password.md)).
4. Open the project in Android Studio, let Gradle sync, and run on a device or emulator.

### Build and test

On Windows, use `gradlew.bat` (or `./gradlew` from Git Bash), with `JAVA_HOME` pointing at Android Studio's bundled JBR.

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew installDebug         # install on a connected device or emulator
./gradlew testDebugUnitTest    # JVM unit tests (900+)
./gradlew lint
```

### Demo setup (no hardware required)

Until the ESP32/Air780E firmware is ready, a phone stands in for the wristband:

1. **Phone A** runs the **GuardBand Mock Sender** (its own launcher icon in debug builds). It has one band button, with the band's gestures: hold 1.5 s for a panic (then tracking every 5 s for 30 min), double-press for a check-in, hold 5 s during a panic to cancel. A battery slider sends a low-battery alert at 20%. It writes to the RTDB REST endpoints, as the band will.
2. **Phone B** runs the receiver/guardian screens. One phone can also play both ends.
3. Firebase is the only intermediary between them.

> `MockSenderActivity` is a **temporary, disposable test harness** (intentionally not MVVM), in the debug source set only, so release builds don't contain it. It will be removed once the real firmware is integrated. Manual: [docs/mock-sender-and-receiver.md](docs/mock-sender-and-receiver.md).

With an ESP32 to hand, the [band firmware prototype](#band-firmware-prototype) plays the band instead of Phone A.

## Project Structure

> The post-login host is documented in [docs/home-host-and-bottom-nav.md](docs/home-host-and-bottom-nav.md), and the alert read path in [docs/alert-data.md](docs/alert-data.md). `CLAUDE.md` has the full file-by-file map.

```
app/src/main/java/.../guardband/
├── data/
│   ├── model/          # User, EmergencyContact, Alert, CheckIn, ConsentRecord, … (plain data classes)
│   ├── repository/     # Auth, UserProfile, Contact, Alert and Place repositories (Firebase-backed),
│   │                   #   typed errors, AlertParser; InMemory* versions are the unit-test doubles
│   └── RepositoryProvider.kt   # manual wiring
├── ui/
│   ├── splash/ login/ signup/ forgot/ reset/ verify/ consent/ loading/   # Before the tabs (Activities)
│   ├── home/           # Host Activity: bottom nav, auth/verification/consent gates
│   ├── track/          # Track tab (default): MapTiler map, band marker, alert pins, recent-alerts sheet
│   ├── contacts/       # Contacts tab: emergency contacts
│   ├── device/         # Device tab: account, band status and battery, pairing placeholder
│   ├── bandviewer/     # The band on a turntable, at the top of the Device tab
│   ├── alert/          # Alert history (pushed from Track)
│   ├── incident/       # Incident view: a panic and its tracking path
│   ├── notifications/  # Inbox (the bell) and the opt-in alert notifications service
│   ├── settings/ account/ display/ about/ changelog/   # Settings (the gear) and its screens
│   └── auth/ avatar/   # Google sign-in token, profile photo or initials
└── utils/              # InputValidator, PasswordCriteria, HostedPages
app/src/debug/            # Mock Sender (debug builds only)
firmware/guardband_band/  # ESP32 Wi-Fi prototype (Arduino sketch)
firmware/tools/           # laptop-gps.ps1: the laptop's location to the band
hosting/public/           # Privacy Notice, delete-account page, assetlinks.json
docs/                     # Design notes per feature; start with handoff.md
```

## Band firmware (prototype)

`firmware/guardband_band/` is an Arduino sketch for the ESP32 prototype: three LEDs and
one button, sending alerts to Firebase Realtime Database over **Wi-Fi**. It
does what the app's debug Mock Sender does, on real hardware. The laptop
supplies the GPS position and battery level over Serial until the NEO-6M and
a battery gauge are wired in. The Air780E (4G and SMS) is not used yet.

`SCHEMA.md` is the authority for the payload. The Mock Sender
(`app/src/debug/.../ui/mocksender/`) is the reference for every timing and
rule. If any of the three changes, change the others to match.

### Firmware setup

1. **Arduino IDE 2** with the **esp32 by Espressif** boards package (built
   written for 3.3.12). No extra libraries are needed.
2. Copy `firmware/guardband_band/secrets.example.h` to
   `firmware/guardband_band/secrets.h` and put in your Wi-Fi name and password
   (**2.4 GHz only**). `secrets.h` is gitignored, so it stays on your machine.
   For the band to sign in as itself (see "Band identity" below), also add the
   `#define FIREBASE_API_KEY` line from `secrets.example.h`, with the
   `current_key` from `app/google-services.json`. Without it the band works
   as before, unsigned.
3. In the IDE: **File → Open → `firmware/guardband_band/guardband_band.ino`**,
   board **ESP32 Dev Module** (or your board), then pick the port and Upload.
4. Open the **Serial Monitor** at **115200** baud. Any line ending works; a
   command with none runs after 0.2 s.

The folder and the `.ino` must keep the same name, or the IDE will want to
move the file.

### Wiring

| Part | GPIO | Other leg |
|---|---|---|
| Red LED (through a resistor) | 25 | GND |
| Yellow LED (through a resistor) | 26 | GND |
| Green LED (through a resistor) | 27 | GND |
| Button | 33 | GND (internal pull-up, pressed reads LOW) |

### What the band does

| Gesture | Sends |
|---|---|
| Hold 1.5 s | `PANIC` as soon as the hold reaches 1.5 s, without waiting for release. Once it has landed, a `TRACKING_UPDATE` every 5 s for 30 min |
| Two presses within 2 s | `CHECKIN` |
| During a panic, hold 5 s | Cancel: tracking stops and a `CHECKIN` is sent |
| One press | Nothing |
| `BATT,` at or below 20 | `LOW_BATTERY`, once, until the level goes back above 20 |

A check-in does **not** cancel a panic, because a double-press can be forced.
Tracking every 5 s is the Mock Sender's demo pace (FR-09 says 60 s), so a path
shows on the map within a minute. It is `TRACKING_INTERVAL_MS`.

### LEDs

| LED | Steady | Blinking |
|---|---|---|
| Red | 3 s: panic pressed | Fast: a send failed (stops at the next good send) |
| Yellow | Button held | Slow: tracking |
| Green | 1.5 s: check-in pressed. Short flash at boot: Wi-Fi OK | |

### Serial commands (laptop → band)

| Command | Effect |
|---|---|
| `GPS,10.3157,123.8854` or `GPS,10.3157,123.8854,8.5` | Sets the fix (the last number is accuracy in metres, default 10) |
| `NOFIX` | Forgets the fix |
| `BATT,85` | Battery percent, used by every alert from then on |
| `STATUS` | Wi-Fi, clock, fix, battery, panic or tracking state, and the band id |
| `RESETID` | Forgets the band identity and makes a new one: from then on it is a new band |

### Band identity

With `FIREBASE_API_KEY` in `secrets.h`, the band signs in to Firebase as
itself ([docs/device-pairing.md](docs/device-pairing.md) §2, step 2 of
pairing):

- **Once Wi-Fi and the clock are ready, it signs up as a Firebase anonymous
  user.** That user's uid is the band's own id, printed as `Band identity
  created. Band id: …` and shown by `STATUS`. The identity is kept in the
  ESP32's flash, so a reboot or a new upload is the same band; `RESETID`
  makes a new one.
- **Every write carries the sign-in** (`?auth=`), renewed before its hour
  runs out. Each send says `signed in` or `not signed in`.
- **It still writes to `guardband-001`**, which the app reads, and a failed
  sign-in only means that alert goes out unsigned. Set `WRITE_TO_OWN_ID` to
  `true` to write to `devices/<band id>` instead. Under the proposed rules
  only this band can write there, and the app does not read it until a band
  can be paired, so check it in the Firebase Console.
- **Anonymous sign-in must be on** in the Firebase Console (Authentication →
  Sign-in method → Anonymous). If it is off, the band says so on Serial.
- Tokens are never printed. The band id is, because pairing will need it.

### The laptop's own position

`firmware/tools/laptop-gps.ps1` sends this laptop's Windows location to the band as a
`GPS,` line every 5 s, and stands in for the Serial Monitor meanwhile: the
board's output shows in the terminal, and anything you type and Enter goes to
the board. **Close the Arduino Serial Monitor first,** because only one program
can hold the port.

```powershell
powershell -ExecutionPolicy Bypass -File firmware\tools\laptop-gps.ps1            # one COM port: found by itself
powershell -ExecutionPolicy Bypass -File firmware\tools\laptop-gps.ps1 -Port COM9
```

- It needs **Windows PowerShell** (`powershell.exe`), not PowerShell 7, and
  Location turned on in Settings → Privacy & security → Location, including
  "Let desktop apps access your location".
- A laptop usually has no GPS chip, so Windows estimates from nearby Wi-Fi:
  expect tens to a few hundred metres (about 135 m on James's laptop). The
  script passes that on as `accuracyMeters`.
- Opening the port does not reset the board (DTR and RTS stay low), so if
  you start the script after boot you miss the boot messages. Type `STATUS`.
- It never prints the coordinates.

**With no fix**, the band copies the Mock Sender: an alert gets a random point
near Cebu City, and tracking walks a ~165 m loop, so a desk demo still shows
something on the map. Set `SIMULATE_WITHOUT_FIX` to `false` to leave the
location out instead. Do that once real GPS is wired in. Each send says which
it used: `CHECKIN sent as #N (GPS fix)` or `(SIMULATED position: ...)`. The
fix lasts until `NOFIX` or a reset, so send `GPS,` again after every upload.

### How sending works

- **One alert at a time, in order,** from a background task, so the button
  and LEDs keep working while HTTPS calls are in flight.
- **History first, then `latest`:** one `PUT` to
  `/devices/guardband-001/history/{sequenceId}.json`, then one to
  `/latest.json`. If the history write fails, `latest` is not written.
- **The `sequenceId` comes from the server.** Before its first send, the band
  reads the keys already in history (`?shallow=true`) and continues after the
  highest one, so it never overwrites the Mock Sender's rows or those of an
  earlier run. **Don't run the Mock Sender and the band at the same time:**
  both would use the same next id, and the live rules allow the overwrite.
- **A panic, check-in or low-battery alert is tried up to 5 times, 5 s
  apart** (`MAX_ATTEMPTS`), then dropped. A tracking update is tried once,
  because the next one replaces it. A failed send blinks the red LED until
  the next good one, so a dropped alert keeps it blinking. After a dropped
  panic, the next 1.5 s hold sends a new one.
- **Nothing is sent until the clock has synced over NTP.** The timestamp is
  the time of the press, not of the send, so a retried panic keeps its time.
- **Writes are signed in when the band can sign in** (see "Band identity").
  Writes to `guardband-001` still rely on its open rules (`RTDBS-RULES.md`)
  until bands can be paired.
- **Every HTTPS call checks the server's certificate** against Google's four
  root certificates in `gts_roots.h` (valid until 2036). The check needs the
  clock, which is one more reason nothing is sent before NTP has synced.
- **No coordinates on Serial.** `STATUS` only says whether there is a fix.

### Wi-Fi log

Every line about the connection starts with `Wi-Fi:`.

- `connected to "…", IP …, signal -55 dBm`: good. Below about -80 dBm is weak.
- `not connected, reason N (…)`: what the ESP32 says went wrong, with the
  usual cause. Reasons 15 and 202 are almost always the password. 201 means
  the board cannot see the network.
- If the first 15 s fail, the band **scans** and says whether your network is
  visible, and lists what it can see. If your network is not in that list, it
  is probably 5 GHz only, or its name in `secrets.h` is spelled differently.
- `secrets.h still has the placeholder name`: your details went into
  `secrets.example.h` instead of `secrets.h`.

### Checking an alert end to end

1. Open the Serial Monitor and wait for `GuardBand ready as guardband-001`.
   `STATUS` should read `Wi-Fi connected, clock synced`.
2. Optionally type `GPS,<your lat>,<your lng>`.
3. Double-press. The Serial Monitor should show `PUT /devices/guardband-001/history/N -> 200`,
   then the same for `/latest`, then `CHECKIN sent as #N (…, signed in)`
   (`not signed in` without `FIREBASE_API_KEY`, or with anonymous sign-in off).
4. In the Firebase Console (Realtime Database), `devices/guardband-001/history/N`
   should be there. In the app, the Track tab's check-in card should read
   "Last check-in: just now".
5. Hold for 1.5 s: the red LED lights and `PANIC` is sent. Then the yellow LED
   blinks, and a `TRACKING_UPDATE` follows every 5 s. In the app, the panic
   shows in the recent alerts, and its Incident view draws the path.
6. Hold for 5 s: tracking stops, the yellow LED goes dark, and a `CHECKIN` is
   sent.
7. `BATT,15` sends a `LOW_BATTERY`. `BATT,50` then re-arms it.
8. Turn the Wi-Fi off and double-press: the red LED blinks fast, and the
   check-in goes through once the Wi-Fi is back.

## Demo day checklist

For presenting the ESP32 band prototype, the laptop feeding it a position,
and the app on the phone. What the LEDs mean: [LEDs](#leds).

### Before you leave

At home, while there is time to fix things.

- [ ] **Use a phone hotspot for the band.** Venue Wi-Fi often has a sign-in
  page, and the ESP32 cannot get past one. Set the hotspot to 2.4 GHz (on an
  iPhone, turn on Maximize Compatibility).
- [ ] **Put the hotspot in `secrets.h` and upload now.** Edit
  `firmware/guardband_band/secrets.h`, not `secrets.example.h`. After
  uploading, the Serial Monitor should say `Wi-Fi: connected to "…"`. Then
  nothing needs changing at the venue.
- [ ] **Receiver phone ready.** Latest debug build installed; signed in and
  past the email and consent screens; the map shows tiles; mobile data on as
  a fallback.
- [ ] **Every teammate's build shows the map.** "Map unavailable" means the
  build had no MapTiler key. `local.properties` is gitignored, so the key
  never comes with the repo: add `MAPTILER_API_KEY=…` to your own
  `local.properties`, Sync Gradle, rebuild and reinstall. Get the key from
  James or Jul in a private message, and never commit it.
- [ ] **Alert notifications on, if you will show them.** Settings → Alert
  notifications. A panic notification is silent on purpose (one short
  vibration, no pop-up), so say that out loud or it looks broken.
- [ ] **Decide on the old test alerts.** History holds 500+ test entries;
  they show in Alert history and as pins on the map. To start clean, delete
  `devices/guardband-001/history` and `latest` in the Firebase Console. This
  is safe while the band runs; the cost is losing the past alerts.
- [ ] **Backup ready.** The Mock Sender on a second phone, and a screen
  recording of one full run from tonight. Never run the Mock Sender and the
  band at the same time: they would write the same alert number.
- [ ] **Pack the cable and charger.** A spare USB data cable (a charge-only
  one will not work) and the laptop charger. Turn off laptop sleep: the
  laptop powers the board and runs the script.

### At the venue

About 15 minutes before you present.

- [ ] **Hotspot on, board plugged in.** The green LED flashes once when
  Wi-Fi connects.
- [ ] **Windows Location on.** Settings → Privacy & security → Location,
  including "Let desktop apps access your location".
- [ ] **Close the Serial Monitor, then start the GPS script.** Only one
  program can hold the board's port. In Windows PowerShell, from the repo
  folder:

  ```powershell
  powershell -ExecutionPolicy Bypass -File firmware\tools\laptop-gps.ps1
  ```

- [ ] **Type `STATUS`.** It should read
  `Wi-Fi connected, clock synced, GPS fix`.
- [ ] **Dry run: one double-press.** The app's check-in card should say
  "just now".
- [ ] **Check where the pin lands.** Indoors, Windows can be a few hundred
  metres off. To pin your real spot, stop the script (Ctrl+C) and send
  `GPS,lat,lng` from the Serial Monitor. While the script runs, it replaces a
  typed fix within 5 s.

### The demo run

In this order; each step leaves the band ready for the next.

1. [ ] **Check-in: double-press.** Green LED. The app shows
   "Last check-in: just now".
2. [ ] **Panic: hold 1.5 s.** Red LED, and the panic appears at the top of the
   recent-alerts sheet. Then yellow blinks while it tracks. Open the panic's
   Incident view. The laptop is not moving, so tracking stays on one spot
   rather than drawing a path.
3. [ ] **Cancel: hold 5 s.** Yellow stops and a check-in is sent. Do this
   before moving on: otherwise tracking sends every 5 s for 30 minutes.
4. [ ] **Low battery: type `BATT,15`.** The alert shows with an orange
   battery icon. `BATT,80` re-arms it.
5. [ ] **Optional: a failed send.** Turn the hotspot off, then double-press.
   Red blinks fast while the band retries, up to 5 times. Turn the hotspot
   back on within about 50 s or that check-in is dropped.

### Keep off the projector

- **The `secrets.h` tab in the Arduino IDE.** The IDE opens every file in
  the sketch folder as a tab, so the Wi-Fi password is one click away.
- **The Firebase Console's `devices/` data,** if you would rather not show
  raw coordinates. The app shows place names instead.

### Likely questions

**Does it text the emergency contacts?**
Not yet (FR-05). It needs the Air780E modem, and pairing so the band can get
the contacts.

**Where is the GPS?**
The NEO-6M is not wired in yet. The laptop's Windows location stands in for
it over USB. It is about 100–150 m accurate, estimated from nearby Wi-Fi.

**Is it secure?**
Not yet, and we know where. The band now signs in to Firebase as itself and
checks the server's certificate, but the shared band id the demo uses
(`guardband-001`) stays open to anyone until bands can be paired to
accounts, which is the next step.

**Why every 5 s, when FR-09 says 60 s?**
A demo pace, so a path appears within a minute. It is one constant in the
sketch (`TRACKING_INTERVAL_MS`).

**What happens if the connection drops?**
The band retries each alert up to 5 times, 5 s apart, and the red LED blinks
fast until a send succeeds again.

## Roadmap

| Window | Milestone |
|---|---|
| Aug – ~Oct 25 | Mobile app development (auth, navigation, MVVM, Figma UI, receiver screens) |
| Oct 26 – Nov 27 | IoT firmware (ESP32 + Air780E + NEO-6M) |
| Early Dec | Integration, final defense, and submission (Dec 11–13 target) |

**Progress (29 Oct 2026):** the app side of nearly every requirement is built. What is missing is the band itself, and the link between a band and an account (pairing).

- [x] Restructure to MVVM
- [x] Secure app navigation (auth, email-verification and consent gates)
- [x] Apply the Figma redesign (placeholders marked `DESIGN: Jul` still await a design pass)
- [x] Mock sender writes PUT-based RTDB REST calls, with the band's one-button gestures
- [x] Receiver screens: live map, alert history, Incident view, notifications
- [x] Deploy Firebase Security Rules (2026-10-11)
- [x] Wi-Fi band prototype on the ESP32 (2026-10-28)
- [ ] Wire in the NEO-6M GPS and a real battery reading
- [ ] Air780E 4G and SMS to emergency contacts (FR-05)
- [ ] Device pairing (closes `devices/` and gets the contacts onto the band)
- [ ] Update the Use Case Diagram to cover FR-09 to FR-11
- [ ] Swap `MockSenderActivity` for real firmware once hardware arrives

## Limitations

- Manual activation only (no fall or impact detection)
- Requires cellular coverage for SMS and data
- GPS accuracy is about 5–10 m (consumer-grade module)
- Single predefined contact set; no location-based prioritization
- Roughly 24–48 h standby; not water-resistant or ruggedized
- Android only (no iOS)
- Until pairing exists, every install watches the one band `guardband-001`
- The map uses MapTiler's free, non-commercial plan; a real release needs a paid one
- Supplements, and does not replace, professional emergency services

## Team

| Member | Role |
|---|---|
| Julian Miguel T. Roldan | Project Leader · System Architect · Android Developer |
| Jedd Shendel P. Davis | Cloud & Database Lead |
| Jehan Ishi Cabellon | Mobile App Developer (UI/UX & frontend) |
| Roxette Anne G. Samarinta | Documentation Lead |

## Documentation

- Functional Requirements Specification (FRS)
- System Architecture Diagram
- Use Case Diagram
- Gantt Chart
- [Handoff](docs/handoff.md) (current state, open items, and traps — start here if you are picking the project up)
- [`SCHEMA.md`](SCHEMA.md) (the alert payload) and [`RTDBS-RULES.md`](RTDBS-RULES.md) (the Security Rules; [docs/database-rules.md](docs/database-rules.md) explains them)
- [Home host and bottom navigation](docs/home-host-and-bottom-nav.md) (app architecture after login)
- [Alert data](docs/alert-data.md) (how the band's alerts are read from Realtime Database) and the [Incident view](docs/incident-view.md)
- [Map](docs/osmdroid.md) (osmdroid, MapTiler, attribution), [Device tab](docs/device-tab.md) and [band viewer](docs/band-viewer.md)
- [Alert notifications](docs/notifications.md)
- [Sign-up](docs/signup-flow.md), [forgot password](docs/forgot-password.md), [email verification](docs/email-verification.md), and [privacy and consent](docs/privacy-and-consent.md)
- [Mock sender and receiver](docs/mock-sender-and-receiver.md) (demos without the band)

---

*GuardBand is an academic prototype built for controlled testing and demonstration.*
