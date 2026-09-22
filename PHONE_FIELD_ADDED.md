# Phone Number Field Added to Signup

## ✅ Changes Made

I've added a dedicated phone number input field to the signup form so users can enter their phone number separately from their email, and use it for logging in later.

---

## 📝 Files Modified (5 files)

### 1. fragment_signup_name.xml
**Location:** `app/src/main/res/layout/fragment_signup_name.xml`

**Changes:**
- Added new phone number field between Email and Password
- Label: "Phone Number"
- Helper text: "Optional - Can be used for login"
- Input type: `phone`
- Placeholder: "e.g., +639171234567"
- TextInputLayout ID: `tilPhone`
- EditText ID: `etPhone`

### 2. SignUpNameFragment.kt
**Location:** `app/src/main/java/com/example/guardband/ui/signup/SignUpNameFragment.kt`

**Changes:**
- Updated `Host` interface to include `phone` parameter:
  ```kotlin
  fun onNameContinue(firstName: String, lastName: String, email: String, phone: String, password: String)
  ```
- Updated `btnContinue` click listener to pass phone field value
- Added "phone" case to `showErrors()` method for validation error display

### 3. SignUpWizardActivity.kt
**Location:** `app/src/main/java/com/example/guardband/ui/signup/SignUpWizardActivity.kt`

**Changes:**
- Updated `onNameContinue()` to accept `phone` parameter
- Added "phone" to the `showFieldError()` when clause

### 4. SignUpContract.kt
**Location:** `app/src/main/java/com/example/guardband/ui/signup/SignUpContract.kt`

**Changes:**
- Updated `Presenter.onNameContinue()` interface to include `phone` parameter

### 5. SignUpPresenter.kt
**Location:** `app/src/main/java/com/example/guardband/ui/signup/SignUpPresenter.kt`

**Changes:**
- Updated `onNameContinue()` method signature to accept `phone` parameter
- **Changed email validation** - now requires actual email format (not phone)
- Added **separate phone validation** - optional but validates format if provided
- Passes `trimmedPhone` directly to `authRepository.registerWithEmail()`
- Removed old logic that tried to use email field for both email and phone

---

## 🎯 How It Works Now

### User Signup Flow

1. **User fills out signup form:**
   - First Name: John
   - Last Name: Doe
   - Email: `john.doe@example.com` (required, must be valid email)
   - Phone Number: `+639171234567` (optional, but validated if provided)
   - Password: Test123!

2. **Validation:**
   - Email must be valid email format
   - Phone is optional but if provided, must match phone regex: `^\\+?\\d{10,15}$`
   - Both fields are now separate and independent

3. **Registration:**
   ```kotlin
   authRepository.registerWithEmail(
       email = "john.doe@example.com",
       password = "Test123!",
       firstName = "John",
       lastName = "Doe",
       phone = "+639171234567",  // ✅ Now passed correctly
       onSuccess = { ... },
       onError = { ... }
   )
   ```

4. **Database Storage:**
   ```
   /users/{uid}
     ├── email: "john.doe@example.com"
     ├── phone: "+639171234567"         ← ✅ Stored!
     ├── firstName: "John"
     ├── lastName: "Doe"
     └── ...
   
   /phone_index/+639171234567
     ├── uid: "{uid}"
     └── email: "john.doe@example.com"  ← ✅ Enables phone login!
   ```

### Login Flow (Already Working)

Users can now log in with **either** email or phone:

**Login with Email:**
```
Email/Phone: john.doe@example.com
Password: Test123!
→ Signs in directly with email
```

**Login with Phone:**
```
Email/Phone: +639171234567
Password: Test123!
→ Looks up email from phone index
→ Signs in with associated email
```

---

## 🔍 Key Changes in Validation Logic

### Before (Problematic):
- Email field accepted both email AND phone
- Phone was never explicitly captured
- Confusing UX

### After (Fixed):
```kotlin
// Email — required, must be valid email
val trimmedEmail = email.trim()
val emailOk = Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
if (trimmedEmail.isBlank()) {
    showError("Email is required")
} else if (!emailOk) {
    showError("Enter a valid email address")
}

// Phone — optional, but validated if provided
val trimmedPhone = phone.trim()
val phoneOk = trimmedPhone.isEmpty() || Regex("^\\+?\\d{10,15}$").matches(trimmedPhone)
if (!phoneOk) {
    showError("Enter a valid phone number (e.g., +639171234567)")
}
```

---

## 📊 UI Changes

### Signup Form Layout (Step 1):

```
┌────────────────────────────────────┐
│  What is your name?                │
│  This is what will be used...      │
│                                    │
│  First Name                        │
│  [________________]                │
│                                    │
│  Last Name                         │
│  [________________]                │
│                                    │
│  Email                             │
│  [________________]                │
│                                    │
│  Phone Number                      │ ← ✅ NEW FIELD
│  [________________]                │
│  Optional - Can be used for login  │
│                                    │
│  Password                          │
│  [________________] 👁             │
│                                    │
│  ☐ At least 8 characters           │
│  ☐ At least 1 uppercase letter     │
│  ☐ At least 1 lowercase letter     │
│  ☐ At least 1 number or special    │
│                                    │
│  [     CONTINUE     ]              │
└────────────────────────────────────┘
```

---

## ✅ Testing Instructions

### Test 1: Signup with Phone Number

1. Open app → Go to Sign Up
2. Fill in:
   - First Name: Test
   - Last Name: User
   - Email: test@example.com
   - Phone Number: +639171234567
   - Password: Test123!
3. Complete signup wizard
4. Check Firebase Console → `/users/{uid}`
5. Verify:
   - `email` = "test@example.com"
   - `phone` = "+639171234567" ✅
6. Check `/phone_index/+639171234567` exists ✅

### Test 2: Login with Phone Number

1. Log out
2. Go to Login
3. Enter:
   - Email/Phone: +639171234567
   - Password: Test123!
4. Tap Log In
5. **Verify:** Login succeeds ✅

### Test 3: Signup WITHOUT Phone (Optional Field)

1. Sign up with:
   - Email: another@example.com
   - Phone: (leave empty)
   - Other fields filled
2. Complete signup
3. Check database:
   - `email` = "another@example.com"
   - `phone` = "" (empty) ✅
4. Login with email should work ✅

### Test 4: Invalid Phone Format

1. Try to sign up with:
   - Phone: "12345" (too short)
2. Tap Continue
3. **Verify:** Shows error "Enter a valid phone number..." ✅

---

## 🎨 Field Details

### Phone Number Field Specifications

**Label:** "Phone Number"

**Input Type:** `phone` (shows numeric keyboard with + symbol)

**Validation:**
- **Optional:** Can be left empty
- **Format:** `^\\+?\\d{10,15}$`
  - Optional + prefix
  - 10-15 digits
  - Examples:
    - ✅ +639171234567
    - ✅ 639171234567
    - ✅ +12025551234
    - ❌ 12345 (too short)
    - ❌ abc123456789 (contains letters)

**Helper Text:** "Optional - Can be used for login"

**Placeholder:** "e.g., +639171234567"

**Error Messages:**
- "Enter a valid phone number (e.g., +639171234567)"

---

## 🔄 Login Compatibility

The existing login flow in `AuthRepository.login()` already supports phone login through the `resolveLoginEmail()` method:

1. User enters phone in login screen
2. `resolveLoginEmail()` checks `/phone_index/{phone}`
3. Retrieves associated email
4. Signs in with Firebase Auth using that email

**No changes needed to login!** ✅

---

## 📚 Related Code

### AuthRepository.registerWithEmail()
```kotlin
fun registerWithEmail(
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    phone: String = "", // ← This parameter
    onSuccess: (User) -> Unit,
    onError: (String) -> Unit
)
```

### User Profile Storage
```kotlin
val profile = User(
    uid = firebaseUser.uid,
    email = authEmail,
    phone = profilePhone,  // ← Now properly populated
    firstName = firstName,
    lastName = lastName,
    profileComplete = false
)
saveProfile(profile, onSuccess, onError)
```

### Phone Index Creation
```kotlin
if (profile.phone.isNotBlank()) {
    updates["$NODE_PHONE_INDEX/${sanitizeKey(profile.phone)}"] =
        mapOf("uid" to profile.uid, "email" to profile.email)
}
```

---

## 🎉 Summary

**Status:** ✅ **Complete**

**What Changed:**
- Added dedicated Phone Number input field to signup form
- Email field now strictly validates email format
- Phone field is optional but validated if provided
- Phone number properly stored in database
- Phone login already works (no changes needed)

**Benefits:**
- ✅ Clear separation between email and phone
- ✅ Better UX - users know what to enter where
- ✅ Optional phone field - not forced on users
- ✅ Phone can be used for login
- ✅ Stored in database and indexed properly

**Next Steps:**
1. Build the app
2. Test signup with phone number
3. Verify phone number stored in Firebase
4. Test login with phone number
5. Verify it works end-to-end

---

**Files Modified:** 5
**Lines Added:** ~50
**Build Status:** Ready for testing
