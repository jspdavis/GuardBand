# GuardBand demo day checklist

Presenting the ESP32 band prototype, the laptop feeding it a position, and
the app on the phone. Written for 28 Oct 2026. How the firmware works:
[`../firmware/README.md`](../firmware/README.md).

**The band's LEDs**

| LED | Steady | Blinking |
|---|---|---|
| Red | Panic pressed (3 s) | Fast: a send failed |
| Yellow | Button held | Slow: tracking after a panic |
| Green | Check-in pressed. One short flash at boot: Wi-Fi OK | |

## Before you leave

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

## At the venue

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

## The demo run

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

## Keep off the projector

- **The `secrets.h` tab in the Arduino IDE.** The IDE opens every file in
  the sketch folder as a tab, so the Wi-Fi password is one click away.
- **The Firebase Console's `devices/` data,** if you would rather not show
  raw coordinates. The app shows place names instead.

## Likely questions

**Does it text the emergency contacts?**
Not yet (FR-05). It needs the Air780E modem, and pairing so the band can get
the contacts.

**Where is the GPS?**
The NEO-6M is not wired in yet. The laptop's Windows location stands in for
it over USB. It is about 100–150 m accurate, estimated from nearby Wi-Fi.

**Is it secure?**
Not yet, and we know where. The band's data in Firebase is open to anyone
until pairing exists, and the prototype skips the server's certificate check.
Both are listed as next steps.

**Why every 5 s, when FR-09 says 60 s?**
A demo pace, so a path appears within a minute. It is one constant in the
sketch (`TRACKING_INTERVAL_MS`).

**What happens if the connection drops?**
The band retries each alert up to 5 times, 5 s apart, and the red LED blinks
fast until a send succeeds again.
