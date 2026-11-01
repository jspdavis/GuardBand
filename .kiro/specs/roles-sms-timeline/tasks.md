# Tasks: Roles, Panic SMS & Alert Timeline

Implementation order is by dependency. Each task should leave the build green.
Status: `[ ]` pending · `[~]` in progress · `[x]` done.

## Phase 1 — Foundational models (additive, unblock everything)

- [x] **T1 — Role model.** New `data/model/Role.kt` enum (`GUARDIAN`, `DEPENDENT`)
  with `fromWire`/`toWire` like `SignInProvider`. Add `val role: Role? = null` to
  `data/model/User.kt`. Unit-test `fromWire`/`toWire`.
- [x] **T2 — Alert status + simulated.** New `data/model/AlertStatus.kt` enum
  (`ACTIVE`, `ACKNOWLEDGED`, `RESOLVED`) with `fromWire`, default `ACTIVE`. Add
  `val status: AlertStatus = AlertStatus.ACTIVE` and `val simulated: Boolean =
  false` to `data/model/Alert.kt`. `AlertParser.parse` reads optional
  `map["status"]` and `map["simulated"]` (defaults ACTIVE / false). Unit-test
  parser defaults + the new fields.
- [x] **T3 — "Older" history heading.** `ui/alert/AlertDays.kt`: add
  `AlertDay.Older`; `group()` emits one `Header(Older)` when the day drops below
  Yesterday and never emits another day header after; `dayOf()` returns `Older`
  for pre-yesterday. `AlertFormatting.dayLabel` → new `label_alert_day_older`
  ("Older") string. Update existing `AlertDaysTest` for the new grouping.

## Phase 2 — Data layer / repositories (ripples to tests; keep green)

- [x] **T4 — Profile write/read for role + phone.** `UserProfileRepository`
  interface + `FirebaseUserProfileRepository`: `finalizeSignUp` gains `role:
  Role` and `ownPhone: String`; adds `"users/{uid}/role"` and `"users/{uid}/phone"`
  to the atomic update map. `fetchProfile` reads `role` and `phone` onto `User`.
  Update `SignUpConsentViewModel` callers + fakes + `FirebaseUserProfileRepositoryTest`.
- [x] **T5 — Contact self-phone validation.** `ContactFields.validated` gains
  `ownPhone: String? = null`; after E.164 normalization, if `phone == ownPhone`
  throw new `ContactError.SelfPhone`. Update all `validated(...)` call sites
  (`SignUpContactsViewModel`, `FirebaseContactRepository.addContact/updateContact`,
  tests). New string `error_contact_phone_is_self`.
- [ ] **T6 — Alert status repository.** `AlertRepository` interface +
  `FirebaseAlertRepository`: `observeStatuses()` reads `devices/{id}/status`;
  `acknowledge(seq)` / `resolve(seq)` write `devices/{id}/status/{seq}` =
  `{ status, updatedAt: ServerValue.TIMESTAMP, updatedBy: uid }`.
  `InMemoryAlertRepository` gets the same for tests. Status merged into
  `AlertRow` by `AlertViewModel` (Phase 4).

## Phase 3 — §4 Timeline UI (builds on T2/T3/T6)

- [ ] **T7 — Status chip + guardian actions.** `item_alert.xml`: add a status
  chip. `AlertCardViews.bind` renders `AlertRow.status`. `AlertViewModel.render`
  overlays statuses from `observeStatuses()`; exposes `onAcknowledge(seq)` /
  `onResolve(seq)` gated to the guardian (via `HomeUiState.role`). `AlertUiState`
  carries status on `AlertRow`.
- [ ] **T8 — Older heading UI verified.** Build + run; confirm Today / Yesterday
  / Older render (T3 did the logic; this is the visual check + any string
  plumbing in `AlertFormatting`).

## Phase 4 — §2 Phone validation UI (builds on T4/T5)

- [ ] **T9 — Own-phone capture.** `SignUpAccountActivity` gains an own-phone
  field validated with `InputValidator.normalizePhoneToE164`; the normalized
  value flows forward to `finalizeSignUp`. Google complete-profile path captures
  own-phone before Contacts. New strings for the field + validation errors.
- [ ] **T10 — Self-phone error in UI.** `SignUpContactsViewModel` and the
  Contacts tab add/edit pass the signed-in user's own phone into
  `ContactFields.validated`; `ContactError.SelfPhone` maps to
  `error_contact_phone_is_self`. Tests for the blocked save.

## Phase 5 — §1 Roles wizard + dashboards

- [ ] **T11 — Role-selection wizard step.** New `SignUpRoleActivity` (+layout
  +viewmodel) with mutual-alignment guidance; passes `Role` forward to Consent.
  Google complete-profile path shows the same role selection before Contacts.
- [ ] **T12 — Gate role resolution.** `HomeViewModel`: after profile + consent,
  read `role`; if null emit new `HomeEvent.RequireCompleteProfile` (route to
  wizard complete-profile). Expose `role` on `HomeUiState`.
- [ ] **T13 — Role-aware dashboard host.** `HomeDestinations.tabsFor(role)` +
  `menu_home_bottom_nav_guardian.xml` / `..._dependent.xml`. `HomeActivity`
  `createFragment`/nav selection by `HomeViewModel.uiState.role`.
- [ ] **T14 — DependentStatusFragment.** Guardian's "Dependent" tab reusing
  `DeviceFragment` band-status rendering.
- [ ] **T15 — Dependent panic-test trigger.** Debug-only (`BuildConfig.DEBUG`)
  control on the Dependent Device tab; reuses `AlertSender.sendAlert(PANIC)`
  send path but tags the payload `simulated: true` (so it can never send an SMS).

## Phase 6 — §3 Panic SMS (builds on T1/T2)

- [ ] **T16 — SmsPanicSender.** New `ui/notifications/SmsPanicSender.kt` wrapping
  `SmsManager.sendTextMessage`; `sendPanicSms(toPhone, lat, lng, sessionId)`.
  New `sms_panic_body` string. Unit-test body formatting (Robolectric).
- [ ] **T17 — AlertWatchService panic branch.** Role-gated (`role == DEPENDENT`
  only) + `!alert.simulated` guard; `cachedRole()` from profile; writes the
  session node `devices/{id}/sessions/{panicSeq}`; resolves the guardian phone
  from the dependent's `isEmergency` contact. Tests with fake role provider.
- [ ] **T18 — SEND_SMS permission.** Add `SEND_SMS` to
  `app/src/main/AndroidManifest.xml`; runtime request (mirror the existing
  `POST_NOTIFICATIONS` flow) with rationale; skip SMS if denied.
- [ ] **T19 — Privacy Notice.** Update the in-app privacy-notice strings +
  `docs/`/handoff notice to disclose SMS PII (coordinates + tracking link from
  the dependent's phone) in the same change (REQ-SMS-08).

## Phase 7 — Rules + final verification

- [ ] **T20 — Security rules docs.** Update `RTDBS-RULES.md` +
  `docs/database-rules.md` with `role` / `phone` validate rules and
  `sessions` / `status` (`auth != null`) rules. No `$other:false` at `users/$uid`.
- [ ] **T21 — Test sweep.** New unit tests for Role, AlertStatus, AlertDays
  Older, ContactFields self-phone, SmsPanicSender body, AlertWatchService
  role-gating; update fakes for the changed signatures. Existing tests stay green.
- [ ] **T22 — Build green.** `:app:assembleDebug` + `:app:testDebugUnitTest`
  clean; fix every error introduced.

## Notes

- All four areas are approved; tasks are listed in dependency order, not
  priority. Start at T1 and proceed sequentially; later phases depend on earlier
  model/repo changes.
- Keep the build green after every task — do not batch breaking changes across
  the signature changes in T4/T5/T6 without updating their call sites + fakes.
- The `simulated` flag (T2/T15/T17) is the release-build SMS safety check (D6);
  keep that guard in the SMS path even while other tasks are in flight.
