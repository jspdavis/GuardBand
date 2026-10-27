# Home Host and Bottom Navigation

How the GuardBand app is structured after login: one host Activity, three bottom-nav tabs, three pushed screens, and the auth gate around them.

> **2026-10-19: the Alert tab is gone.** The bar is **Track, Contacts, Profile** (icon-only). Track shows the newest alerts in a draggable bottom sheet over the map, and its **See all** opens **Alert history**, a pushed screen holding the whole list. Both read one host-scoped `AlertViewModel` ([alert-data.md](alert-data.md) §8). Sections below that describe the four-tab layout are kept for the record and marked where they changed.

| | |
|---|---|
| **Branch** | `mvvm-main` |
| **Status** | Implemented 2026-10-04 in the working tree, not yet built or committed. The theme pass (checkpoint green + Poppins) was already in place. |
| **Related** | [`CLAUDE.md`](../CLAUDE.md) (conventions), [`SCHEMA.md`](../SCHEMA.md) (alert payload), [`README.md`](../README.md) |

---

## 1. What changed

Before this pass, a successful login ended on `DashboardActivity`, a single scrolling screen with a welcome line, a status card, the contacts list and Log Out.

Now a successful login ends on **`HomeActivity`**, a host with a bottom navigation bar:

| Old Dashboard content | New home |
|---|---|
| Welcome header | **Profile** tab |
| Emergency contacts list | **Contacts** tab (now with delete) |
| "You are protected." status card | Status pill on the **Track** tab |
| Log Out button | **Settings** screen (gear icon on Track) |

New content:
- the **Track** tab with its top bar and map controls
- the **Alert** tab, showing the latest status and incident history
- the **Notifications** screen (bell icon on Track)

The Dashboard → Home rename touched every caller:

| Old | New |
|---|---|
| `LoadingActivity.DEST_DASHBOARD` (`"dest_dashboard"`) | `DEST_HOME` (`"dest_home"`) |
| `LoadingEvent.NavigateToDashboard` | `NavigateToHome` |
| `LoginEvent.NavigateToLoadingDashboard` | `NavigateToLoadingHome` |
| `SignUpContactsEvent.NavigateToLoadingDashboard` | `NavigateToLoadingHome` |
| Manifest entry `.ui.dashboard.DashboardActivity` | `.ui.home.HomeActivity` |

> **Pending cleanup.** The old `ui/dashboard/` package (4 files) and `res/layout/activity_dashboard.xml` are still on disk. They're dead: nothing starts `DashboardActivity`, and it is no longer in the manifest. They still compile, so they're harmless. Delete them along with three things only they use:
> - the `label_dashboard_welcome`, `label_dashboard_status` and `label_your_contacts` strings
> - the `androidx.cardview:cardview` dependency (marked with a TODO in `app/build.gradle.kts`)

---

## 2. Navigation map

```
SplashActivity ──(signed in)──────────────────────────────┐
     │                                                     │
     └──(signed out)──► LoginActivity ──► LoadingActivity ─┤
                          │   ▲                            │
           SignUp (3) ────┘   │                            ▼
           Forgot (4)         │              ┌──────── HomeActivity ────────┐
                              │              │                              │
                              │              │  [Track] [Contacts] [Profile]   ◄─ bottom nav
                              │              │     │                        │
                              │              │     ├─ gear ─► Settings      │  pushed: bar
                              │              │     ├─ bell ─► Notifications │  hidden
                              │              │     └─ See all ─► Alert history
                              │              └──────────────────────────────┘
                              │                     │
                              └──── Log Out (Settings), or no session (HomeViewModel)
```

Back behavior inside Home:

| Where you are | Back does |
|---|---|
| Any tab | Leaves the app. Home was started with `CLEAR_TASK`, so nothing is under it. |
| Settings, Notifications or Alert history | Returns to the tab you came from (always Track for Alert history, the only place See all is). The on-screen back arrow does the same. |

The bar is hidden on a pushed screen (since 2026-10-13), so Back and the back arrow are the only ways out of one.

---

## 3. Decisions and why

| Decision | Reason |
|---|---|
| **One host Activity + Fragments** for the post-login screens | The bar stays fixed with no flicker, each tab keeps its state, and it's the standard Material pattern. One Activity per tab would re-render the bar, duplicate the nav wiring and fight the back stack. This is the one deliberate exception to "every screen is an Activity". |
| **No Navigation Component** | Three tabs and three pushed screens need about 40 lines of manual `FragmentManager` code, with no extra dependency or graph XML. Its main benefit, a separate back stack per tab, isn't needed for this design. |
| **Show/hide, not `replace()`** | `replace()` (the checkpoint's approach) destroys the tab on every switch, losing scroll position and reloading data. With show/hide each Fragment, and its ViewModel, lives as long as the host. |
| **Settings and Notifications are Fragments in the host**, not Activities | The Figma keeps the bottom bar visible on them. Being inside the host also means the host's auth check covers them. *(Alert history, added 2026-10-19, is one too, for the second reason: it has to sit behind the same verification, consent and deletion gates as the tabs.)* |
| **Two-step auth gate** (Splash + HomeViewModel) | Splash routes signed-in users straight to Home. The in-memory session dies with the process, but Android can restore `HomeActivity` directly after process death, skipping Splash. A fresh `HomeViewModel` re-checks and sends the user to Login. |
| **In-memory alert data first** | It keeps this pass focused on navigation. Firebase gets its own pass, and swapping it in is a one-line change in `RepositoryProvider`. |
| **Check-in pill and map buttons are UI only** | FR-10 defines check-in as a double-press on the band; the app doesn't send one. The live map isn't built yet. Their taps show an explanatory toast. *(Since 2026-10-14 the pill is a read-only status of the latest check-in, with no tap: see CLAUDE.md, "Check-in status".)* |
| **Device id is a constant** (`guardband-001`) behind the repository | ViewModels never see it, so user↔band pairing can replace it later without UI changes. |

---

## 4. How `HomeActivity` hosts the screens

File: `app/src/main/java/com/example/guardband/ui/home/HomeActivity.kt`
Layout: `res/layout/activity_home.xml`. It contains a `FrameLayout` (`fragment_container_home`) above a 1dp divider and the `BottomNavigationView` (`bottom_nav_home`).

### Destinations

Every destination is a Fragment identified by a tag:

| Tag | Fragment | Kind | Menu item |
|---|---|---|---|
| `home_track` | `TrackFragment` | tab (default) | `nav_track` |
| `home_contacts` | `ContactsFragment` | tab | `nav_contacts` |
| `home_profile` | `ProfileFragment` | tab | `nav_profile` |
| `home_settings` | `SettingsFragment` | pushed | (none) |
| `home_notifications` | `NotificationsFragment` | pushed | (none) |
| `home_alert_history` | `AlertHistoryFragment` | pushed | (none) |

The tags live in `ui/home/HomeDestinations`, a pure object, so the restore rule below is a JVM test (`HomeDestinationsTest`). `home_alert` / `AlertFragment` / `nav_alert` were the Alert tab until 2026-10-19.

### `show(tag)`

All navigation goes through one function, `show(tag)`, which runs a single transaction:
1. It hides every visible Fragment except the target.
2. It shows the target if it exists, or **creates and adds it on first use**. Creation is lazy, but the alerts listener is no longer tied to it: the host-scoped `AlertViewModel` starts with Track, the default tab, because Track's panel shows the newest alerts. Alert history reuses that instance, so opening it starts no second read.

It then updates three pieces of host state:
- `currentTag`, and `lastTabTag` if the target is a tab. For a tab it also checks that tab's bar item, so the bar can never be left lighting an item it could not restore.
- The `OnBackPressedCallback`, which is enabled only while a pushed screen is showing.
- The bar's visibility: the bar and its divider are shown on a tab and hidden on a pushed screen, so Settings and Notifications can only be left with Back. The bar's selection is never touched, so returning still shows the last tab highlighted. (Until 2026-10-13 the bar stayed visible with no tab checked, through `menu.setGroupCheckable`.)

### Listeners

- `setOnItemSelectedListener` → `show(tag for item)`.
- No reselect listener: it only existed for "in Settings, tap the tab I came from", and the bar is now hidden there.

### State restore

`currentTag` and `lastTabTag` are saved in `onSaveInstanceState`. On recreation the `FragmentManager` restores the Fragments and their hidden flags itself, and `show(currentTag)` runs once **the gate opens** to re-apply the bar and Back state; until then the gate's cover hides everything, so a restore never skips the verification, consent or deletion check.

The saved tags are read back through `HomeDestinations.restoreCurrent` / `restoreLastTab`: a tag the host does not have (the removed Alert tab's `home_alert`, or nothing) is **Track**, and `lastTabTag` is only ever a tab. A process death on Alert history comes back on Alert history, with Back to Track.

### Host-level events

`HomeViewModel` emits these events, and the Activity executes them:

| Event | Emitted when | Activity does |
|---|---|---|
| `NavigateToLogin` | `init` finds no session | Starts `LoginActivity` with `NEW_TASK | CLEAR_TASK` |
| `ShowSettings` | Track's gear is tapped | `show(home_settings)` |
| `ShowNotifications` | Track's bell is tapped | `show(home_notifications)` |
| `ShowAlertHistory` | See all on Track's recent-alerts panel | `show(home_alert_history)` |

Fragments reach `HomeViewModel` with `by activityViewModels { HomeViewModel.Factory }`. They never cast `requireActivity()` to `HomeActivity`.

---

## 5. Screens

Legend: ✅ works with (in-memory) data · 🟡 placeholder UI

### Track (default tab), `ui/track/`
- *(Removed 2026-10-19: Track has no avatar. Its top bar is the gear top-left and the bell top-right, as on Contacts and Profile.)* ✅ **Avatar:** the default profile icon only; the signed-in user's name (falls back to "User") is its TalkBack description, not visible text. Google's profile photo is a later change. *The location line was removed with the `location` field — see below.*
- ✅ **Bell** → Notifications, **gear** → Settings (through `HomeViewModel`).
- 🟡 **Map:** grey `surface_muted` area with a "Live map coming soon" label.
- 🟡 **Status pill:** static "You are protected." with a green dot. It isn't driven by alert data yet.
- 🟡 **Round map buttons** (layers, recenter): toast "The live map isn't available yet." *(Since 2026-10-19 the round arrow is **Navigate**: tap opens maps on the band, long-press re-centres and resumes following. The white Navigate pill is gone. Layers is still a stub.)*
- ✅ **Check-in pill:** read-only status of the latest check-in ("Last check-in: 5 minutes ago" / "No recent check-ins"); not tappable. Was a toast stub until 2026-10-14.
- ✅ **Recent alerts sheet (2026-10-19):** a draggable bottom sheet over the map (`BottomSheetBehavior`, not hideable) with a drag handle, **See all** → Alert history, and the newest three alerts (`item_alert` rows, same as Alert history). Collapsed it shows only the handle and the header (96dp); dragged up, the three rows. Its own spinner, error + Try again, and empty text ("No alerts from your band yet."). The pill and the round controls sit just above its collapsed height. Bound by `RecentAlertsPanel`; rows come from the host-scoped `AlertViewModel`.

`TrackViewModel` holds `TrackUiState(userName)` and `TrackEvent.ShowMessage`. It no longer takes a `UserProfileRepository`.

### Contacts, `ui/contacts/`
- ✅ **List:** a `RecyclerView` with `ContactAdapter` (`ListAdapter` + `DiffUtil` keyed by `id`) and `item_contact.xml` rows: avatar, name, relationship, phone and a delete icon.
- ✅ **Delete:** a `MaterialAlertDialog` confirms, then `ContactsViewModel.onDeleteConfirmed(id)` calls `ContactRepository.deleteContact`. A "Contact deleted." toast follows.
- ✅ Shows a spinner while loading or deleting, and an empty-state text when there are no contacts.
- Not built yet: **add** and **edit** (the rest of FR-06).

### Alert history (pushed), `ui/alert/` (the Alert tab until 2026-10-19)
- **Path:** Track → **See all** on the recent-alerts panel. Back or the back arrow returns to Track; the bar is hidden while it shows. The bell still opens Notifications.
- `AlertHistoryFragment` + `fragment_alert_history.xml`: the old tab's screen with a white top bar (back arrow + "Alert history") in place of the gear and bell. Everything below it is unchanged.

The Alert tab, as first built:
- ✅ **Current status:** a card with the latest alert's type (colored dot + label), local time, coordinates and battery (with "charging" if set).
- ✅ **History (FR-08):** every alert, newest first, in the same card style.
- ✅ **Live:** both lists update whenever the repository emits. A failed read keeps the last good data and shows a toast.
- The latest card is an `<include>` of `item_alert.xml`, bound with the same `AlertHistoryAdapter.AlertViewHolder` the list uses.

> **Superseded.** The Alert tab now reads Firebase, hides `TRACKING_UPDATE`, derives the card from history, and has an error/retry state. The latest card is its own block, not an `<include>`. See **[alert-data.md](alert-data.md)**. The type-colour and timestamp notes below are still accurate.

Type colors:

| Type | Label | Color token |
|---|---|---|
| `PANIC` | Panic | `alert_panic` `#D32F2F` |
| `CHECKIN` | Check-in | `alert_checkin` `#4E8B38` |
| `LOW_BATTERY` | Low battery | `alert_low_battery` `#F57C00` |
| `TRACKING_UPDATE` | Location update | `alert_tracking` `#1976D2` |
| anything else | Unknown alert | `text_hint` |

Timestamps go through `AlertFormatting.formatTimestamp`. It parses ISO 8601 UTC with or without milliseconds: SCHEMA.md's example has none, and the debug mock sender writes them. The result is shown in local time as "Oct 4, 4:45 PM". A value it can't parse is shown raw. `SimpleDateFormat` is used because `java.time` needs API 26 and minSdk is 24.

### Profile, `ui/profile/`
- ✅ Avatar, "Welcome back, *name*" and email. Read-only.
- The rest of the tab's content is **not yet defined**. (An inert "Account information" card sat at the bottom until 2026-10-14; it was removed, so that screen has one entry point, in Settings.)

### Settings (pushed), `ui/settings/`
- White background, the same as the rows and the top bar (back arrow + "Settings"), so the rows sit on the screen rather than in a raised card (2026-10-19; it was grey `surface_muted` with a shadowed card). Contacts, Profile, Notifications and Alert history went white the same day.
- **Every top bar** (Settings, Notifications, Alert history, Account information, Changelog) has a 1dp `divider` line along its bottom edge, from the shared `@drawable/bg_top_bar`, so a white bar stays distinct on a white screen. Change the line there, not per layout.
- ✅ **Account information** opens `ui/account/AccountActivity` (below).
- 🟡 **Placeholder row:** Device information shows "Coming soon." (A "Privacy & security" placeholder was removed on 2026-10-19. Change password became a real screen on 2026-10-22 and moved into Account information on 2026-10-27.)
- ✅ **Display** opens `ui/display/DisplayActivity`: the theme, Use phone setting / Light / Dark (2026-10-20). See CLAUDE.md "Dark mode".
- ✅ **Alert notifications** opens `ui/notifications/AlertNotificationsActivity`, which holds the switch (2026-10-20).
- ✅ **Changelog** opens `ChangelogActivity`; **About** opens `ui/about/AboutActivity`: the version, and the **Privacy Notice** row, which opens the hosted page (it was a Settings row until 2026-10-20).
- **A chevron means another screen of the app.** Every Settings row has one; a row that opens a web or system page (About's Privacy Notice, the battery row) uses `Widget.GuardBand.SettingsRow.Action`, which has none.
- ✅ **Log Out** (outlined button) signs out and goes to Login with the task cleared.

### Account information, `ui/account/` (an Activity, opened from Settings)
- **Path:** Track (or any tab) → gear → Settings → **Account information**. Back returns to Settings. Also opened by the verification gate's "Wrong email? Delete this account".
- **Before any tab:** `HomeViewModel` sends an unverified password account to `ui/verify/VerifyRequiredActivity` (task cleared) instead of the tabs. See [`email-verification.md`](email-verification.md) §8.
- An **Activity**, not a pushed Fragment, for the Changelog's reason: the host's Back always returns to a tab, and from here it has to return to Settings. Being outside the host also puts it outside `HomeViewModel`'s gate, so `AccountViewModel` sends a session-less restore to Login itself.
- ✅ **Details card:** name and email, from the session.
- ✅ **Danger zone** at the very bottom: a red heading, a red-outlined card saying what deleting removes, and the **Delete account** row. Below it, the failure card with Retry and the progress spinner. Everything the flow had in Settings came with it unchanged: the confirmation modal (focus on Cancel), re-authentication, the retry guards and the Back block while it runs. See CLAUDE.md, "Account deletion".

### Notifications (pushed), `ui/notifications/`
- 🟡 Static empty inbox: "No notifications yet."
- It has **no ViewModel**, like `ResetSuccessActivity`, the other static navigation-only screen. Add one when the inbox gets data.
- **System notifications are separate** (2026-10-19): Settings' **Alert notifications** switch, the alert watch service in the same package, and a tapped notification opening Alert history (or Settings, from the watch's own notification) through the Home gate (`HomeLaunchTarget`). The inbox still shows nothing. See [`notifications.md`](notifications.md).

---

## 6. Data layer

> **Partly superseded.** The `Alert` model below is still accurate. Everything about `AlertRepository`, `InMemoryAlertRepository` and the alert wiring describes the in-memory version that **[alert-data.md](alert-data.md)** replaced. Contacts and the rest of this section are unaffected.

### `Alert` model, `data/model/Alert.kt`

It mirrors SCHEMA.md v1.0 field for field, adapted from the checkpoint's `Alert.kt`:

```kotlin
data class Alert(
    val schemaVersion: String = "",   // "1.0" (a string)
    val deviceId: String = "",
    val type: String = "",            // raw wire value
    val timestamp: String = "",       // ISO 8601 UTC
    val location: Location? = null,   // lat, lng, accuracyMeters
    val battery: Battery? = null,     // percent, isCharging
    val sequenceId: Long = 0
) { fun alertType(): AlertType? }     // null for an unknown type
```

Changes from the checkpoint version:
- **`alertType()` returns null for unknown values.** The checkpoint silently treated unknown values as `PANIC`.
- **It's a function, not a property,** so a Firebase mapper won't treat it as a field.
- **Display formatting moved to the UI** (`AlertFormatting`).

> **Two `AlertType`s:** the debug-only mock sender has its own `ui.mocksender.AlertType`. Both compile, but check the import when you're in `src/debug`.

### `AlertRepository`, `data/repository/AlertRepository.kt`

```kotlin
interface AlertRepository {
    fun observeLatestAlert(): Flow<Result<Alert?>>        // /devices/{id}/latest
    fun observeAlertHistory(): Flow<Result<List<Alert>>>  // /devices/{id}/history, newest first
}
```

- **Live reads use `Flow`,** unlike the `suspend` one-shot style of `AuthRepository`/`ContactRepository`. The Firebase implementation will wrap a `ValueEventListener` in `callbackFlow`.
- **Errors are emitted as `Result.failure`** with a user-facing message, not swallowed into `null` or an empty list as the checkpoint did.
- **There's no device id parameter.** The implementation is constructed with one.

### `InMemoryAlertRepository` and the seed data

The implementation waits the usual 1.2 s, then follows `InMemoryStore.alerts` (a `StateFlow`) filtered to its device. That makes it genuinely live: anything added to the store appears on the Alert tab. The four seed alerts are for `guardband-001` at Cebu City (10.3157, 123.8854):

| seq | type | timestamp (UTC) | battery |
|---|---|---|---|
| 1 | PANIC | 2026-10-03 13:05:12 | 64% |
| 2 | TRACKING_UPDATE | 2026-10-03 13:06:12 | 64% |
| 3 | CHECKIN | 2026-10-04 02:30:00 | 41% |
| 4 | LOW_BATTERY | 2026-10-04 08:45:00 | 20% ← latest |

### Contacts

`ContactRepository` gained `suspend fun deleteContact(contactId: String): Result<Unit>`. It fails with "That contact no longer exists." for an unknown id. `InMemoryStore.removeContact` backs it. Contacts are still one global list, not per user.

### Wiring, `data/RepositoryProvider.kt`

```kotlin
val alertRepository: AlertRepository by lazy {
    InMemoryAlertRepository(DeviceConstants.DEFAULT_DEVICE_ID)
}
```

`DeviceConstants.DEFAULT_DEVICE_ID = "guardband-001"` should be read only here.

---

## 7. Fragment conventions

These follow the Activity conventions in `CLAUDE.md` (UiState in a `StateFlow`, one-shot events in a `Channel`, `MSG_*` constants, `Factory` in a companion). Fragment-specific rules:

```kotlin
class ExampleFragment : Fragment(R.layout.fragment_example) {

    private lateinit var tvTitle: TextView

    private val viewModel: ExampleViewModel by viewModels { ExampleViewModel.Factory }
    // Only if the screen triggers host navigation:
    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvTitle = view.findViewById(R.id.tv_example_title)
        view.findViewById<Button>(R.id.btn_example).setOnClickListener { viewModel.onClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }
}
```

- **Collect with `viewLifecycleOwner`,** never the Fragment's own lifecycle.
- **Hiding doesn't pause.** Show/hide doesn't change a Fragment's lifecycle, so hidden tabs keep collecting. That's what keeps Track's alerts panel live while another tab is showing.
- **A ViewModel two screens share is `activityViewModels`.** `AlertViewModel` is the one case: Track's panel and Alert history both use `by activityViewModels { AlertViewModel.Factory }`, so there is one instance and one listener for both.
- **Toasts and `startActivity`** run in the Fragment (`requireContext()`). Fragment transactions run only in `HomeActivity`.
- **Pushed screens' back arrow** calls `requireActivity().onBackPressedDispatcher.onBackPressed()`, so the host decides where Back goes.
- **Ids are snake_case, prefixed by screen:** `iv_track_avatar`, `rv_contacts`. List-row ids use `item_`: `tv_item_contact_name`.
- **Lists:** `ListAdapter` + `DiffUtil` with a stable key, `findViewById` in the ViewHolder, and `app:layoutManager` set in XML.

---

## 8. How to add a destination

**A new tab:**
1. Add an `<item>` to `res/menu/menu_home_bottom_nav.xml` with an icon and a `tab_*` string.
2. Add a tag constant in `HomeDestinations` and put it in `TABS`; add it to `HomeActivity`'s `createFragment()`, `tagFor()` and `menuItemIdFor()`.
3. Create the package `ui/<name>/` with `<Name>Fragment`, `<Name>ViewModel`, `<Name>UiState`, `<Name>Event`, plus `fragment_<name>.xml`.

**A new pushed screen** (like Settings):
1. Add a tag constant in `HomeDestinations` and put it in `PUSHED` (not `TABS`); add a `createFragment()` branch.
2. Add a `HomeEvent.Show<Name>` and an `on<Name>Clicked()` in `HomeViewModel`, and handle it in `HomeActivity.handleEvent`.
3. Give the screen a back arrow that calls the back dispatcher.

**A screen that should cover the bottom bar** (e.g. a full-screen editor): make it a separate Activity started from the Fragment, and add it to the manifest (portrait). It must re-check the session like `HomeViewModel` does.

---

## 9. Resources added

| Kind | Items |
|---|---|
| Menu | `menu_home_bottom_nav.xml` (`nav_track`, `nav_contacts`, `nav_profile`; `nav_alert` removed 2026-10-19) |
| Icons (Material, `?attr/colorControlNormal` tint) | `ic_map` (Track tab), `ic_phone` (Contacts, since 2026-10-19; `ic_group` before, still used by sign-up's consent screen), `ic_warning` (was the Alert tab; Settings still uses it), `ic_profile` (Profile, avatars; from the checkpoint), `ic_settings`, `ic_notifications`, `ic_arrow_back` (from the checkpoint's `back_arrow`), `ic_delete`, `ic_navigation`, `ic_layers`, `ic_check_circle`, `ic_chevron_right` |
| Shapes | `bg_pill.xml` (white pill with a hairline border). `bg_status_dot.xml` was removed on 2026-10-19, when the alert rows' dots became symbols (`alert-data.md`, D1) |
| Color selector | `color/bottom_nav_item.xml`: green when checked, `text_primary` otherwise |
| Color tokens | `alert_panic`, `alert_checkin`, `alert_low_battery`, `alert_tracking` |
| Styles | `Widget.GuardBand.BottomNavigation`: white, no M3 indicator pill, tint selector, always labeled. `Widget.GuardBand.SettingsRow` / `SettingsDivider`. `ShapeAppearance.GuardBand.Circle` (from the theme pass) is now used for every avatar and the round map buttons. |
| Strings | `tab_*`, plus per-screen `label_track_*`, `label_contacts_*`, `label_alert_*`, `label_profile_*`, `label_settings_*`, `label_notifications_*`, `cd_*` content descriptions and dialog strings |
| Dependencies | `androidx.fragment:fragment-ktx:1.8.2`, `androidx.recyclerview:recyclerview:1.3.2` (in `gradle/libs.versions.toml`) |

---

## 10. Notes for the Firebase pass — done

**This pass has landed; see [alert-data.md](alert-data.md).** The notes are kept for the record, with what actually happened:

- The `isCharging` mapper trap was **avoided**, not worked around: `AlertParser` reads the raw wire key, so Firebase's object mapper is never involved.
- The history-ordering note below is **wrong** and was corrected. RTDB sorts integer-like keys numerically, so `orderByKey().limitToLast(n)` is correct and needs no index. Sorting by the `sequenceId` field in memory is still done, as a guard.
- Errors are **not** `Exception("<user-facing text>")`. They are typed `AlertError`s mapped by `DatabaseError` code, with the wording held in the ViewModel.
- The swap really was one line in `RepositoryProvider`, but the ViewModel did change — D1, D2 and D3 live there.

Original notes:

- **`isCharging` mapping:** Firebase's object mapper turns Kotlin's `isCharging` getter into the key `charging`. Annotate the field with `@get:PropertyName("isCharging") @set:PropertyName("isCharging")`, or map the snapshot by hand. Otherwise battery charging always reads `false`.
- **`.get().await()`** needs `kotlinx-coroutines-play-services`, which isn't declared. Alternatively, use `callbackFlow` only, which the observe methods need anyway.
- **History ordering:** RTDB keys are strings, so sort by the `sequenceId` field, not by key.
- **Errors:** emit `Result.failure(Exception("<user-facing text>"))` from `onCancelled`. Don't swallow them.
- **Security Rules:** the app must be allowed to *read* `/devices/{id}/latest` and `/history`. The rules on `mvp-checkpoint-local-backup` are world-writable and must not be deployed as-is.
- **Swap:** change one line in `RepositoryProvider`. No ViewModel changes are needed.

---

## 11. Known limitations

- Placeholders: the map, the Check-in pill, the map buttons, all four Settings rows and the Notifications inbox.
- The Track status pill is static. Deriving it from the latest alert (e.g. "Low battery · 20%") is a natural next step.
- Contacts can't be added or edited from Home yet (FR-06).
- ~~Everything is in memory~~ — no longer true. Auth, profile, contacts and alerts are all on Firebase.
- Fragments keep `lateinit` view references. That's safe here because hidden tab views are never destroyed while the host lives, but don't reuse the pattern with `replace()`.
- The Figma wasn't available while building, so spacing and copy on Track, Settings and Notifications are approximations to check against it.

---

## 12. Manual test checklist

Run `gradlew.bat assembleDebug`, then on a device:

**Auth gate**
- [✓] Cold start → Splash → Login (no session yet).
- [✓] Log in with `alex@guardband.com` / `password123` → Loading → Home on the **Track** tab.
- [✓] Back on any tab leaves the app.
- [ ] (Optional) With Home open, run `adb shell am kill com.example.guardband` while the app is in the background, then reopen it from Recents. Expect it to land on Login.

**Tabs**
- [✓] Every tab switches instantly. Its label and icon turn green, and the others are dark.
- [✓] Contacts: scroll, switch tabs and come back. The list is still there and doesn't reload.
- [✓] Alert: a spinner shows for about 1 s, then "Low battery · Battery 20%" as current status and 4 history cards, newest first, with colored dots. *(Superseded: there is no Alert tab. See the 2026-10-19 block below.)*

**Track**
- [✓] The chip shows "Alex Rivera" / "San Francisco, CA".
- [✓] Check-in, layers and recenter each show their toast.

**Pushed screens**
- [✓] Gear → Settings. The bar stays visible with **no** tab green.
- [✓] Back arrow → Track, highlighted again.
- [✓] Gear → Settings, then the system Back → Track.
- [✓] Gear → Settings, then tap **Track** in the bar → Track.
- [✓] Gear → Settings, then tap **Alert** → Alert. *(Superseded: no Alert tab, and the bar is hidden on Settings.)*
- [✓] Bell → Notifications → empty state. Back → Track.
- [✓] Settings rows show "Coming soon."

**Contacts delete**
- [✓] Delete → the dialog names the contact. Cancel keeps it.
- [✓] Delete → Delete → spinner, then the row disappears with a "Contact deleted." toast.
- [✓] Delete every contact → "No emergency contacts added yet."

**Log Out**
- [✓] Settings → Log Out → Login.
- [✓] Back from Login doesn't return to Home.

**Three tabs and Alert history (2026-10-19)**
- [ ] The bar shows exactly three icons: Track, Contacts, Profile. Each opens its screen; TalkBack reads each icon's name.
- [ ] Track shows the recent-alerts sheet collapsed low over the bottom of the map: just the handle and the "Recent alerts · See all" header. Drag it up: at most three rows, newest first. Drag it down, or tap the handle: back to collapsed. It cannot be dragged off screen.
- [ ] Track's top bar is the gear top-left and the bell top-right, in the same places as on Contacts and Profile. There is no avatar.
- [ ] The Contacts icon in the bar is a phone.
- [ ] With a fix, tapping the round arrow opens a maps app on the band; long-pressing it re-centres the map after you have dragged it away. Without a fix it is greyed out. There is no white Navigate pill.
- [ ] **See all** opens Alert history with the bar hidden. Back, and the back arrow, return to Track with the bar back.
- [ ] The bell still opens Notifications.
- [ ] On Alert history, background the app and kill it (`adb shell am kill com.example.guardband`), then reopen from Recents: the gate's spinner covers the screen, then Alert history is back; Back → Track.

**Sign-up path**
- [✓] Sign up a new user → Loading → Home. The chip and Profile show the new name, and Contacts includes the new contact.
