# The Device tab

The third tab of the Home host, in place of the Profile tab since
2026-10-27. It shows the band (its status, battery, last alert and last
known position), the account connected to it, a placeholder for pairing,
and how the band works. It also replaced Settings' "Device information
(Coming soon)" row, which opened this tab from 2026-10-27 until the row was
removed on 2026-10-28; the bar is the way in.

| | |
|---|---|
| **Branch** | `feature/device-tab`, merged into `mvvm-main` |
| **Code** | `ui/device/` (`DeviceFragment`, `DeviceViewModel`, `DeviceUiState`), `ui/track/BandReport.kt`, `res/layout/fragment_device.xml` |
| **Tests** | `DeviceViewModelTest` (27), `BandReportTest` (16), plus `HomeViewModelTest`, `HomeDestinationsTest`, `AlertViewModelTest` additions |
| **Related** | [home-host-and-bottom-nav.md](home-host-and-bottom-nav.md), [alert-data.md](alert-data.md), CLAUDE.md "Track and map" |

**Pairing is not part of this.** The tab watches the one hard-coded band,
`DeviceConstants.DEFAULT_DEVICE_ID`, like every other screen, and `devices/`
is unchanged. Nothing here writes anything.

---

## 1. What the tab shows

A scrolling column, top to bottom:

| Part | Shows |
|---|---|
| **a) Header** | Avatar (Google photo, else initials, else the person icon), display name, email. What the Profile tab showed, made compact. Account information in Settings still holds the rest. |
| **Band viewer** (2026-10-28) | The band on a turntable: drag to turn it. A still placeholder until the rendered frames exist. Visual only; the tab knows it only as a `BandViewer`. [band-viewer.md](band-viewer.md). |
| **b) Band card** | "GuardBand" and the band's id; a status chip, **"Reporting"** or **"Last seen 5 min ago"**; battery with the alert rows' battery symbol, orange at 20% or below; the last alert (type in its colour and symbol, "5 min ago", place name); the last known position as a place name. **"No data from the band yet"** when the band has never reported. No map, no coordinates. |
| **c) Connected account** | Sign-in method (Email and password, Google, or both); email Verified / Not verified; **"Emergency contacts: N, M starred"**, which switches to the Contacts tab. |
| **d) Pair a band** | Disabled, "Coming soon". |
| **e) How the band works** | Three plain lines: panic, double-press check-in, low battery at 20%. DESIGN: Jul. |

---

## 2. Where each value comes from

**The tab adds no Firebase listener.** It reads two host-scoped ViewModels
that already listen, and makes one one-shot read of its own.

| Value | Source | Notes |
|---|---|---|
| Name, email | The session (`AuthRepository.currentUser()`), then `users/{uid}` (`fetchProfile`, one-shot) | As Profile did: the stored record replaces the session's values; a missing record or a failed read keeps them. |
| Avatar | `BandMarkerFace.of(user, providers)`: `AvatarPhoto.urlFor` + the marker's initials | The same face as the band's marker on Track. Shown by `ui/avatar/AvatarImage`, which gained an initials overlay. Same 288 px photo link, so it shares Coil's cache with the marker. |
| Band id | `RepositoryProvider.watchedBandId` (= `DEFAULT_DEVICE_ID`) | Display only. Which band is read is the repository's wiring, never the ViewModel's (D4). |
| Reporting / Last seen | `BandReport` fed `AlertUiState.newestReport` | See §3. "Now" is `AlertUiState.nowMillis`, which `AlertViewModel` moves on every 30 s, so "Last seen" ages with no ticker in this tab. |
| Battery | `BandReport.battery`, retained across reports without one; orange when `BandReport.isBatteryLow` | Symbol and colour from `AlertFormatting.batteryIcon` / `batteryColor`, the alert rows' own. |
| Last alert | `AlertUiState.latest` (D2: newest non-tracking), `placeNameFor` | Type label, colour and symbol from `AlertFormatting`; time from `AlertFormatting.relativeTime`. No name yet: "Place name not available", never coordinates. |
| Last known position | `BandReport.location` + `AlertUiState.placeNames` | §4. |
| Sign-in method | `AuthRepository.currentUserProviders()` | Both providers read "Email and password, Google". None known: "Unknown". |
| Verification | `EmailVerification.of(providers, isEmailVerified())` | Google accounts are always Verified, as in Account information. Read from the session once, not reloaded: the verification gate keeps unverified password accounts out of the tabs anyway. |
| Contacts count | `ContactsViewModel.uiState` | Every contact, not the search's or the chips' slice. A failed read after a good one keeps the last count. |

**Two ViewModels became, or stayed, host-scoped for this:**

- `AlertViewModel` was already host-scoped. `AlertUiState` gained
  `newestReport`, the newest entry in the window **of any type**, tracking
  updates included. The lists still hide tracking updates (D1).
- `ContactsViewModel` is now host-scoped too (`activityViewModels` in both
  `ContactsFragment` and `DeviceFragment`), so there is still one contacts
  listener. Contacts' behaviour did not change: its Fragment was already
  added once and kept for the host's life.

Both listeners end when the host's `ViewModelStore` is cleared: Log Out,
an account switch, the host finishing. The Fragment never collects either
ViewModel's events; they belong to their own screens.

**Why the history window and not `latest`.** Track's `TrackViewModel`, which
reads `devices/{id}/latest`, is Fragment-scoped, and a second `latest`
subscription would be a new listener. The band writes `history/{seq}` first
and `latest` second, with the same payload, so the window's newest entry is
what `latest` holds, give or take the moment between the two writes. The
Device tab can therefore run a moment **ahead** of Track, never behind.

---

## 3. Band status: one set of rules, `BandReport`

`ui/track/BandReport` is a small immutable data class that both Track and
this tab use. It was lifted out of `TrackViewModel` without changing what
Track does; Track's tests are untouched and pass.

- `after(report)` folds in the band's newest report. **Position and battery
  are retained** across reports without them (Track's D3). A null report
  (the band has never reported) clears everything.
- `isReporting(now)`: reported less than **180 s** ago
  (`BandStatus.ONLINE_THRESHOLD_MS`, three of FR-09's 60 s intervals).
  Exactly 180 s is no longer reporting. A future timestamp counts as
  reporting (the band's clock may run ahead).
- `elapsed(now)`: how long ago, for "Last seen". Null when the timestamp
  could not be read, which reads "Last seen: time unknown", never as fresh.
- `isBatteryLow`: `percent <= AlertFormatting.LOW_BATTERY_PERCENT` (20).
- `locationFromTracking`: whether the retained position came from a
  `TRACKING_UPDATE` (§4).

Track keeps one `BandReport` fed from `latest`; the Device tab keeps its own
fed from the window's newest entry. Same rules, two inputs.

---

## 4. The last known position, and why it is sometimes not named

**Only an alert's position is ever named, and only with the name the alert
screens already looked up.** The Privacy Notice says the geocoder is asked
about "each alert's position… only while you are looking at alerts". A
tracking update's position has never been sent to it, and this tab does not
start doing so: that would be a new data flow, which needs a notice change
and a `CONSENT_VERSION` bump.

| Retained position | Line reads |
|---|---|
| None yet | "No position yet" |
| From a panic, check-in, low-battery or unknown alert, name known | The place name |
| From an alert, no name yet (or none exists) | "Place name not available" |
| From a tracking update | "On the move. See the map on Track." |

The tab makes **no geocoder call of its own**: names come from
`AlertUiState.placeNames`. If notice wording ever covers tracking
positions, this is the one function to change: `DeviceViewModel.positionOf`.

---

## 5. Pairing: Unpaired and Paired

`DeviceUiState.pairing` is a sealed `DevicePairing`:

- `Paired(bandId, band: BandCard)`: **always this today**, with the
  hard-coded band.
- `Unpaired`: unreachable. The Fragment renders it (the band card says
  "Coming soon" and hides its details) so the `when` is exhaustive.

When pairing exists, the ViewModel decides between them from the account's
paired band, the Factory supplies that band's id, and the "Pair a band" row
gets a real action. Nothing on the screen needs reshaping.

---

## 6. Navigation

- **Tab:** `HomeDestinations.DEVICE` (`home_device`), menu item `nav_device`,
  icon `ic_watch` (a placeholder, DESIGN: Jul), label "Device". A saved
  `home_profile` from before the rename restores to Track.
- **No Settings row** (2026-10-28). Settings' Device information row opened
  this tab through `HomeViewModel.onDeviceInfoClicked()` →
  `HomeEvent.ShowDevice` for a day; both were removed with the row. To bring
  it back, send a tab event the way the contacts row below does.
- **Emergency contacts row** calls `HomeViewModel.onContactsSummaryClicked()`
  → `HomeEvent.ShowContacts` → `show(CONTACTS)`, the bar's own mechanism.
- Gear and bell, as on the other tabs.

---

## 7. What it must not do

- Write to `devices/`, or anywhere: no commands, test alerts or
  acknowledgements.
- Add a Firebase listener, or a second subscription to one that exists.
- Show a coordinate, or geocode a tracking update's position (§4).
- Show firmware version, signal strength or GPS-fix quality: none is in
  `SCHEMA.md`.
- Hold wearer medical or identifying details, or a band nickname: new
  personal data, which means a notice change first. (A nickname under
  `users/{uid}` was considered and skipped for that reason. The rules
  would accept the unlisted child, unvalidated, but the notice lists what an
  account stores.)
- Log anything: the state holds an email, a photo link and place names.

---

## 8. Phone checks

With the Mock Sender on a second phone (or the same phone):

- [ ] Fresh install, no band data (empty `history`): the band card reads
      "No data from the band yet", with no chip, battery or alert. Pair a band
      reads "Coming soon" and does nothing when tapped.
- [ ] Send a check-in: within a second or so the chip reads **Reporting**
      (green), the battery shows the slider's level with its symbol, Last
      alert shows a green Check-in, "Just now", and a place name a moment
      later. Last known position shows the same place name.
- [ ] Hold for a panic and let tracking run: Last alert becomes a red Panic;
      the position line reads "On the move. See the map on Track."; the chip
      stays Reporting.
- [ ] Drop the battery slider to 20% or below: a Low battery alert arrives
      and the battery level turns orange with the nearly-empty symbol. At 21%
      or above it is grey again.
- [ ] Stop the sender (cancel the panic, or background it). After three
      minutes the chip changes to **Last seen 3 min ago**, then keeps aging
      ("4 min ago", ...) without any new data.
- [ ] Turn the connection off, then reopen the app cold: the gate's cover
      first, then (if the read fails) "Couldn't load your band's status."
      With data already on screen, losing the connection keeps it.
- [ ] Email account: header shows initials on green; Sign-in method "Email
      and password"; Email "Verified".
- [ ] Google account: header shows the Google photo (initials first, while
      it loads, and if offline); Sign-in method "Google"; Email "Verified".
- [ ] Emergency contacts: N, M starred matches the Contacts tab. Star or add
      a contact in Contacts and return: the count updates. Tap the row: the
      Contacts tab opens with its bar item lit.
- [ ] Settings has no Device information row; Display is followed by
      Changelog with a single divider between them.
- [ ] The bar shows Track, Contacts and Device (a watch icon); TalkBack reads
      "Device".
- [ ] Dark mode: every card, the chip, the initials and the battery colours
      read well; switch the theme with the tab open and it comes back on the
      Device tab.
- [ ] Log Out and sign in as another account: the header, sign-in method
      and contacts count are the new account's, and no old photo shows.
