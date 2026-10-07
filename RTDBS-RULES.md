{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid",
        "emergency_contacts": {
          "$contactId": {
            ".read": "auth != null && auth.uid == $uid",
            ".write": "auth != null && auth.uid == $uid"
          }
        }
      }
    },
    "phone_index": {
      "$phone": {
        ".read": true,
        ".write": "auth != null"
      }
    },
    "email_index": {
      "$emailKey": {
        ".read": true,
        ".write": "auth != null"
      }
    },
    "password_resets": {
      "$emailKey": {
        ".read": true,
        ".write": true
      }
    },
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
        },
        "command": {
          ".read": true,
          ".write": true,
          "led": {
            ".validate": "newData.isBoolean()"
          }
        },
        "status": {
          ".read": true,
          ".write": true,
          "led": {
            ".validate": "newData.isBoolean()"
          },
          "online": {
            ".validate": "newData.isBoolean()"
          },
          "lastUpdate": {
            ".validate": "newData.isNumber()"
          }
        }
      }
    }
  }
}
