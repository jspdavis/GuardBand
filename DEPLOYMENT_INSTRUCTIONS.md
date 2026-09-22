# GuardBand Security Rules - Deployment Instructions

## 🎯 Task: Deploy Security Rules to Firebase Console

The Security Rules have been prepared and are ready for manual deployment through the Firebase Console.

---

## 📋 Step-by-Step Deployment

### Step 1: Open Firebase Console
Go to: https://console.firebase.google.com/project/guardband-aae65/database

### Step 2: Navigate to Rules Tab
Click the **"Rules"** tab at the top of the Realtime Database page

### Step 3: Copy the Rules
The rules are located in: `database.rules.json`

Or copy directly from here:

```json
{
  "rules": {
    "devices": {
      "$deviceId": {
        "latest": {
          ".read": true,
          ".write": true,
          ".validate": "newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
          "type": {
            ".validate": "newData.val() === 'PANIC' || newData.val() === 'CHECKIN' || newData.val() === 'LOW_BATTERY' || newData.val() === 'TRACKING_UPDATE'"
          },
          "sequenceId": {
            ".validate": "newData.isNumber()"
          },
          "battery": {
            "percent": {
              ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 100"
            },
            "isCharging": {
              ".validate": "newData.isBoolean()"
            }
          }
        },
        "history": {
          "$sequenceId": {
            ".read": true,
            ".write": true,
            ".validate": "newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
            "type": {
              ".validate": "newData.val() === 'PANIC' || newData.val() === 'CHECKIN' || newData.val() === 'LOW_BATTERY' || newData.val() === 'TRACKING_UPDATE'"
            }
          }
        }
      }
    }
  }
}
```

### Step 4: Paste into Rules Editor
1. Select all existing rules in the editor
2. Delete them
3. Paste the new rules from above
4. Review to ensure formatting is correct

### Step 5: Publish
Click the blue **"Publish"** button at the top right

### Step 6: Confirm Publication
Wait for the success message: "Your rules have been successfully published"

---

## 🧪 Testing After Deployment

### Test 1: Valid Alert (Should Succeed ✅)

**PowerShell Command:**
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "PANIC",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false},
    "sequenceId": 1
  }'
```

**Expected Result:** Returns the JSON data (200 OK)

### Test 2: Invalid Alert - Missing Field (Should Fail ❌)

**PowerShell Command:**
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "PANIC",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false}
  }'
```

**Expected Result:** Error with "Permission denied" message

### Test 3: Invalid Alert - Bad Type (Should Fail ❌)

**PowerShell Command:**
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "INVALID_TYPE",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false},
    "sequenceId": 1
  }'
```

**Expected Result:** Error with "Permission denied" message

---

## ✅ Verification Checklist

After deployment and testing, confirm:

- [ ] Rules published successfully in Firebase Console
- [ ] Rules visible in Rules tab
- [ ] Test 1 (valid alert) succeeded - returned JSON data
- [ ] Test 2 (missing field) failed - showed "Permission denied"
- [ ] Test 3 (invalid type) failed - showed "Permission denied"
- [ ] Data visible in Data tab at `/devices/guardband-001/latest`
- [ ] Screenshot captured of published rules

---

## 📸 Documentation to Provide

Please provide:

1. **Screenshot or text copy of the published rules** from Firebase Console Rules tab
2. **Confirmation that Test 1 succeeded** (valid alert was written)
3. **Confirmation that Test 2 failed** (missing field was rejected)
4. **Confirmation that Test 3 failed** (invalid type was rejected)
5. **Screenshot of data** in Firebase Console Data tab (optional but helpful)

---

## 🔍 What These Rules Do

### Validation Enforced:
- ✅ Requires: schemaVersion, deviceId, type, timestamp, battery, sequenceId
- ✅ Alert type must be: PANIC, CHECKIN, LOW_BATTERY, or TRACKING_UPDATE
- ✅ sequenceId must be a number
- ✅ battery.percent must be 0-100
- ✅ battery.isCharging must be boolean

### Paths Protected:
- `/devices/{deviceId}/latest` - Latest alert status (overwrites)
- `/devices/{deviceId}/history/{sequenceId}` - Historical alerts (appends)

### Access:
- **Read:** Open (no authentication required)
- **Write:** Open but validated (rules enforce schema)

---

## 🐛 Troubleshooting

**Problem:** Rules editor shows syntax error  
**Solution:** Ensure JSON is properly formatted, check for missing commas or braces

**Problem:** Test 1 (valid alert) fails  
**Solution:** 
- Wait 10-20 seconds after publishing rules
- Verify `.json` suffix on URL
- Check all required fields are present

**Problem:** All tests fail  
**Solution:** Rules may not be published - check Rules tab shows the new rules

**Problem:** All tests succeed (including invalid ones)  
**Solution:** Rules not properly deployed - republish and wait

---

## 📚 Additional Resources

- **MANUAL_TEST_GUIDE.md** - More test commands and examples
- **database.rules.json** - The rules file
- **DIRECT_DATABASE_APPROACH.md** - Architecture explanation
- **RULES_DEPLOYMENT_STATUS.md** - Deployment checklist

---

## 🎓 Summary

**What:** Deploy Security Rules to Firebase Realtime Database  
**Where:** Firebase Console → Database → Rules tab  
**How:** Copy from database.rules.json, paste, publish  
**Test:** Run 3 test commands (1 valid, 2 invalid)  
**Report:** Confirm valid succeeded, invalid failed  

This replaces the Cloud Function approach and works on the free Spark plan! 🎉
