# Security Rules Deployment Status

## Current Status: Ready for Manual Deployment

The Security Rules have been updated in `database.rules.json` with the simplified validation approach.

---

## Rules to Deploy

The following rules are ready to be published in Firebase Console:

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

---

## Deployment Steps

### Option 1: Via Firebase Console (Recommended)

1. Open: https://console.firebase.google.com/project/guardband-aae65/database
2. Click **"Rules"** tab
3. Copy the rules from `database.rules.json` (above)
4. Paste into the editor
5. Click **"Publish"**
6. Wait for confirmation message

### Option 2: Via Firebase CLI

```powershell
firebase deploy --only database
```

---

## What These Rules Validate

### ✅ Required Fields
- `schemaVersion`
- `deviceId`
- `type`
- `timestamp`
- `battery` (with `percent` and `isCharging`)
- `sequenceId`

### ✅ Type Validation
- `type` must be one of: PANIC, CHECKIN, LOW_BATTERY, TRACKING_UPDATE
- `sequenceId` must be a number
- `battery.percent` must be a number between 0-100
- `battery.isCharging` must be a boolean

### ✅ Access Control
- `.read`: true (public read access)
- `.write`: true (public write access, validated by rules)

---

## Testing After Deployment

### Quick Test Command (PowerShell):

**Valid alert (should succeed):**
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

**Invalid alert (should fail):**
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

---

## Verification Checklist

After deploying and testing, verify:

- [ ] Rules are visible in Firebase Console Rules tab
- [ ] Valid PANIC alert succeeds (200 OK)
- [ ] Invalid type alert fails (403 Permission denied)
- [ ] Missing sequenceId fails (403 Permission denied)
- [ ] Battery out of range fails (403 Permission denied)
- [ ] Data appears in Firebase Console Data tab at `/devices/guardband-001/latest`
- [ ] Screenshot or copy of published rules captured

---

## Expected Test Results

| Test Case | Expected Result |
|-----------|----------------|
| Valid PANIC alert | ✅ 200 OK - Data written |
| Valid CHECKIN alert | ✅ 200 OK - Data written |
| Valid LOW_BATTERY alert | ✅ 200 OK - Data written |
| Valid TRACKING_UPDATE alert | ✅ 200 OK - Data written |
| Missing sequenceId | ❌ 403 Permission denied |
| Invalid type (e.g., "INVALID_TYPE") | ❌ 403 Permission denied |
| Battery percent > 100 | ❌ 403 Permission denied |
| Battery percent < 0 | ❌ 403 Permission denied |
| Missing battery object | ❌ 403 Permission denied |

---

## Documentation Reference

- **MANUAL_TEST_GUIDE.md** - Detailed testing commands
- **database.rules.json** - Rules file (ready to deploy)
- **DIRECT_DATABASE_APPROACH.md** - Architecture explanation
- **DEPLOY_AND_TEST.md** - Quick start guide

---

## Next Steps

1. ✅ Copy rules from `database.rules.json`
2. ⏳ Paste into Firebase Console Rules editor
3. ⏳ Click "Publish"
4. ⏳ Run test commands from MANUAL_TEST_GUIDE.md
5. ⏳ Verify both valid and invalid cases behave correctly
6. ⏳ Take screenshot of published rules
7. ⏳ Report back with test results

---

**File Location:** `d:\AndroidStudioProjects\GuardBand\database.rules.json`  
**Firebase Console:** https://console.firebase.google.com/project/guardband-aae65/database  
**Status:** Ready for manual deployment via Firebase Console
