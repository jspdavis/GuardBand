# GuardBand Security Rules - Quick Reference

## 🎯 What You Need to Know

Your Firebase Realtime Database Security Rules are **ready to deploy**. This approach is **better and simpler** than using a Cloud Function.

---

## 🚀 Deploy (1 Command)

```powershell
firebase deploy --only database
```

## 🧪 Test (1 Command)

```powershell
.\test-database-rules.ps1
```

---

## 📊 Why Security Rules > Cloud Function

| Aspect | Cloud Function ❌ | Security Rules ✅ |
|--------|------------------|-------------------|
| Firebase Plan | Blaze (paid, requires credit card) | Spark (100% free) |
| Setup | Install Node.js + npm + dependencies | Firebase CLI only |
| Deploy Command | `firebase deploy --only functions` | `firebase deploy --only database` |
| Latency | Device → Function → Database (2 hops) | Device → Database (1 hop) |
| Cost at Scale | $0.40 per million invocations | Free (within 1GB storage + 10GB bandwidth) |
| Complexity | Manage function code + rules | Rules only |
| Debugging | Function logs + error tracking | Rules Playground |
| ESP32 Compatible | ✅ Yes (HTTPS POST) | ✅ Yes (HTTPS PUT) |

**Winner:** Security Rules ✅ - Simpler, faster, and free!

---

## 🔍 How It Works

### Before: Cloud Function Approach (Not Used)
```
Sender → Cloud Function → Database
         (validates)     (writes)
```
**Problem:** Requires Blaze plan ($$$)

### Now: Direct Database Approach (Current)
```
Sender → Database
         (Security Rules validate)
```
**Benefit:** Works on free Spark plan!

---

## 📋 What Gets Validated

Security Rules enforce the same validation as the Cloud Function would have:

✅ **Required Fields**
- schemaVersion = "1.0"
- deviceId (matches URL path)
- type (PANIC, CHECKIN, LOW_BATTERY, or TRACKING_UPDATE)
- timestamp (string)
- location (lat, lng, accuracyMeters)
- battery (percent 0-100, isCharging boolean)
- sequenceId (number)

✅ **Data Types**
- Strings are strings
- Numbers are numbers
- Booleans are booleans

✅ **Business Rules**
- Alert type must be one of 4 valid values
- Battery percent must be 0-100
- DeviceId in payload must match URL
- SequenceId must match URL (for history)
- History entries can't be overwritten

---

## 🎯 REST API Endpoints

### Base URL
```
https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app
```

### Write to /latest (overwrite latest status)
```http
PUT /devices/guardband-001/latest.json
Content-Type: application/json

{...payload...}
```

### Write to /history (append to history log)
```http
PUT /devices/guardband-001/history/{sequenceId}.json
Content-Type: application/json

{...payload...}
```

**Important:** 
- Use `PUT` method (not POST)
- Add `.json` suffix
- Send same payload to both endpoints

---

## ✅ Example: Valid Request

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

**Response:** Returns the written data (200 OK)

---

## ❌ Example: Invalid Request

```powershell
# Missing sequenceId field
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

**Response:** Error - "Permission denied" (401/403)

---

## 🧪 Automated Test Suite

Run `.\test-database-rules.ps1` to test:

1. ✅ Valid PANIC → /latest
2. ✅ Valid PANIC → /history/1
3. ✅ Valid CHECKIN → /latest
4. ✅ Valid CHECKIN → /history/2
5. ✅ Valid LOW_BATTERY → /latest
6. ✅ Valid LOW_BATTERY → /history/3
7. ✅ Valid TRACKING_UPDATE → /latest
8. ✅ Valid TRACKING_UPDATE → /history/4
9. ❌ Missing sequenceId (should reject)
10. ❌ Invalid alert type (should reject)
11. ❌ Battery out of range (should reject)
12. ❌ DeviceId mismatch (should reject)
13. ❌ Missing location (should reject)
14. ❌ Missing battery (should reject)

**Expected:** 14/14 tests pass

---

## 📁 Key Files

```
GuardBand/
├── database.rules.json                # ⭐ Deploy this
├── test-database-rules.ps1            # ⭐ Run this to test
│
├── README_SECURITY_RULES.md           # ⭐ This file (quick ref)
├── DEPLOY_AND_TEST.md                 # Quick start guide
├── SECURITY_RULES_SUMMARY.md          # Detailed summary
├── DIRECT_DATABASE_APPROACH.md        # Full architecture
│
└── functions/                         # Reference only
    └── index.js                       # (not deployed)
```

---

## 🔄 Update Your Sender App

### Old (Cloud Function approach):
```kotlin
val url = "https://region-project.cloudfunctions.net/ingestAlert"
// POST request
```

### New (Security Rules approach):
```kotlin
val baseUrl = "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app"
val deviceId = "guardband-001"
val sequenceId = alert.sequenceId

// Write to /latest
val latestUrl = "$baseUrl/devices/$deviceId/latest.json"
// PUT request with full payload

// Write to /history
val historyUrl = "$baseUrl/devices/$deviceId/history/$sequenceId.json"
// PUT request with full payload
```

**Changes:**
- ❌ ~~POST to function URL~~
- ✅ PUT to database REST API
- ✅ Two separate requests (latest + history)
- ✅ Add `.json` suffix

---

## 🎓 Complete Checklist

- [ ] **Deploy rules:** `firebase deploy --only database`
- [ ] **Test rules:** `.\test-database-rules.ps1`
- [ ] **Verify in console:** Check Rules and Data tabs
- [ ] **Update sender app:** Change to PUT requests with new URLs
- [ ] **Test end-to-end:** Send alert from app, verify in Firebase

---

## 🐛 Quick Troubleshooting

**Problem:** Rules not working  
**Solution:** Wait 10-20 seconds after deploy, check Rules tab in console

**Problem:** All writes rejected  
**Solution:** Verify `.json` suffix, check all required fields present

**Problem:** Need to debug  
**Solution:** Use Rules Playground in Firebase Console

---

## 📚 Documentation Map

- **New here?** → You're reading it! (README_SECURITY_RULES.md)
- **Ready to deploy?** → See DEPLOY_AND_TEST.md
- **Need architecture details?** → See DIRECT_DATABASE_APPROACH.md
- **Want full summary?** → See SECURITY_RULES_SUMMARY.md

---

## 💡 Key Takeaways

1. ✅ Security Rules = Free tier compatible
2. ✅ Same validation as Cloud Function would provide
3. ✅ Lower latency (direct to database)
4. ✅ Simpler deployment (one command)
5. ✅ Perfect for school project budget
6. ✅ Works great with ESP32 AT commands

---

## 🚀 Ready to Deploy?

```powershell
# Make sure you're logged in
firebase login

# Deploy the rules
cd d:\AndroidStudioProjects\GuardBand
firebase deploy --only database

# Test them
.\test-database-rules.ps1

# Done! 🎉
```

---

**Status:** ✅ Ready to deploy  
**Time to deploy:** ~1 minute  
**Cost:** $0 (free tier)  
**Benefit:** Same validation, simpler architecture  

Good luck with your GuardBand project! 🎓🚀
