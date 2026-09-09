# Login & Sign-Up Flow Bugfix Design

## Overview

Three distinct bugs in the GuardBand Android app's authentication flows, plus an all-fields-required mandate across the sign-up wizard:

- **Bug 1 (LoginPresenter)**: `onLoginClicked` accepts any non-blank string as an identifier and passes it directly to Firebase without format validation. Users can attempt login with clearly malformed phone numbers or email addresses, leading to opaque Firebase errors rather than clear inline feedback.

- **Bug 2 (SignUpPresenter)**: `onNameContinue` has a silent `else → goToStep(2)` branch that allows progression through Step 1 when both email and password fields are blank — creating accounts with no credentials. Additionally, `onSkip()` offers a navigation escape hatch that bypasses required fields, and the Skip button is still visible in the UI.

- **Bug 3 (SignUpPresenter / SignUpNameFragment)**: Password strength rules are not evaluated live as the user types. The only check is `password.length < 8`, which fires only on form submission. The four rule indicators (`tvRuleLength`, `tvRuleUpper`, `tvRuleLower`, `tvRuleSpecial`) do not exist in the layout and are not wired up. The UX matches `ForgotPasswordActivity`'s credential screen but the implementation is absent.

- **Contacts requirement**: `SignUpContactsFragment` allows the user to tap CONTINUE with zero emergency contacts saved, silently completing signup without this data.

The fix strategy is targeted and minimal: add format validation in `LoginPresenter`, remove the blank-credential bypass in `SignUpPresenter`, add live password rule evaluation (reusing the existing `ValidationResult.evaluatePassword` and the `bindRule` color pattern already in `ForgotPasswordActivity`), hide the Skip button, and block CONTINUE in contacts when the list is empty.

No changes are needed to `AuthRepository`, `ForgotPasswordPresenter`, `ForgotPasswordActivity`, `ValidationResult`, or `SignUpLocationFragment`.

---

## Glossary

- **Bug_Condition (C)**: The set of inputs that trigger the defective behavior — as distinguished per bug below.
- **Property (P)**: The correct behavior that the fixed function SHALL exhibit for any input where C holds.
- **Preservation**: All behaviors for inputs where C does NOT hold that must remain byte-for-identical after the fix.
- **`onLoginClicked`**: Method in `LoginPresenter.kt` that validates credentials and dispatches to `authRepository.login(...)`.
- **`onNameContinue`**: Method in `SignUpPresenter.kt` that validates Step 1 fields and advances the wizard to Step 2.
- **`onPasswordChanged`**: New method to be added to `SignUpPresenter.kt` / `SignUpContract.Presenter` that evaluates live password strength as the user types.
- **`evaluatePassword`**: Static helper in `ValidationResult.kt` that returns a `PasswordRules` struct indicating which of the 4 strength rules are met.
- **`PasswordRules`**: Data class with `minLength`, `hasUppercase`, `hasLowercase`, `hasNumberOrSpecial`, `passwordsMatch`, and computed `allMet`.
- **`bindRule`**: Visual helper pattern (from `ForgotPasswordActivity`) that sets a `TextView`'s icon prefix and color based on whether the rule is met/unmet/not-yet-typed.
- **`isPhoneLike` / `isEmailLike`**: Format-detection helpers to be added to `LoginPresenter` to decide which validation path to apply.

---

## Bug Details

### Bug 1 — Login Identifier Format Validation

#### Bug Condition

The bug manifests when a user submits a non-blank but syntactically invalid identifier (phone or email) from `LoginActivity`. `onLoginClicked` passes the raw string directly to `authRepository.login()` without format checking, which then calls Firebase and returns a generic or misleading error message.

**Formal Specification:**
```
FUNCTION isBugCondition_Bug1(identifier, password)
  INPUT: identifier: String, password: String
  OUTPUT: boolean

  IF identifier.isBlank() THEN RETURN false   // caught by existing blank check
  IF password.isBlank()   THEN RETURN false   // caught by existing blank check

  isPhoneLike  := identifier.startsWith("+") OR identifier.all { it.isDigit() }
  isEmailLike  := identifier.contains("@")

  RETURN (isPhoneLike AND NOT identifier.matches(Regex("\\+?[0-9]{7,15}")))
      OR (isEmailLike AND NOT Patterns.EMAIL_ADDRESS.matches(identifier))
      OR (NOT isPhoneLike AND NOT isEmailLike)
END FUNCTION
```

#### Examples

- `identifier = "abc"`, `password = "Secret1!"` → Bug condition holds. Neither phone-like nor email-like. Expected: "Please enter a valid phone number or email." Actual: Firebase call with opaque error.
- `identifier = "+123"`, `password = "Secret1!"` → Bug condition holds. Phone-like but fails `[0-9]{7,15}` length check. Expected: "Invalid phone number format." Actual: Firebase error.
- `identifier = "notanemail@"`, `password = "Secret1!"` → Bug condition holds. `@` present but fails `Patterns.EMAIL_ADDRESS`. Expected: "Invalid email format." Actual: Firebase error.
- `identifier = "user@example.com"`, `password = "Secret1!"` → Bug condition does NOT hold. Valid email → passes through to `authRepository.login()`.
- `identifier = "+639171234567"`, `password = "Secret1!"` → Bug condition does NOT hold. Valid phone → passes through.

---

### Bug 2 — Sign-Up Step 1 Allows Blank Credentials

#### Bug Condition

The bug manifests when a user taps CONTINUE on Step 1 with blank email and password fields. The `else → goToStep(2)` branch in `onNameContinue` silently advances the wizard, creating an account state with no Auth credentials. Additionally, the Skip button in `SignUpWizardActivity` calls `onSkip()` which calls `goToStep(2)` from Step 1, bypassing the same checks.

**Formal Specification:**
```
FUNCTION isBugCondition_Bug2(firstName, lastName, email, password)
  INPUT: four String fields from Sign-Up Step 1
  OUTPUT: boolean

  namesProvided := firstName.isNotBlank() AND lastName.isNotBlank()

  RETURN namesProvided
         AND email.isBlank()
         AND password.isBlank()
END FUNCTION
```

#### Examples

- `firstName="Alice"`, `lastName="Smith"`, `email=""`, `password=""` → Bug condition holds. Wizard advances to Step 2. Expected: field errors on email and password fields.
- `firstName=""`, `lastName=""`, `email=""`, `password=""` → Bug condition does NOT hold (caught by existing firstName/lastName blank checks).
- `firstName="Alice"`, `lastName="Smith"`, `email="alice@example.com"`, `password="Short1"` → Bug condition does NOT hold; email present, hits password length check.
- Skip button tapped on Step 1 → Bug condition manifests differently but same root: `onSkip()` calls `goToStep(2)` unconditionally.

---

### Bug 3 — No Live Password Rule Feedback on Sign-Up Step 1

#### Bug Condition

The bug manifests when a user types into the password field on Step 1 of the sign-up wizard. No live rule evaluation occurs because: (a) `SignUpNameFragment` has no `TextWatcher` on `etPassword`, (b) `onPasswordChanged` does not exist in the presenter or contract, and (c) the four rule `TextView`s (`tvRuleLength`, `tvRuleUpper`, `tvRuleLower`, `tvRuleSpecial`) do not exist in `fragment_signup_name.xml`. The only password check fires at form submission and gives only a generic "Password must be at least 8 characters." message.

**Formal Specification:**
```
FUNCTION isBugCondition_Bug3(passwordText)
  INPUT: passwordText: String typed by user into etPassword
  OUTPUT: boolean

  RETURN passwordText.isNotEmpty()
         AND (NO TextWatcher fires onPasswordChanged)
         AND (rule TextViews are absent from layout OR not updated)
END FUNCTION
```

#### Examples

- User types `"Hello"` → Bug holds. No rule indicators update. Expected: `tvRuleLength` shows ☒ (unmet), `tvRuleUpper` shows ☑ (met), etc.
- User types `"Hello1!"` then `"Hello1!A"` → Bug holds for both keystrokes. Expected: indicators update incrementally.
- User submits with `password = "hello"` → Only generic "Password must be at least 8 characters." shown. Expected: all four rule TextViews highlight unmet rules.
- Form submitted with `password = "Hello1!X"` → Bug condition does NOT hold at submission (all rules met), but the live indicators were never shown.

---

### Contacts Requirement — CONTINUE Allowed with Zero Contacts

#### Bug Condition

The bug manifests when a user taps CONTINUE on Step 3 with no emergency contacts saved. `SignUpContactsFragment.btnAction` dispatches `onContactsContinue()` regardless of adapter item count.

**Formal Specification:**
```
FUNCTION isBugCondition_Contacts(adapterItemCount)
  INPUT: adapterItemCount: Int (number of saved contacts)
  OUTPUT: boolean

  RETURN adapterItemCount == 0
         AND user taps CONTINUE button
END FUNCTION
```

#### Examples

- No contacts added, user taps CONTINUE → Bug condition holds. Wizard completes. Expected: "Add at least one emergency contact." shown inline.
- One contact added, user taps CONTINUE → Bug condition does NOT hold. `onContactsContinue()` called normally.

---

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors (must not be altered by any of the fixes):**

1. `LoginPresenter`: Valid email identifiers (`user@example.com`) continue to reach `authRepository.login()` unmodified.
2. `LoginPresenter`: Valid phone identifiers (`+639171234567`, `09171234567`) continue to reach `authRepository.login()` unmodified.
3. `LoginPresenter`: Blank identifier → "Phone number or email is required." (existing behavior, order unchanged).
4. `LoginPresenter`: Blank password → "Password is required." (existing behavior).
5. `SignUpPresenter.onNameContinue`: Blank firstName → `showFieldError("firstName", "First name is required.")` (unchanged).
6. `SignUpPresenter.onNameContinue`: Blank lastName → `showFieldError("lastName", "Last name is required.")` (unchanged).
7. `SignUpPresenter.onLocationContinue`: Blank location → `showFieldError("location", ...)` (unchanged).
8. `SignUpPresenter.onLocationContinue`: After successful `updateProfile`, always calls `goToStep(3)` (unchanged).
9. `SignUpPresenter.onAddContact` / `onDeleteContact`: Contact list management unchanged.
10. `SignUpPresenter.onContactsContinue`: When contacts list is non-empty, calls `authRepository.updateProfile(...)` and navigates to Loading (unchanged).
11. `ForgotPasswordPresenter` / `ForgotPasswordActivity`: Entirely unmodified.
12. `AuthRepository`: Entirely unmodified.
13. `ValidationResult.evaluatePassword`: Entirely unmodified.
14. `SignUpLocationFragment`: Entirely unmodified.
15. Google Sign-In flow (`LoginPresenter.onGoogleIdTokenReceived`): Unchanged — identifier format checks apply only to the `onLoginClicked` path.

**Scope:**
All inputs that do NOT satisfy any of the four bug conditions above are completely unaffected by the fixes. This includes all valid credential combinations, all Firebase-backed flows, all fragment transitions already working correctly, and all error paths not listed as bugs.

---

## Hypothesized Root Causes

### Bug 1
1. **Missing format-detection step**: `onLoginClicked` was written to rely on Firebase to surface format errors, rather than validating client-side first. There is no `isPhoneLike` / `isEmailLike` helper.
2. **Error message ownership**: Firebase errors for malformed inputs are generic (e.g., "The email address is badly formatted.") and not localized to specific fields; the fix centralises this in the presenter.

### Bug 2
1. **Conditional credential block**: The credential registration block in `onNameContinue` only activates when `trimmedEmail.isNotBlank() OR trimmedPassword.isNotBlank()`. Both blank → the `else` branch silently advances. The intent was to allow Google-seeded users to skip credential entry, but the condition is too broad.
2. **`onSkip()` not guarded per step**: `onSkip()` calls `goToStep(2)` for step 1 with no validation gate, providing a second bypass path.
3. **Skip button visible**: `tvSkip` in `SignUpWizardActivity` is never hidden, making the bypass user-reachable.

### Bug 3
1. **`TextWatcher` never attached**: `SignUpNameFragment.onViewCreated` does not add a watcher to `etPassword`, so `onPasswordChanged` is never called.
2. **`onPasswordChanged` not in contract**: The method doesn't exist in `SignUpContract.Presenter` or `SignUpPresenter`, so even if the watcher fired there is nowhere to dispatch to.
3. **Rule TextViews missing from layout**: `fragment_signup_name.xml` jumps straight from `tilPassword` to `btnContinue`. There are no `tvRuleLength`, `tvRuleUpper`, `tvRuleLower`, `tvRuleSpecial` views to bind.
4. **`updatePasswordRules` not in contract**: `SignUpContract.View` / `SignUpWizardActivity` don't implement the display method.

### Contacts
1. **No guard in button handler**: `btnAction.setOnClickListener` dispatches `onContactsContinue()` when button text is "CONTINUE" without checking `adapter.itemCount`.

---

## Correctness Properties

Property 1: Bug Condition — Login Identifier Format Validation

_For any_ call to `onLoginClicked(identifier, password)` where `identifier` is non-blank, `password` is non-blank, and `identifier` is syntactically invalid (malformed phone, malformed email, or neither phone-like nor email-like), the fixed `LoginPresenter` SHALL call `view?.showError(...)` with a descriptive inline message and SHALL NOT call `authRepository.login(...)`.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Bug Condition — Sign-Up Step 1 Rejects Blank Credentials

_For any_ call to `onNameContinue(firstName, lastName, email, password)` where `firstName` and `lastName` are non-blank but either `email` or `password` (or both) are blank, the fixed `SignUpPresenter` SHALL call `view?.showFieldError(...)` for each blank credential field and SHALL NOT advance to Step 2.

**Validates: Requirements 2.4, 2.5**

Property 3: Bug Condition — Live Password Rule Evaluation

_For any_ non-empty string `passwordText` typed into `etPassword`, the fixed system SHALL call `view?.updatePasswordRules(rules, hasTyped=true)` with the correctly evaluated `PasswordRules`, and all four rule `TextView`s in `fragment_signup_name.xml` SHALL visually reflect the current rule state using the `bindRule` color pattern.

**Validates: Requirements 2.6, 2.7**

Property 4: Bug Condition — Contacts CONTINUE Blocked When Empty

_For any_ tap of the CONTINUE button in `SignUpContactsFragment` when `adapter.itemCount == 0`, the fixed fragment SHALL display the error message "Add at least one emergency contact." and SHALL NOT call `host?.onContactsContinue()`.

**Validates: Requirements 2.8**

Property 5: Preservation — Valid Login Inputs Reach Repository

_For any_ call to `onLoginClicked(identifier, password)` where both fields are non-blank and `identifier` is a syntactically valid email or phone, the fixed `LoginPresenter` SHALL call `authRepository.login(identifier, password, ...)` exactly as the original code does, preserving all existing success and error callback behavior.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

Property 6: Preservation — Sign-Up Wizard Non-Bug Paths Unchanged

_For any_ input to `onNameContinue`, `onLocationContinue`, `onAddContact`, `onDeleteContact`, or `onContactsContinue` that does NOT satisfy any bug condition, the fixed `SignUpPresenter` SHALL produce the same outcomes as the original code, including all `goToStep`, `showFieldError`, `navigateToLoading`, and Firebase call behaviors.

**Validates: Requirements 3.5, 3.6, 3.7, 3.8**

---

## Fix Implementation

### Changes Required

#### File: `app/src/main/java/com/example/guardband/ui/login/LoginPresenter.kt`

**Function**: `onLoginClicked` + new private helpers

**Specific Changes:**

1. **Add format-detection helpers** (private, after existing methods):
   ```kotlin
   private fun isPhoneLike(s: String): Boolean =
       s.startsWith("+") || s.all { it.isDigit() }

   private fun isValidPhone(s: String): Boolean =
       s.matches(Regex("\\+?[0-9]{7,15}"))

   private fun isEmailLike(s: String): Boolean = s.contains("@")
   ```

2. **Replace the existing guard block in `onLoginClicked`** with:
   ```
   1. Blank identifier  → showError("Phone number or email is required.")  return
   2. Blank password    → showError("Password is required.")                return
   3. isPhoneLike(id)   → if !isValidPhone(id) showError("Invalid phone number format.") return
   4. isEmailLike(id)   → if !Patterns.EMAIL_ADDRESS.matches(id) showError("Invalid email format.") return
   5. neither           → showError("Please enter a valid phone number or email.")  return
   6. pass              → view?.showLoading(); authRepository.login(identifier, password, ...)
   ```

---

#### File: `app/src/main/java/com/example/guardband/ui/signup/SignUpContract.kt`

**Specific Changes:**

1. **Add to `Presenter` interface:**
   ```kotlin
   fun onPasswordChanged(password: String)
   ```

2. **Add to `View` interface:**
   ```kotlin
   fun updatePasswordRules(rules: ValidationResult.PasswordRules, hasTyped: Boolean)
   ```
   (Requires adding `import com.example.guardband.data.model.ValidationResult` to the file.)

---

#### File: `app/src/main/java/com/example/guardband/ui/signup/SignUpPresenter.kt`

**Specific Changes:**

1. **Add `hasTypedPassword` field** alongside existing fields:
   ```kotlin
   private var hasTypedPassword = false
   ```

2. **Add `onPasswordChanged` override:**
   ```kotlin
   override fun onPasswordChanged(password: String) {
       hasTypedPassword = true
       val result = ValidationResult.evaluatePassword(password, password)
       view?.updatePasswordRules(result.passwordRules, true)
   }
   ```

3. **Replace the credential block in `onNameContinue`** — change the condition from `if (trimmedEmail.isNotBlank() || trimmedPassword.isNotBlank())` to require both fields:
   - If `trimmedEmail.isBlank()` → `showFieldError("email", "Email or phone is required.")` and return.
   - Validate email vs phone format (same pattern as `LoginPresenter`).
   - If `trimmedPassword.isBlank()` → `showFieldError("password", "Password is required.")` and return.
   - Replace `trimmedPassword.length < 8` check with:
     ```kotlin
     val rules = ValidationResult.evaluatePassword(trimmedPassword, trimmedPassword)
     if (!rules.passwordRules.allMet) {
         view?.showFieldError("password", "Password does not meet all requirements.")
         view?.updatePasswordRules(rules.passwordRules, true)
         return
     }
     ```
   - Remove the `else → goToStep(2)` branch entirely.

4. **Remove or no-op `onSkip()`** — change all cases to no-ops or remove the method body so it never navigates from Step 1.

---

#### File: `app/src/main/java/com/example/guardband/ui/signup/SignUpNameFragment.kt`

**Specific Changes:**

1. **Add lateinit views** for the four rule TextViews: `tvRuleLength`, `tvRuleUpper`, `tvRuleLower`, `tvRuleSpecial`.

2. **In `onViewCreated`**, bind all four views and attach a `TextWatcher` on `etPassword`:
   ```kotlin
   etPassword.addTextChangedListener(object : TextWatcher {
       override fun afterTextChanged(s: Editable?) {
           host?.onPasswordChanged(s?.toString().orEmpty())
       }
       override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
       override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
   })
   ```

3. **Add `updatePasswordRules(rules, hasTyped)` function** using the same `bindRule` helper pattern as `ForgotPasswordActivity`:
   ```kotlin
   fun updatePasswordRules(rules: ValidationResult.PasswordRules, hasTyped: Boolean) {
       bindRule(tvRuleLength,  "At least 8 characters",                   rules.minLength,         hasTyped)
       bindRule(tvRuleUpper,   "At least one uppercase letter",            rules.hasUppercase,      hasTyped)
       bindRule(tvRuleLower,   "At least one lowercase letter",            rules.hasLowercase,      hasTyped)
       bindRule(tvRuleSpecial, "At least one number or special character", rules.hasNumberOrSpecial, hasTyped)
   }

   private fun bindRule(tv: TextView, label: String, met: Boolean, hasTyped: Boolean) {
       val icon = when { !hasTyped -> "☐"; met -> "☑"; else -> "☒" }
       tv.text = "$icon $label"
       tv.setTextColor(ContextCompat.getColor(requireContext(), when {
           !hasTyped -> R.color.rule_neutral
           met       -> R.color.rule_met
           else      -> R.color.rule_unmet
       }))
   }
   ```

---

#### File: `app/src/main/java/com/example/guardband/ui/signup/SignUpWizardActivity.kt`

**Specific Changes:**

1. **In `onCreate`**, hide the Skip button:
   ```kotlin
   findViewById<TextView>(R.id.tvSkip).visibility = View.GONE
   ```

2. **Add `onPasswordChanged` delegation** in the `SignUpNameFragment.Host` interface implementation:
   ```kotlin
   override fun onPasswordChanged(firstName: String) { /* already in Host */ }
   // Actually, add to SignUpNameFragment.Host:
   fun onPasswordChanged(password: String)
   // and delegate:
   override fun onPasswordChanged(password: String) {
       presenter.onPasswordChanged(password)
   }
   ```
   *(The `Host` interface on `SignUpNameFragment` needs `onPasswordChanged(String)` added.)*

3. **Implement `updatePasswordRules`** from `SignUpContract.View`:
   ```kotlin
   override fun updatePasswordRules(rules: ValidationResult.PasswordRules, hasTyped: Boolean) {
       nameFragment.updatePasswordRules(rules, hasTyped)
   }
   ```

---

#### File: `app/src/main/java/com/example/guardband/ui/signup/SignUpContactsFragment.kt`

**Specific Changes:**

1. **Add a `tvEmptyContactsError` TextView** reference (to be added to layout, or use an existing container — see layout change below).

2. **In `btnAction.setOnClickListener`**, add guard before `onContactsContinue()`:
   ```kotlin
   if (btnAction.text.toString().equals("CONTINUE", ignoreCase = true) &&
       layoutAddForm.visibility != View.VISIBLE
   ) {
       if (adapter.itemCount == 0) {
           tvEmptyContactsError.visibility = View.VISIBLE
           return
       }
       tvEmptyContactsError.visibility = View.GONE
       host?.onContactsContinue()
   } else { /* existing ADD path */ }
   ```

3. **In `renderContacts`**, hide the error when contacts are non-empty:
   ```kotlin
   if (contacts.isNotEmpty()) tvEmptyContactsError.visibility = View.GONE
   ```

---

#### File: `app/src/main/res/layout/fragment_signup_name.xml`

**Specific Changes:**

1. **Remove `android:layout_marginBottom="24dp"`** from `tilPassword` (replace with `8dp` or `4dp` to allow rules to sit naturally below it).

2. **Add four `TextView`s** immediately after `tilPassword` and before `btnContinue`:
   ```xml
   <TextView
       android:id="@+id/tvRuleLength"
       android:layout_width="match_parent"
       android:layout_height="wrap_content"
       android:layout_marginTop="4dp"
       android:text="☐ At least 8 characters"
       android:textColor="@color/rule_neutral"
       android:textSize="12sp" />

   <TextView
       android:id="@+id/tvRuleUpper"
       android:layout_width="match_parent"
       android:layout_height="wrap_content"
       android:layout_marginTop="2dp"
       android:text="☐ At least one uppercase letter"
       android:textColor="@color/rule_neutral"
       android:textSize="12sp" />

   <TextView
       android:id="@+id/tvRuleLower"
       android:layout_width="match_parent"
       android:layout_height="wrap_content"
       android:layout_marginTop="2dp"
       android:text="☐ At least one lowercase letter"
       android:textColor="@color/rule_neutral"
       android:textSize="12sp" />

   <TextView
       android:id="@+id/tvRuleSpecial"
       android:layout_width="match_parent"
       android:layout_height="wrap_content"
       android:layout_marginTop="2dp"
       android:layout_marginBottom="20dp"
       android:text="☐ At least one number or special character"
       android:textColor="@color/rule_neutral"
       android:textSize="12sp" />
   ```

---

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate each bug on the unfixed code to confirm root-cause analysis; then verify the fix produces correct behavior for all bug inputs and preserves all non-bug-input behavior.

---

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate each bug BEFORE implementing the fix. Confirm or refute the root cause analysis.

**Test Plan**: Write unit tests against `LoginPresenter` and `SignUpPresenter` using mock views. Run against unfixed code to observe that failures match the hypothesized root causes.

**Test Cases:**

1. **Bug 1 — Malformed Phone**: Call `onLoginClicked("+123", "Secret1!")` on unfixed code. Expected: `showError("Invalid phone number format.")` called, `authRepository.login()` NOT called. Actual on unfixed: `authRepository.login()` called → confirms missing format guard.

2. **Bug 1 — Neither Phone nor Email**: Call `onLoginClicked("abc", "Secret1!")` on unfixed code. Expected: `showError("Please enter a valid phone number or email.")`. Actual on unfixed: Firebase call dispatched.

3. **Bug 2 — Blank Credentials Bypass**: Call `onNameContinue("Alice", "Smith", "", "")` on unfixed code. Expected: field errors shown. Actual on unfixed: `goToStep(2)` called → confirms `else` branch bypass.

4. **Bug 2 — Skip Button Bypass**: Call `onSkip()` while `step == 1` on unfixed code. Expected: no navigation (or error). Actual on unfixed: `goToStep(2)` called.

5. **Bug 3 — No Live Rules**: Simulate calling `onPasswordChanged("Hello")` on unfixed code (method doesn't exist → compilation error or no-op). Confirms method is absent.

6. **Contacts — Empty CONTINUE**: Simulate `adapter.itemCount == 0` and button tap. Expected: error message shown. Actual on unfixed: `onContactsContinue()` called.

**Expected Counterexamples:**
- `authRepository.login()` invoked with invalid identifiers.
- `goToStep(2)` invoked when email/password blank.
- No call to `view?.updatePasswordRules(...)` on password text changes.
- `host?.onContactsContinue()` invoked with empty adapter.

---

### Fix Checking

**Goal**: Verify that for all inputs where any bug condition holds, the fixed code produces the correct behavior.

**Pseudocode:**
```
FOR ALL (identifier, password) WHERE isBugCondition_Bug1(identifier, password) DO
  result := onLoginClicked_fixed(identifier, password)
  ASSERT showError called with correct message
  ASSERT authRepository.login NOT called
END FOR

FOR ALL (firstName, lastName, email, password) WHERE isBugCondition_Bug2(...) DO
  result := onNameContinue_fixed(...)
  ASSERT showFieldError called for blank credential fields
  ASSERT goToStep NOT called
END FOR

FOR ALL passwordText WHERE isBugCondition_Bug3(passwordText) DO
  result := onPasswordChanged_fixed(passwordText)
  ASSERT updatePasswordRules called with correct PasswordRules
  ASSERT rule TextViews updated with correct icons and colors
END FOR

FOR ALL adapterCount WHERE isBugCondition_Contacts(adapterCount) DO
  result := btnAction_fixed tap
  ASSERT tvEmptyContactsError visible
  ASSERT onContactsContinue NOT called
END FOR
```

---

### Preservation Checking

**Goal**: Verify that for all inputs where no bug condition holds, fixed code produces the same result as original code.

**Pseudocode:**
```
FOR ALL (identifier, password) WHERE NOT isBugCondition_Bug1(identifier, password) DO
  ASSERT onLoginClicked_original(identifier, password) = onLoginClicked_fixed(identifier, password)
END FOR

FOR ALL (firstName, lastName, email, password) WHERE NOT isBugCondition_Bug2(...) DO
  ASSERT onNameContinue_original(...) = onNameContinue_fixed(...)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically, covering the full valid-input domain.
- It catches edge cases (e.g., phone numbers at the boundary of 7 and 15 digits) that manual tests miss.
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs.

**Test Cases:**

1. **Valid Email Login Preservation**: Generate random valid email + password pairs; verify `authRepository.login()` is called with exact inputs.
2. **Valid Phone Login Preservation**: Generate phone strings matching `\+?[0-9]{7,15}`; verify `authRepository.login()` called.
3. **Blank-Field Errors Preservation**: `identifier=""` → existing "Phone number or email is required." still fires first.
4. **Blank Password Preservation**: `identifier="user@example.com"`, `password=""` → "Password is required." still fires.
5. **First/Last Name Blank Preservation**: `onNameContinue("", "Smith", "a@b.com", "Secret1!")` → `showFieldError("firstName", ...)` still fires.
6. **Location Continue Preservation**: Valid location → `goToStep(3)` via `updateProfile` success callback (unchanged).
7. **Non-Empty Contacts CONTINUE Preservation**: 1+ contacts → `onContactsContinue()` called normally.

---

### Unit Tests

- `LoginPresenterTest`: Test all 6 guard cases in `onLoginClicked` (blank id, blank pw, invalid phone, invalid email, neither, valid) with a mock `LoginContract.View` and mock `AuthRepository`.
- `SignUpPresenterTest`: Test `onNameContinue` with all combinations of blank/non-blank/valid/invalid fields.
- `SignUpPresenterTest`: Test `onPasswordChanged` produces correct `PasswordRules` for sample password strings.
- `SignUpPresenterTest`: Test `onNameContinue` full-password-rules path at submission.
- `SignUpContactsFragmentTest`: Test CONTINUE tap with 0 and 1+ contacts.

### Property-Based Tests

- Generate random strings that pass `isPhoneLike` and `isValidPhone`; assert `authRepository.login()` always called.
- Generate random strings that are email-like and valid; assert `authRepository.login()` always called.
- Generate random passwords; assert `evaluatePassword(pw, pw).passwordRules` returned by `onPasswordChanged` matches direct `ValidationResult.evaluatePassword(pw, pw)` call.
- Generate random sign-up Step 1 inputs with all non-blank fields and valid credentials; assert `goToStep(2)` always called (no regression).

### Integration Tests

- End-to-end: Submit login form with malformed phone → inline error visible, no Firebase call.
- End-to-end: Complete sign-up Step 1 with all valid fields → proceeds to Step 2 correctly.
- End-to-end: Type progressively in password field → all four rule TextViews update correctly at each keystroke.
- End-to-end: Attempt CONTINUE on Step 3 with no contacts → error message visible, wizard does not complete.
- End-to-end: Skip button is not visible on any step of the wizard.
