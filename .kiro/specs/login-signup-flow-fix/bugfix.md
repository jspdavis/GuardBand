# Bugfix Requirements Document

## Introduction

Three interconnected bugs affect the GuardBand Android app's authentication flows: the Login screen leaks a confusing Firebase error when users type a phone number as their identifier; the Sign-Up wizard navigates away from Step 2 without advancing to Step 3; and the Sign-Up password field silently accepts weak passwords that would be rejected later by `AuthRepository`. A fourth cross-cutting concern — that every form field across Login, Sign-Up, and Forgot Password must be required — ties all three together. Fixing these bugs must preserve the existing happy-path behaviour for users who already enter valid credentials in the correct format.

---

## Bug Analysis

### Current Behavior (Defect)

**Bug 1 — Login identifier validation**

1.1 WHEN the user submits the login form with a phone-number identifier (input starts with `+` or contains only digits) THEN the system shows "Email is badly formatted." (a raw Firebase Auth error) before the phone-number Firestore lookup can complete.

1.2 WHEN the user submits the login form with a syntactically invalid email string (contains `@` but is malformed) THEN the system passes the value directly to Firebase and surfaces an opaque provider error instead of a user-friendly validation message.

1.3 WHEN the user submits the login form with an identifier string that is neither a phone number nor an email address THEN the system passes the unrecognized string to Firebase and surfaces a confusing error rather than a clear format hint.

1.4 WHEN the user submits the login form with a blank password field THEN the system shows "Password is required." but still proceeds to call `authRepository.login()` after the blank-check in some code paths (no early return guard on the combined flow).

**Bug 2 — Sign-Up Step 2 → Step 3 navigation**

2.1 WHEN the user completes Step 2 (Location) of the sign-up wizard and taps Continue THEN the system navigates away from the wizard (back to LoginActivity or finishes the activity) instead of advancing to Step 3 (Emergency Contacts).

2.2 WHEN the user is on Step 1 of sign-up and leaves both email and password blank THEN the system silently advances to Step 2 without creating an account, bypassing the "all fields required" mandate.

2.3 WHEN the user taps the "Skip" button at any step of the wizard THEN the system advances past the current required step without validating or saving data, violating the all-fields-required mandate.

**Bug 3 — Weak password accepted at sign-up**

3.1 WHEN the user types a password shorter than 8 characters in Sign-Up Step 1 THEN the system shows an error only at submission time, with no real-time feedback while typing.

3.2 WHEN the user types a password that is 8+ characters but lacks an uppercase letter, lowercase letter, or number/special character THEN the system accepts the password and proceeds to Firebase registration, which subsequently fails with a server-side error.

3.3 WHEN the user types in the password field of Sign-Up Step 1 THEN the system shows no live rule indicators (length, uppercase, lowercase, number/special), giving no guidance on what constitutes a valid password.

---

### Expected Behavior (Correct)

**Bug 1 — Login identifier validation**

2.1 WHEN the user submits the login form with an identifier that starts with `+` or consists only of digits, and it does not match the pattern `+?[0-9]{7,15}` THEN the system SHALL show "Invalid phone number format." and SHALL NOT call `authRepository.login()`.

2.2 WHEN the user submits the login form with an identifier that starts with `+` or consists only of digits, and it matches `+?[0-9]{7,15}` THEN the system SHALL proceed to `authRepository.login()` without pre-empting the Firestore phone-lookup fallback.

2.3 WHEN the user submits the login form with an identifier that contains `@` and it fails `android.util.Patterns.EMAIL_ADDRESS` THEN the system SHALL show "Invalid email format." and SHALL NOT call `authRepository.login()`.

2.4 WHEN the user submits the login form with an identifier that contains `@` and it passes `android.util.Patterns.EMAIL_ADDRESS` THEN the system SHALL proceed to `authRepository.login()`.

2.5 WHEN the user submits the login form with an identifier that is neither phone-like nor email-like THEN the system SHALL show "Please enter a valid phone number or email." and SHALL NOT call `authRepository.login()`.

2.6 WHEN the user submits the login form with a blank password THEN the system SHALL show "Password is required." and SHALL NOT call `authRepository.login()`.

2.7 WHEN the user submits the login form with a blank identifier THEN the system SHALL show "Phone number or email is required." and SHALL NOT call `authRepository.login()`.

**Bug 2 — Sign-Up Step 2 → Step 3 navigation**

2.8 WHEN `SignUpPresenter.onLocationContinue()` is called with a non-blank location and the profile update succeeds THEN the system SHALL advance the wizard to Step 3 (Emergency Contacts) and SHALL NOT navigate to LoginActivity or call `finish()` on the wizard activity.

2.9 WHEN `SignUpPresenter.onLocationContinue()` is called with a non-blank location and the profile update fails THEN the system SHALL still advance the wizard to Step 3 and SHALL display the error as a toast, preserving wizard continuity.

2.10 WHEN the user taps Continue on Sign-Up Step 1 and firstName, lastName, email, or password is blank THEN the system SHALL show a per-field required error and SHALL NOT advance to Step 2.

2.11 WHEN the "Skip" button is present in `SignUpWizardActivity` THEN the system SHALL NOT allow the user to bypass any required step; the Skip button SHALL be removed or hidden for all steps.

**Bug 3 — Weak password accepted at sign-up**

2.12 WHEN the user types in the password field of Sign-Up Step 1 THEN the system SHALL display four live rule indicators (minimum 8 characters, contains uppercase, contains lowercase, contains number or special character) that update in real time as the user types.

2.13 WHEN the user taps Continue on Sign-Up Step 1 and the password does not satisfy all four rules THEN the system SHALL block submission and show appropriate per-rule error state, using `ValidationResult.evaluatePassword()`.

2.14 WHEN the user taps Continue on Sign-Up Step 1 and the password field is blank THEN the system SHALL show "Password is required." and SHALL NOT proceed.

2.15 WHEN the user taps Continue on Sign-Up Step 1 and the email/phone field is blank THEN the system SHALL show "Email or phone is required." and SHALL NOT proceed.

**Cross-cutting — Emergency Contacts (Step 3)**

2.16 WHEN the user is on Sign-Up Step 3 and has added zero emergency contacts THEN the system SHALL disable or prevent the CONTINUE button action and SHALL display a message prompting the user to add at least one contact.

---

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the user submits the login form with a valid email and correct password THEN the system SHALL CONTINUE TO authenticate via Firebase and navigate to MainActivity (via LoadingActivity).

3.2 WHEN the user submits the login form with a valid phone number that exists in Firestore's phone index and the correct password THEN the system SHALL CONTINUE TO resolve the email via `resolveEmailFromPhone` and authenticate successfully.

3.3 WHEN the user signs in with Google and the account is new or incomplete THEN the system SHALL CONTINUE TO navigate to SignUpWizardActivity at Step 2 (Location) with the user's name and email pre-filled.

3.4 WHEN the user signs in with Google and the account is already complete THEN the system SHALL CONTINUE TO navigate directly to MainActivity.

3.5 WHEN the user completes Sign-Up Step 1 with valid firstName, lastName, a valid email, and a password meeting all four rules THEN the system SHALL CONTINUE TO call `authRepository.registerWithEmail()` and advance to Step 2.

3.6 WHEN the user completes Sign-Up Step 2 with a non-blank location THEN the system SHALL CONTINUE TO call `authRepository.updateProfile()` and advance to Step 3.

3.7 WHEN the user adds at least one emergency contact in Step 3 and taps CONTINUE THEN the system SHALL CONTINUE TO call `authRepository.updateProfile()` with `profileComplete = true` and navigate to LoadingActivity → MainActivity.

3.8 WHEN the user completes the Forgot Password flow (email → OTP → new password) THEN the system SHALL CONTINUE TO call `authRepository.updatePasswordWithOtp()` and navigate to LoginActivity with the back stack cleared.

3.9 WHEN the Forgot Password OTP timer expires THEN the system SHALL CONTINUE TO call `view?.onTimerFinished()` after 2 minutes, as implemented in `ForgotPasswordPresenter.startOtpTimer()`.

3.10 WHEN the user changes an existing emergency contact's data in Step 3 THEN the system SHALL CONTINUE TO reflect the updated list via `renderContacts()` without duplicating or losing contacts.

3.11 WHEN the user deletes an emergency contact that was saved to Firestore (non-local ID) THEN the system SHALL CONTINUE TO call `contactRepository.deleteContact()` in addition to removing it from the local list.

3.12 WHEN `SignUpPresenter.onBack()` is called from Step 2 or Step 3 THEN the system SHALL CONTINUE TO navigate to the previous step within the wizard without finishing the activity.

---

## Bug Condition Pseudocode

### Bug 1 — Login Identifier Validation

```pascal
FUNCTION isBugCondition_Bug1(identifier)
  INPUT: identifier of type String
  OUTPUT: boolean

  trimmed ← identifier.trim()
  isPhoneLike ← trimmed.startsWith("+") OR trimmed.all { it.isDigit() }
  isEmailLike ← trimmed.contains("@")

  IF isPhoneLike THEN
    RETURN NOT matches(trimmed, pattern="+?[0-9]{7,15}")
  ELSE IF isEmailLike THEN
    RETURN NOT Patterns.EMAIL_ADDRESS.matches(trimmed)
  ELSE
    RETURN true  // unrecognized format — always a bug condition
  END IF
END FUNCTION

// Property: Fix Checking — no raw Firebase error surfaces
FOR ALL identifier WHERE isBugCondition_Bug1(identifier) DO
  result ← onLoginClicked'(identifier, password)
  ASSERT userFacingError(result) IN { "Invalid phone number format.",
                                      "Invalid email format.",
                                      "Please enter a valid phone number or email." }
  ASSERT authRepository.login NOT called
END FOR

// Property: Preservation Checking
FOR ALL identifier WHERE NOT isBugCondition_Bug1(identifier) DO
  ASSERT onLoginClicked'(identifier, password) = onLoginClicked(identifier, password)
END FOR
```

### Bug 2 — Sign-Up Step 2 Navigation

```pascal
FUNCTION isBugCondition_Bug2(location)
  INPUT: location of type String
  OUTPUT: boolean

  RETURN location.isNotBlank()
  // Bug triggers when a valid location is entered and wizard still navigates away
END FUNCTION

// Property: Fix Checking — Step 3 is reached
FOR ALL location WHERE isBugCondition_Bug2(location) DO
  result ← onLocationContinue'(location)
  ASSERT currentStep(result) = 3
  ASSERT LoginActivity NOT started
  ASSERT wizardActivity.isFinishing = false
END FOR

// Property: Preservation Checking
FOR ALL location WHERE NOT isBugCondition_Bug2(location) DO
  ASSERT onLocationContinue'(location) = onLocationContinue(location)
  // blank location still shows field error, no step change
END FOR
```

### Bug 3 — Weak Password at Sign-Up

```pascal
FUNCTION isBugCondition_Bug3(password)
  INPUT: password of type String
  OUTPUT: boolean

  rules ← ValidationResult.evaluatePassword(password, password).passwordRules
  RETURN NOT rules.allMet
  // Bug triggers when a weak/invalid password is submitted without rejection
END FUNCTION

// Property: Fix Checking — submission blocked
FOR ALL password WHERE isBugCondition_Bug3(password) DO
  result ← onNameContinue'(firstName, lastName, email, password)
  ASSERT step unchanged (still step 1)
  ASSERT authRepository.registerWithEmail NOT called
  ASSERT view.showFieldError("password", _) called
END FOR

// Property: Preservation Checking
FOR ALL password WHERE NOT isBugCondition_Bug3(password) DO
  ASSERT onNameContinue'(firstName, lastName, email, password)
         proceeds to registration as before
END FOR
```
