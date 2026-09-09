# Implementation Plan

- [x] 1. Write bug condition exploration tests (before any fix)
  - **Property 1: Bug Condition** - Login Identifier, Blank Credentials, Live Password Rules, Empty Contacts
  - **CRITICAL**: These tests MUST FAIL on unfixed code — failure confirms the bugs exist
  - **DO NOT attempt to fix the code when tests fail**
  - **GOAL**: Surface counterexamples that demonstrate each bug exists
  - Create `LoginPresenterTest.kt` in `app/src/test/java/com/example/guardband/ui/login/`
    - Use a mock `LoginContract.View` and stub `AuthRepository`
    - Test A1: call `onLoginClicked("+123", "Secret1!")` → assert `showError("Invalid phone number format.")` called; assert `authRepository.login()` NOT called (Bug 1 — malformed phone)
    - Test A2: call `onLoginClicked("abc", "Secret1!")` → assert `showError("Please enter a valid phone number or email.")` called; assert `authRepository.login()` NOT called (Bug 1 — neither phone nor email)
    - Test A3: call `onLoginClicked("notanemail@", "Secret1!")` → assert `showError("Invalid email format.")` called; assert `authRepository.login()` NOT called (Bug 1 — malformed email)
  - Create `SignUpPresenterTest.kt` in `app/src/test/java/com/example/guardband/ui/signup/`
    - Use a mock `SignUpContract.View` and stub `AuthRepository` / `ContactRepository`
    - Test B1: call `onNameContinue("Alice", "Smith", "", "")` → assert `showFieldError("email", ...)` called; assert `goToStep` (step change) NOT triggered (Bug 2 — blank credential bypass)
    - Test B2: call `onNameContinue("Alice", "Smith", "", "Secret1!")` → assert `showFieldError("email", ...)` called (Bug 2 — blank email alone)
    - Test B3: call `onNameContinue("Alice", "Smith", "alice@example.com", "")` → assert `showFieldError("password", ...)` called (Bug 2 — blank password alone)
    - Test B4: set step=1 then call `onSkip()` → assert step remains 1 / no navigation (Bug 2 — Skip button bypass)
    - Test C1: call `onPasswordChanged("Hello")` on unfixed code — expect compilation error or no-op confirming method is absent (Bug 3 — no live rules)
  - Create `SignUpContactsFragmentTest.kt` (instrumented or Robolectric) OR document that CONTINUE-with-zero-contacts must be observed by running the app with no contacts saved and tapping CONTINUE
    - Test D1: simulate adapter empty + CONTINUE tap → assert `onContactsContinue()` NOT called; assert error visible (Contacts bug)
  - Run all exploration tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (confirms bugs exist); document each counterexample
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [-] 2. Write preservation property tests (before any fix)
  - **Property 2: Preservation** - Valid Login Inputs, Sign-Up Non-Bug Paths, Non-Empty Contacts
  - **IMPORTANT**: Follow observation-first methodology — run unfixed code with non-buggy inputs first
  - **GOAL**: Capture baseline behavior; tests must PASS on unfixed code
  - In `LoginPresenterTest.kt`:
    - Observe: `onLoginClicked("user@example.com", "Secret1!")` calls `authRepository.login(...)` on unfixed code
    - Observe: `onLoginClicked("+639171234567", "Secret1!")` calls `authRepository.login(...)` on unfixed code
    - Observe: `onLoginClicked("09171234567", "Secret1!")` calls `authRepository.login(...)` on unfixed code
    - Write property-based test (Kotest `forAll` or manual loop): for all valid emails matching `Patterns.EMAIL_ADDRESS`, assert `authRepository.login()` is called with the exact identifier
    - Write property-based test: for all valid phones matching `\+?[0-9]{7,15}`, assert `authRepository.login()` is called
    - Write preservation unit test: `onLoginClicked("", "Secret1!")` → `showError("Phone number or email is required.")` (existing blank-id path)
    - Write preservation unit test: `onLoginClicked("user@example.com", "")` → `showError("Password is required.")` (existing blank-pw path)
  - In `SignUpPresenterTest.kt`:
    - Observe: `onNameContinue("", "Smith", "a@b.com", "Secret1!")` → `showFieldError("firstName", ...)` on unfixed code
    - Observe: `onNameContinue("Alice", "", "a@b.com", "Secret1!")` → `showFieldError("lastName", ...)` on unfixed code
    - Observe: full valid submission `onNameContinue("Alice", "Smith", "alice@example.com", "Secret1!")` triggers `registerWithEmail` on unfixed code
    - Write preservation tests capturing all three observations above
    - Write preservation test: `onLocationContinue("")` → `showFieldError("location", ...)` unchanged
    - Write preservation test: `onLocationContinue("Manila")` → calls `updateProfile` then `goToStep(3)` unchanged
    - Write preservation test: `onDeleteContact` removes contact from list; `onAddContact` adds contact — unchanged
  - In `SignUpContactsFragmentTest.kt`:
    - Observe: adapter non-empty + CONTINUE tap → `onContactsContinue()` called on unfixed code
    - Write preservation test capturing that behavior
  - Run all preservation tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

- [ ] 3. Group A — Fix Bug 1: LoginPresenter identifier format validation

  - [~] 3.1 Add format-detection helpers to `LoginPresenter.kt`
    - Add private helper `isPhoneLike(s: String): Boolean` — returns true when `s.startsWith("+")` or `s.all { it.isDigit() }`
    - Add private helper `isValidPhone(s: String): Boolean` — returns true when `s.matches(Regex("\\+?[0-9]{7,15}"))`
    - Add private helper `isEmailLike(s: String): Boolean` — returns true when `s.contains("@")`
    - Place helpers after the existing override methods in `LoginPresenter`
    - _Bug_Condition: isBugCondition_Bug1(identifier, password) — non-blank identifier that is malformed phone, malformed email, or neither phone-like nor email-like_
    - _Requirements: 2.1, 2.2, 2.3_

  - [~] 3.2 Replace the guard block in `onLoginClicked` with ordered validation
    - Keep existing blank-identifier guard first: `showError("Phone number or email is required.")`
    - Keep existing blank-password guard second: `showError("Password is required.")`
    - Add guard 3: if `isPhoneLike(identifier)` and NOT `isValidPhone(identifier)` → `showError("Invalid phone number format.")` return
    - Add guard 4: if `isEmailLike(identifier)` and NOT `Patterns.EMAIL_ADDRESS.matches(identifier)` → `showError("Invalid email format.")` return
    - Add guard 5: if neither `isPhoneLike` nor `isEmailLike` → `showError("Please enter a valid phone number or email.")` return
    - All passing inputs continue to `view?.showLoading(); authRepository.login(identifier, password, ...)` unchanged
    - _Expected_Behavior: showError with descriptive message; authRepository.login NOT called for any bug-condition input_
    - _Preservation: valid emails and valid phones still reach authRepository.login() unmodified; blank-id and blank-pw errors fire first in order_
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4_

  - [~] 3.3 Verify Bug 1 exploration test now passes (Property 1: Expected Behavior)
    - **Property 1: Expected Behavior** - Login Identifier Format Validation
    - **IMPORTANT**: Re-run the SAME tests from task 1 targeting Bug 1 (tests A1, A2, A3) — do NOT write new tests
    - Run tests A1, A2, A3 from `LoginPresenterTest.kt`
    - **EXPECTED OUTCOME**: Tests PASS (confirms Bug 1 is fixed)
    - _Requirements: 2.1, 2.2, 2.3_

  - [~] 3.4 Verify preservation tests still pass (Property 2: Preservation)
    - **Property 2: Preservation** - Valid Login Inputs Reach Repository
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 targeting login — do NOT write new tests
    - **EXPECTED OUTCOME**: All preservation tests PASS (confirms no regressions in login flow)

- [ ] 4. Group B — Fix Bug 2: SignUpPresenter blank-credential bypass + Skip navigation

  - [~] 4.1 Remove the blank-credential bypass in `SignUpPresenter.onNameContinue`
    - Remove the `else → goToStep(2)` branch entirely
    - Change the outer condition from `if (trimmedEmail.isNotBlank() || trimmedPassword.isNotBlank())` to always validate credentials
    - Add guard: if `trimmedEmail.isBlank()` → `showFieldError("email", "Email or phone is required.")` return
    - Add email/phone format validation using the same isPhoneLike/isValidPhone/isEmailLike pattern (can inline or import helpers)
    - Add guard: if `trimmedPassword.isBlank()` → `showFieldError("password", "Password is required.")` return
    - Replace `if (trimmedPassword.length < 8)` check with full `ValidationResult.evaluatePassword(trimmedPassword, trimmedPassword).passwordRules.allMet` check
    - _Bug_Condition: isBugCondition_Bug2 — firstName and lastName non-blank; email blank OR password blank_
    - _Expected_Behavior: showFieldError for each blank credential field; goToStep NOT called_
    - _Preservation: blank firstName/lastName errors still fire first; valid full submission still calls registerWithEmail_
    - _Requirements: 2.4, 2.5, 3.5, 3.6_

  - [~] 4.2 Disable Skip navigation from Step 1 in `SignUpPresenter.onSkip()`
    - Change the `1 -> goToStep(2)` case in `onSkip()` to a no-op (do not navigate)
    - Steps 2 and 3 skip behavior (`2 -> goToStep(3)`, `else -> onContactsContinue()`) remain unchanged
    - _Bug_Condition: isBugCondition_Bug2 — Skip button tapped while step == 1 bypasses credential validation_
    - _Requirements: 2.4, 2.5_

  - [~] 4.3 Hide the Skip button in `SignUpWizardActivity.onCreate`
    - Add `findViewById<TextView>(R.id.tvSkip).visibility = View.GONE` in `onCreate` after existing setup
    - This removes the UI entry point to `onSkip()` from Step 1
    - _Requirements: 2.4_

  - [~] 4.4 Verify Bug 2 exploration tests now pass (Property 1: Expected Behavior)
    - **Property 1: Expected Behavior** - Sign-Up Step 1 Rejects Blank Credentials
    - **IMPORTANT**: Re-run tests B1, B2, B3, B4 from task 1 — do NOT write new tests
    - **EXPECTED OUTCOME**: Tests PASS (confirms Bug 2 is fixed)
    - _Requirements: 2.4, 2.5_

  - [~] 4.5 Verify preservation tests still pass (Property 2: Preservation)
    - **Property 2: Preservation** - Sign-Up Non-Bug Paths Unchanged
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 targeting sign-up — do NOT write new tests
    - **EXPECTED OUTCOME**: All preservation tests PASS (confirms no regressions in sign-up wizard)

- [ ] 5. Group C — Fix Bug 3: Live password rules on Sign-Up Step 1

  - [~] 5.1 Add `onPasswordChanged` to `SignUpContract`
    - Add `fun onPasswordChanged(password: String)` to the `Presenter` interface
    - Add `fun updatePasswordRules(rules: ValidationResult.PasswordRules, hasTyped: Boolean)` to the `View` interface
    - Add `import com.example.guardband.data.model.ValidationResult` to `SignUpContract.kt`
    - _Requirements: 2.6, 2.7_

  - [~] 5.2 Implement `onPasswordChanged` in `SignUpPresenter`
    - Add `private var hasTypedPassword = false` alongside existing private fields
    - Add `override fun onPasswordChanged(password: String)`: set `hasTypedPassword = true`, compute `val result = ValidationResult.evaluatePassword(password, password)`, call `view?.updatePasswordRules(result.passwordRules, true)`
    - _Bug_Condition: isBugCondition_Bug3 — passwordText non-empty but no TextWatcher / onPasswordChanged dispatched / rule TextViews not updated_
    - _Expected_Behavior: updatePasswordRules called with correctly evaluated PasswordRules for every keystroke_
    - _Requirements: 2.6, 2.7_

  - [~] 5.3 Add rule TextViews to `fragment_signup_name.xml`
    - Reduce `android:layout_marginBottom` on `tilPassword` from `24dp` to `8dp`
    - Add `<TextView android:id="@+id/tvRuleLength" ...>` immediately after `tilPassword`, before `btnContinue`
    - Add `<TextView android:id="@+id/tvRuleUpper" ...>`
    - Add `<TextView android:id="@+id/tvRuleLower" ...>`
    - Add `<TextView android:id="@+id/tvRuleSpecial" android:layout_marginBottom="20dp" ...>`
    - All four TextViews: `layout_width="match_parent"`, `layout_height="wrap_content"`, `textSize="12sp"`, initial `textColor="@color/rule_neutral"`, initial text using `☐` prefix
    - _Requirements: 2.7_

  - [~] 5.4 Wire up TextWatcher and `updatePasswordRules` in `SignUpNameFragment`
    - Add `fun onPasswordChanged(password: String)` to `SignUpNameFragment.Host` interface
    - In `onViewCreated`, find all four rule TextViews (`tvRuleLength`, `tvRuleUpper`, `tvRuleLower`, `tvRuleSpecial`)
    - Attach `TextWatcher` on `etPassword` that calls `host?.onPasswordChanged(s?.toString().orEmpty())` in `afterTextChanged`
    - Add `fun updatePasswordRules(rules: ValidationResult.PasswordRules, hasTyped: Boolean)` that delegates to `bindRule` for each of the four TextViews
    - Add private `fun bindRule(tv: TextView, label: String, met: Boolean, hasTyped: Boolean)` using `☐`/`☑`/`☒` icons and `R.color.rule_neutral` / `R.color.rule_met` / `R.color.rule_unmet` colors via `ContextCompat.getColor`
    - _Requirements: 2.6, 2.7_

  - [~] 5.5 Delegate `onPasswordChanged` and `updatePasswordRules` in `SignUpWizardActivity`
    - Implement `override fun onPasswordChanged(password: String)` from `SignUpNameFragment.Host`, delegating to `presenter.onPasswordChanged(password)`
    - Implement `override fun updatePasswordRules(rules: ValidationResult.PasswordRules, hasTyped: Boolean)` from `SignUpContract.View`, delegating to `nameFragment.updatePasswordRules(rules, hasTyped)`
    - _Requirements: 2.6, 2.7_

  - [~] 5.6 Verify Bug 3 exploration test now passes (Property 1: Expected Behavior)
    - **Property 1: Expected Behavior** - Live Password Rule Evaluation
    - **IMPORTANT**: Re-run test C1 from task 1 — do NOT write new tests
    - **EXPECTED OUTCOME**: Test PASSES (confirms Bug 3 is fixed)
    - _Requirements: 2.6, 2.7_

  - [~] 5.7 Verify preservation tests still pass (Property 2: Preservation)
    - **Property 2: Preservation** - Sign-Up Non-Bug Paths Unchanged
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 — do NOT write new tests
    - **EXPECTED OUTCOME**: All preservation tests PASS (confirms no regressions)

- [ ] 6. Group D — Fix Contacts: Require ≥1 contact before CONTINUE

  - [~] 6.1 Add `tvEmptyContactsError` to `fragment_signup_contacts.xml`
    - Add a `<TextView android:id="@+id/tvEmptyContactsError" ...>` with text `"Add at least one emergency contact."` and `android:visibility="gone"` near the CONTINUE button
    - Style consistently with other error messages in the layout (e.g., `textColor="@color/rule_unmet"` or error red, `textSize="14sp"`)
    - _Requirements: 2.8_

  - [~] 6.2 Guard CONTINUE in `SignUpContactsFragment.btnAction` click listener
    - Bind `tvEmptyContactsError` in `onViewCreated`
    - In the CONTINUE branch of `btnAction.setOnClickListener`: before calling `host?.onContactsContinue()`, check `if (adapter.itemCount == 0)` → set `tvEmptyContactsError.visibility = View.VISIBLE` and `return`
    - On passing the guard: set `tvEmptyContactsError.visibility = View.GONE`, then call `host?.onContactsContinue()`
    - _Bug_Condition: isBugCondition_Contacts — adapterItemCount == 0 AND user taps CONTINUE_
    - _Expected_Behavior: tvEmptyContactsError shown; onContactsContinue NOT called_
    - _Preservation: adapter non-empty → onContactsContinue still called normally_
    - _Requirements: 2.8, 3.8_

  - [~] 6.3 Hide the error when contacts become non-empty in `renderContacts`
    - In `SignUpContactsFragment.renderContacts`, when `contacts.isNotEmpty()`, call `tvEmptyContactsError.visibility = View.GONE`
    - This ensures the error clears automatically when the first contact is added
    - _Requirements: 2.8_

  - [~] 6.4 Verify Bug D exploration test now passes (Property 1: Expected Behavior)
    - **Property 1: Expected Behavior** - Contacts CONTINUE Blocked When Empty
    - **IMPORTANT**: Re-run test D1 from task 1 — do NOT write new tests
    - **EXPECTED OUTCOME**: Test PASSES (confirms Contacts bug is fixed)
    - _Requirements: 2.8_

  - [~] 6.5 Verify preservation tests still pass (Property 2: Preservation)
    - **Property 2: Preservation** - Non-Empty Contacts CONTINUE Unchanged
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 targeting contacts — do NOT write new tests
    - **EXPECTED OUTCOME**: All preservation tests PASS (confirms no regressions)

- [~] 7. Checkpoint — Ensure all tests pass
  - Run the full test suite: `./gradlew test` (unit tests) and `./gradlew connectedAndroidTest` (instrumented, if device/emulator available)
  - Confirm all four exploration tests now PASS (bugs are fixed)
  - Confirm all preservation tests still PASS (no regressions)
  - Confirm the project builds cleanly: `./gradlew assembleDebug`
  - Fix any compilation errors or test failures before marking complete
  - Ask the user if any questions arise about ambiguous behavior

- [ ] 8. Group E — Wire DatabaseManager into ContactRepository for RTDB dual-write

  - [~] 8.1 Add RTDB dual-write to ContactRepository.addContact
    - Import DatabaseManager and com.google.firebase.database.ServerValue in ContactRepository.kt
    - After the Firestore .addOnSuccessListener that calls onSuccess(saved), also write the contact to RTDB path users/{uid}/emergency_contacts/{contactId} using DatabaseManager.database.getReference("users//emergency_contacts/").setValue(saved.toMap())
    - The RTDB write is fire-and-forget (no additional callbacks needed — Firestore remains the source of truth)
    - Ensure the RTDB write uses the same saved.id as the Firestore document ID so both stores are keyed identically
    - _Requirements: checklist item 3 — contact writes to RTDB under users/{uid}/emergency_contacts_

  - [~] 8.2 Add RTDB dual-delete to ContactRepository.deleteContact
    - After the Firestore delete succeeds, also remove the RTDB node: DatabaseManager.database.getReference("users//emergency_contacts/").removeValue()
    - Fire-and-forget — do not block onSuccess on the RTDB removal
    - _Requirements: checklist item 3 — RTDB kept in sync with Firestore_

  - [~] 8.3 Verify RTDB path in DatabaseManager
    - Confirm DatabaseManager.DATABASE_URL points to https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/
    - Confirm DatabaseManager.database is the singleton FirebaseDatabase instance
    - No code changes needed if already correct — this is a read-and-confirm step
    - _Requirements: checklist item 3_

- [ ] 9. Group F — Verify ForgotPasswordActivity 8-step backstack integrity

  - [~] 9.1 Audit ForgotPasswordActivity step transitions for backstack leaks
    - Read ForgotPasswordActivity.showStep() and ForgotPasswordPresenter step transitions
    - Confirm iewFlipper.displayedChild is used for all step changes (no startActivity or inish() between steps 1–8)
    - Confirm tnLogin in step 8 calls presenter.onLoginClicked() which calls iew?.navigateToLoginCleared() which uses Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK — this clears the backstack correctly
    - Confirm back-press on steps 2/3 navigates within the ViewFlipper (no activity finish leaking to wrong screen)
    - If any step uses startActivity instead of iewFlipper.displayedChild, fix to use ViewFlipper
    - _Requirements: checklist item 4 — ForgotPasswordActivity progresses through 8 states without backstack issues_

  - [~] 9.2 Ensure OTP timer is stopped on all exit paths from ForgotPasswordActivity
    - Confirm presenter.stopOtpTimer() is called in onDestroy
    - Confirm stopOtpTimer() is also called in onChangeEmail() and onCancelCredentials() presenter methods (already present — verify, don't duplicate)
    - Confirm timer is NOT running when the activity is destroyed via the step-8 Login button
    - _Requirements: checklist item 4 — no backstack or timer leak_

