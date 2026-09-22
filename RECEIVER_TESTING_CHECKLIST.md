# Alert Receiver - Testing Checklist

## Pre-Test Setup

- [ ] Firebase Security Rules deployed and working
- [ ] Mock Sender app installed on Device A (or Emulator A)
- [ ] Main GuardBand app installed on Device B (or Emulator B)
- [ ] Both devices connected to internet
- [ ] Firebase project: guardband-aae65

---

## Build Instructions

### Option 1: Android Studio (Recommended)
```
1. Open Android Studio
2. File → Sync Project with Gradle Files
3. Wait for sync to complete
4. Build → Make Project
5. Run → Run 'app'
6. Select target device/emulator
```

### Option 2: Command Line
```bash
# Windows
.\gradlew.bat assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk

# Mac/Linux
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Test Scenario 1: First Alert (PANIC)

### On Mock Sender (Device A):
- [ ] Open Mock Sender app
- [ ] Select alert type: PANIC
- [ ] Tap "Send Alert"
- [ ] Verify success message appears

### On Receiver (Device B):
- [ ] Open GuardBand app
- [ ] Tap "Open Alert Receiver" button
- [ ] **Verify Latest Alert Card:**
  - [ ] Card is visible (not "No alerts")
  - [ ] Type: PANIC
  - [ ] Timestamp displayed
  - [ ] Location: 10.3157, 123.8854
  - [ ] Battery: 74% (Not charging)
  - [ ] Sequence: 1
- [ ] **Verify Alert History:**
  - [ ] History list visible (not "No history")
  - [ ] 1 item in list
  - [ ] Item shows PANIC
  - [ ] Item background is red
  - [ ] Sequence: 1

**Expected:** Alert appears automatically within 1-2 seconds! ✅

---

## Test Scenario 2: Multiple Alert Types

### Send CHECKIN Alert:
- [ ] Mock Sender: Select CHECKIN, send
- [ ] Receiver: Latest card updates to CHECKIN
- [ ] Receiver: History shows 2 items (CHECKIN on top)
- [ ] Receiver: CHECKIN item background is green
- [ ] Receiver: Sequence: 2

### Send LOW_BATTERY Alert:
- [ ] Mock Sender: Select LOW_BATTERY, send
- [ ] Receiver: Latest card updates to LOW_BATTERY
- [ ] Receiver: History shows 3 items (LOW_BATTERY on top)
- [ ] Receiver: LOW_BATTERY item background is orange
- [ ] Receiver: Battery shows 15%
- [ ] Receiver: Sequence: 3

### Send TRACKING_UPDATE Alert:
- [ ] Mock Sender: Select TRACKING_UPDATE, send
- [ ] Receiver: Latest card updates to TRACKING_UPDATE
- [ ] Receiver: History shows 4 items (TRACKING_UPDATE on top)
- [ ] Receiver: TRACKING_UPDATE item background is blue
- [ ] Receiver: Battery shows charging status
- [ ] Receiver: Sequence: 4

**Expected:** History order: [4, 3, 2, 1] (most recent first) ✅

---

## Test Scenario 3: Real-Time Updates

### Setup:
- [ ] Receiver screen open and visible
- [ ] Mock Sender ready to send

### Test:
- [ ] Send alert from Mock Sender
- [ ] **DO NOT touch receiver screen**
- [ ] Watch for automatic update (1-2 seconds)

**Expected:** Screen updates without any user interaction! ✅

---

## Test Scenario 4: Persistence

- [ ] Send an alert (any type)
- [ ] Verify it appears on receiver
- [ ] **Kill the GuardBand app** (swipe away)
- [ ] Reopen GuardBand app
- [ ] Navigate to Receiver screen
- [ ] Verify alert still visible

**Expected:** Data persists (loaded from Firebase) ✅

---

## Test Scenario 5: No Network

- [ ] Send an alert (to populate database)
- [ ] Verify it appears
- [ ] Turn off WiFi/Mobile data on receiver device
- [ ] Send another alert from sender
- [ ] Verify receiver shows old data (not updated)
- [ ] Turn network back on
- [ ] Wait 5 seconds
- [ ] Verify receiver updates with new alert

**Expected:** Graceful offline handling, syncs when online ✅

---

## Test Scenario 6: Empty State

### Fresh Database Test:
- [ ] Go to Firebase Console
- [ ] Delete /devices/guardband-001/latest
- [ ] Delete /devices/guardband-001/history
- [ ] Refresh Receiver screen (or reopen)
- [ ] Verify shows "No latest alert available"
- [ ] Verify shows "No alert history available"

**Expected:** Proper empty state messages ✅

---

## Visual Verification

### Latest Alert Card:
- [ ] Material Design card with elevation
- [ ] Clear typography
- [ ] All fields aligned properly
- [ ] No overlapping text

### History List:
- [ ] Items have proper spacing
- [ ] Color coding works:
  - Red = PANIC
  - Orange = LOW_BATTERY
  - Green = CHECKIN
  - Blue = TRACKING_UPDATE
- [ ] Text readable on all backgrounds
- [ ] Scrolls smoothly
- [ ] No jank or lag

### Overall Layout:
- [ ] Toolbar with back button works
- [ ] Content doesn't overlap system bars
- [ ] Progress bar appears during load
- [ ] Responsive to different screen sizes

---

## Firebase Console Verification

Cross-check with Firebase Console:

1. Open: https://console.firebase.google.com/project/guardband-aae65/database
2. Navigate to Data tab
3. Check: /devices/guardband-001/latest
   - [ ] Matches what's shown on receiver screen
4. Check: /devices/guardband-001/history
   - [ ] All sent alerts are present
   - [ ] Sequence IDs match

---

## Performance Check

- [ ] Screen loads in < 2 seconds
- [ ] Real-time updates appear in < 2 seconds
- [ ] History list scrolls smoothly (60fps)
- [ ] No ANR (App Not Responding) dialogs
- [ ] No crashes
- [ ] Memory usage reasonable (check Android Profiler)

---

## Error Handling

### Invalid Data Test:
If you have Firebase access, manually edit data:

1. Firebase Console → /latest
2. Remove "sequenceId" field
3. Check receiver screen
4. Verify app doesn't crash
5. Restore valid data
6. Verify app recovers

**Expected:** Graceful error handling ✅

---

## Integration Test

### Full Pipeline Test:
1. [ ] Clear all data in Firebase
2. [ ] Open Receiver (shows empty state)
3. [ ] Send PANIC from Mock Sender
4. [ ] Verify appears on Receiver live
5. [ ] Send 3 more alerts (different types)
6. [ ] Verify all 4 appear in correct order
7. [ ] Verify latest card shows most recent
8. [ ] Verify color coding correct
9. [ ] Verify all data fields accurate

**Expected:** Complete end-to-end pipeline working! ✅

---

## Bug Report Template

If issues found:

```
**Bug:** [Brief description]
**Steps to Reproduce:**
1. 
2. 
3. 

**Expected:** [What should happen]
**Actual:** [What actually happened]
**Device:** [Model and Android version]
**Screenshot:** [If applicable]
**Logs:** [Logcat output if crash]
```

---

## Success Criteria

All tests must pass:
- ✅ Alerts sent from Mock Sender appear on Receiver
- ✅ Real-time updates work (no manual refresh needed)
- ✅ All 4 alert types display correctly
- ✅ History sorted correctly (most recent first)
- ✅ Color coding works
- ✅ Data persists after app restart
- ✅ No crashes or ANRs
- ✅ Proper empty states
- ✅ Firebase data matches UI

---

## Report Template

After testing, report:

```markdown
# Receiver Testing Report

**Date:** [Date]
**Tester:** [Name]
**Devices:** 
- Sender: [Device/Emulator info]
- Receiver: [Device/Emulator info]

## Test Results

- [ ] Build successful
- [ ] Scenario 1: First Alert - PASS/FAIL
- [ ] Scenario 2: Multiple Types - PASS/FAIL
- [ ] Scenario 3: Real-time Updates - PASS/FAIL
- [ ] Scenario 4: Persistence - PASS/FAIL
- [ ] Scenario 5: No Network - PASS/FAIL
- [ ] Scenario 6: Empty State - PASS/FAIL

## Screenshots
[Attach screenshots of receiver screen with alerts]

## Issues Found
[List any bugs or issues]

## Confirmation
✅ End-to-end pipeline verified working:
   Mock Sender → Firebase → Receiver Screen

**Overall Status:** PASS / FAIL
```

---

**Ready to test!** Build the app and work through this checklist systematically. 🚀
