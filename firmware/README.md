# Band firmware (prototype)

`guardband_band/` is an Arduino sketch for the ESP32 prototype: three LEDs and
one button, sending alerts to Firebase Realtime Database over **Wi-Fi**. It
does what the app's debug Mock Sender does, on real hardware. The laptop
supplies the GPS position and battery level over Serial until the NEO-6M and
a battery gauge are wired in. The Air780E (4G and SMS) is not used yet.

`SCHEMA.md` is the authority for the payload. The Mock Sender
(`app/src/debug/.../ui/mocksender/`) is the reference for every timing and
rule. If any of the three changes, change the others to match.

## Setup

1. **Arduino IDE 2** with the **esp32 by Espressif** boards package (built
   written for 3.3.12). No extra libraries are needed.
2. Copy `guardband_band/secrets.example.h` to `guardband_band/secrets.h` and
   put in your Wi-Fi name and password (**2.4 GHz only**). `secrets.h` is
   gitignored, so it stays on your machine.
3. In the IDE: **File → Open → `firmware/guardband_band/guardband_band.ino`**,
   board **ESP32 Dev Module** (or your board), then pick the port and Upload.
4. Open the **Serial Monitor** at **115200** baud. Any line ending works; a
   command with none runs after 0.2 s.

The folder and the `.ino` must keep the same name, or the IDE will want to
move the file.

## Wiring

| Part | GPIO | Other leg |
|---|---|---|
| Red LED (through a resistor) | 25 | GND |
| Yellow LED (through a resistor) | 26 | GND |
| Green LED (through a resistor) | 27 | GND |
| Button | 33 | GND (internal pull-up, pressed reads LOW) |

## What the band does

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

## LEDs

| LED | Steady | Blinking |
|---|---|---|
| Red | 3 s: panic pressed | Fast: a send failed (stops at the next good send) |
| Yellow | Button held | Slow: tracking |
| Green | 1.5 s: check-in pressed. Short flash at boot: Wi-Fi OK | |

## Serial commands (laptop → band)

| Command | Effect |
|---|---|
| `GPS,10.3157,123.8854` or `GPS,10.3157,123.8854,8.5` | Sets the fix (the last number is accuracy in metres, default 10) |
| `NOFIX` | Forgets the fix |
| `BATT,85` | Battery percent, used by every alert from then on |
| `STATUS` | Wi-Fi, clock, fix, battery, panic or tracking state |

### The laptop's own position

`tools/laptop-gps.ps1` sends this laptop's Windows location to the band as a
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

## How sending works

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
- **Writes are unauthenticated** and rely on the open `devices/` rules
  (`RTDBS-RULES.md`). Pairing will change that.
- **`setInsecure()`** skips the TLS certificate check. That is fine for a
  prototype and not for a real band.
- **No coordinates on Serial.** `STATUS` only says whether there is a fix.

## Wi-Fi log

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

## Checking an alert end to end

1. Open the Serial Monitor and wait for `GuardBand ready as guardband-001`.
   `STATUS` should read `Wi-Fi connected, clock synced`.
2. Optionally type `GPS,<your lat>,<your lng>`.
3. Double-press. The Serial Monitor should show `PUT /devices/guardband-001/history/N -> 200`,
   then the same for `/latest`, then `CHECKIN sent as #N`.
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
