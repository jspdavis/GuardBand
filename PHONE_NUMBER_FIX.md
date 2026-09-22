# Phone Number Storage Fix

## 🐛 Problem Identified

Phone numbers were not being stored in the Firebase Realtime Database during signup, even though users could enter them and use them for login.

## 🔍 Root Cause

**File:** `app/src/main/java/com/example/guardband/ui/signup/SignUpPresenter.kt`  
**Line:** 114

The `SignUpPresenter` was calling `authRepository.registerWithEmail()` but **not passing the `phone` parameter**:

### Before (Broken):
```kotlin
authRepository.registerWithEmail(
    email = trimmedEmail,
    password = password,
    firstName = this.firstName,
    lastName = this.lastName,
    // ❌ phone parameter missing!
    onSuccess = { ... },
    onError = { ... }
)
```

Since the `phone` parameter in `AuthRepository.registerWithEmail()` has a default value of `""` (empty string), when not provided, it would save an empty phone number to the database.

## ✅ Solution

Added the `phone` parameter to the registration call, detecting whether the user entered a phone number or email:

### After (Fixed):
```kotlin
authRepository.registerWithEmail(
    email = trimmedEmail,
    password = password,
    firstName = this.firstName,
    lastName = this.lastName,
    phone = if (!emailOk && phoneOk) trimmedEmail else "", // ✅ Pass phone if user entered phone number
    onSuccess = { ... },
    onError = { ... }
)
```

## 🎯 How It Works

1. **User enters phone number** (e.g., +639171234567) in the email field during signup
2. **Validation detects** it's a phone number (matches phone regex but not email pattern)
3. **`phoneOk = true`, `emailOk = false`**
4. **Passes phone to registration**: `phone = trimmedEmail`
5. **AuthRepository saves**:
   - Creates auth account with synthetic email (`639171234567@guardband.phone`)
   - Stores phone in `/users/{uid}/phone`
   - Creates phone index at `/phone_index/{phone}` → `{uid, email}`

## 📊 Database Structure After Fix

When user signs up with phone `+639171234567`:

```
/users/{uid}
  ├── email: "639171234567@guardband.phone"
  ├── phone: "+639171234567"              ← ✅ Now stored!
  ├── firstName: "John"
  ├── lastName: "Doe"
  └── ...

/phone_index/+639171234567
  ├── uid: "{uid}"
  └── email: "639171234567@guardband.phone"  ← ✅ Enables phone login lookup!
```

## 🔐 Login Flow (Already Working)

The login flow in `AuthRepository` already supported phone login:

1. **User enters phone number** in login screen
2. **`resolveLoginEmail()`** method:
   - Checks `/phone_index/{phone}` for associated email
   - Falls back to scanning `/users` if index missing
3. **Returns synthetic email** (`639171234567@guardband.phone`)
4. **Firebase Auth signs in** with that email and password

**This part was already correct!** The only issue was that signup wasn't passing the phone number.

## ✅ Verification Checklist

After this fix, verify:

- [ ] Sign up with phone number (e.g., +639171234567)
- [ ] Check Firebase Console → Realtime Database → `/users/{uid}`
  - [ ] `phone` field is present and has the correct phone number
- [ ] Check Firebase Console → Realtime Database → `/phone_index/{phone}`
  - [ ] Entry exists with `uid` and `email`
- [ ] Log out
- [ ] Log in using the phone number and password
  - [ ] Login succeeds ✅
- [ ] Check profile screen
  - [ ] Phone number displays correctly

## 🧪 Testing Instructions

### Test 1: Phone Number Signup
```
1. Open app
2. Go to Sign Up
3. Enter:
   - First Name: Test
   - Last Name: User
   - Email/Phone: +639171234567
   - Password: Test123!
4. Complete signup wizard
5. Go to Firebase Console → Database
6. Navigate to /users/{uid}
7. Verify 'phone' field = "+639171234567"
8. Navigate to /phone_index/+639171234567
9. Verify entry exists
```

### Test 2: Phone Number Login
```
1. Log out
2. Go to Log In
3. Enter:
   - Phone/Email: +639171234567
   - Password: Test123!
4. Tap Log In
5. Verify successful login
```

### Test 3: Email Signup (Still Works)
```
1. Sign up with email: test@example.com
2. Check database
3. Verify:
   - email field = "test@example.com"
   - phone field = "" (empty, which is correct)
```

## 📝 Files Modified

### 1. SignUpPresenter.kt
**Change:** Added `phone` parameter to `registerWithEmail()` call

**Lines changed:** 118

**Impact:** Phone numbers now properly saved during signup

## 🎓 Technical Details

### Phone Detection Logic
```kotlin
val phoneOk = Regex("^\\+?\\d{10,15}$").matches(trimmedEmail)
val emailOk = Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
```

- **Phone regex**: Matches 10-15 digits, optional + prefix
- **Email pattern**: Android's built-in email validator

### Conditional Phone Passing
```kotlin
phone = if (!emailOk && phoneOk) trimmedEmail else ""
```

- **If phone entered**: Pass phone number to registration
- **If email entered**: Pass empty string (email-only signup)

### AuthRepository Handling
The repository automatically:
- Converts phone to synthetic email for Firebase Auth
- Stores actual phone number in user profile
- Creates phone index for login lookup

## 🔄 Backward Compatibility

This fix is **backward compatible**:

- **Existing users with email**: No impact (phone stays empty)
- **Existing users with phone but no stored phone**: 
  - Login still works (email index exists)
  - Can update profile to add phone later
- **New users**: Phone properly stored from signup

## 📚 Related Code

### AuthRepository.registerWithEmail()
```kotlin
fun registerWithEmail(
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    phone: String = "", // ← Default empty if not provided
    onSuccess: (User) -> Unit,
    onError: (String) -> Unit
)
```

### Phone to Auth Email Conversion
```kotlin
private fun phoneToAuthEmail(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return "$digits@guardband.phone"
}
```

### User Model
```kotlin
data class User(
    val phone: String = "",  // ← This field
    // ... other fields
)

fun toMap(): Map<String, Any?> = mapOf(
    "phone" to phone,  // ← Included in database save
    // ... other fields
)
```

## 🎉 Summary

**Status:** ✅ **Fixed**

**Change:** One line added to pass phone parameter during signup

**Result:** 
- Phone numbers are now stored in the database
- Phone login continues to work perfectly
- Email signup unaffected

**Next:** Build and test the app to verify the fix works end-to-end.
