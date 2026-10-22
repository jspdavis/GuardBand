{
  "//": [
    "GuardBand Realtime Database Security Rules.",
    "",
    "STATUS 2026-10-19: PROPOSED, NOT DEPLOYED. The devices block below is",
    "Option A+ from docs/database-rules.md section 4, PENDING JUL'S",
    "DECISION. The users block is unchanged from the rules deployed on",
    "2026-10-11. Until this is published, the live ruleset is the",
    "2026-10-11 text kept in docs/database-rules.md section 9, which is also",
    "the rollback. Once published, delete this STATUS paragraph.",
    "",
    "This file, with every '//' key stripped, is meant to BE the live",
    "ruleset. The Console rejects the '//' keys, so strip them before",
    "pasting (docs/database-rules.md section 7 has a one-line command).",
    "Change the rules HERE first, then paste the whole stripped file over",
    "the Console's and publish, so this file and the Console never drift.",
    "",
    "Every database path the app and the mock sender use, and who may read",
    "or write it, is the inventory in docs/database-rules.md section 2.",
    "",
    "Project guardband-aae65, asia-southeast1, Spark plan.",
    "Related: CLAUDE.md, docs/privacy-and-consent.md, SCHEMA.md (the devices",
    "payload contract, which is authoritative for that shape)."
  ],
  "// How the 2026-10-11 deploy was done": [
    "The rules live before that date were read out of the Console and merged",
    "with this file rather than pasted over:",
    "- users/: taken from here wholesale. Live had owner-only access and no",
    "  validation at all.",
    "- devices/: live was identical to the block here, MINUS the '.read' on",
    "  history itself, which is why the Alert tab and the mock sender were",
    "  getting 401 on every history read. That one line was added.",
    "- password_resets, phone_index, email_index: their rules were removed",
    "  (password_resets was world-readable and world-writable). Removing a",
    "  rule does not remove data, so the whole database was exported and the",
    "  three nodes then deleted in the Data tab, also on 2026-10-11.",
    "",
    "Before publishing, every test below was run against these rules in the",
    "local Database Emulator under a demo- project id (never the real one), and",
    "after publishing the history read was re-checked against the real",
    "database: 401 before, 200 after. The project has no emulator suite of its",
    "own, so the Playground list is still the test suite to run after any",
    "change."
  ],
  "// Rules Playground tests": [
    "The list lives in docs/database-rules.md section 5, one place only, so",
    "it cannot drift from a copy here. Console -> Realtime Database -> Rules",
    "-> Playground; run every test there before publishing. Tests 1 to 9",
    "are the nine that used to be listed here, unchanged."
  ],
  "// Why consent is not required here": [
    "It is tempting to add",
    "  users/$uid/.validate: newData.hasChildren(['consent'])",
    "so a profile can never exist without one. It is NOT added, because a",
    "legitimate path writes a profile with no consent child:",
    "ProfileProvisioning.ensureProfile calls saveProfile (name + email only)",
    "on any sign-in where users/{uid} is confirmed absent. That rule would",
    "fail those writes and leave the user with no profile at all.",
    "",
    "Options are written up in docs/privacy-and-consent.md. Decide there",
    "first; do not add the rule as a quick fix."
  ],
  "// devices": [
    "PENDING JUL'S DECISION: the block below is Option A+ (recommended).",
    "Options A, A+, B and C, what each breaks, and the full text of each",
    "devices block are in docs/database-rules.md section 4. Switching",
    "options means replacing the whole \"devices\" block with that one.",
    "",
    "A+ keeps reads and writes open, as deployed, with one change: a",
    "history entry can be CREATED but never overwritten or deleted, its",
    "key must be a whole number, and it gets the same field checks as",
    "latest. latest, command and status are exactly as deployed.",
    "",
    "It is still world-readable AND world-writable: '.read': true and",
    "'.write' open on the wearer's coordinates. That is not an oversight,",
    "it is what the current design needs - the debug mock sender PUTs here",
    "unauthenticated over REST, as the band's firmware will, and Track and",
    "Alert history read it. There is one shared device id (guardband-001)",
    "because nothing pairs a band to an account yet.",
    "",
    "Closing it needs pairing: a device id per account, an auth token for the",
    "band, and AlertSender taught to send ?auth=. That is a separate piece of",
    "work, and tightening the rules without it breaks both ends.",
    "",
    "Until then the Privacy Notice says plainly that this data is open."
  ],
  "rules": {
    "users": {
      "$uid": {
        "//": [
          "Owner-only, and that single .write is also what lets the owner DELETE",
          "their whole record: a removal is a write of null at this path.",
          "RTDB does not evaluate .validate rules when newData is null, so none",
          "of the validators below block Delete account. Re-test that in the",
          "Playground after any edit here (test 2 in the list at the top).",
          "",
          "There is deliberately NO rule requiring a consent child. See the",
          "'Why consent is not required here' note at the top of this file."
        ],
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid",
        "name": {
          ".validate": "newData.isString() && newData.val().length <= 100"
        },
        "email": {
          ".validate": "newData.isString() && newData.val().length <= 254"
        },
        "emergency_contacts": {
          "$contactId": {
            "//": [
              "Counts cannot be enforced here: RTDB rules cannot count",
              "children, so InputValidator.MIN_CONTACTS (1) stays the repository's",
              "job (there is no upper cap). These rules only police the",
              "shape of one contact."
            ],
            ".read": "auth != null && auth.uid == $uid",
            ".write": "auth != null && auth.uid == $uid",
            ".validate": "newData.hasChildren(['name','phone'])",
            "name": {
              ".validate": "newData.isString() && newData.val().length > 0 && newData.val().length <= 100"
            },
            "phone": {
              "//": [
                "MUST stay in step with InputValidator.normalizePhoneToE164,",
                "which is what decides the stored form. The app normalises",
                "BEFORE writing (ContactFields.validated), so this matches the",
                "stored value, never what the user typed:",
                "",
                "  09171234567   -> +639171234567",
                "  639171234567  -> +639171234567",
                "  +639171234567 -> +639171234567",
                "",
                "The app's rule, in its own words:",
                "  PH mobile  ^\\+639[0-9]{9}$          (always stored this way)",
                "  otherwise  ^\\+[0-9]{8,15}$  E.164, stored as typed,",
                "             EXCEPT anything starting +63 that is not a",
                "             PH mobile, which the app refuses outright.",
                "",
                "That exception needs a negative lookahead, which RTDB's",
                "regex does not have, so the non-PH branch is split by first",
                "digit instead:",
                "  [0-57-9][0-9]{7,14}      first digit not 6",
                "  6[0-24-9][0-9]{6,13}     first 6, second not 3",
                "Both give 8 to 15 digits in total.",
                "",
                "A landline such as +6321234567 is refused, here and in the",
                "app: an emergency contact has to be reachable by SMS."
              ],
              ".validate": "newData.isString() && newData.val().matches(/^\\+(639[0-9]{9}|[0-57-9][0-9]{7,14}|6[0-24-9][0-9]{6,13})$/)"
            },
            "relationship": {
              "//": "Optional. Stored as an empty string when left blank, never removed.",
              ".validate": "newData.isString() && newData.val().length <= 50"
            },
            "emergency": {
              "//": "Emergency designation flag. Starred contacts. Defaults to false when absent.",
              ".validate": "newData.isBoolean()"
            },
            "favorite": {
              "//": "Legacy alias for emergency designation flag.",
              ".validate": "newData.isBoolean()"
            },
            "$other": {
              ".validate": false
            }
          }
        },
        "consent": {
          "//": [
            "Written once, by UserProfileRepository.finalizeSignUp, in the same",
            "atomic updateChildren as the profile and the contacts - so a",
            "profile cannot exist without a consent record on that path.",
            "",
            "All three children are required together. A partial write would",
            "leave a record that cannot say what was agreed to, or when."
          ],
          ".validate": "newData.hasChildren(['accepted','acceptedAt','version'])",
          "accepted": {
            "//": "true only. A stored 'false' is not a consent record, it is noise.",
            ".validate": "newData.isBoolean() && newData.val() == true"
          },
          "acceptedAt": {
            "//": [
              "ServerValue.TIMESTAMP, which the server resolves to a number",
              "before these rules run. Bounded above by now so a client",
              "cannot date its consent into the future; not bounded below,",
              "because a legitimate record written moments ago and one",
              "written last year are both valid."
            ],
            ".validate": "newData.isNumber() && newData.val() <= now"
          },
          "version": {
            "//": [
              "A STRING, matching UserProfileRepository.CONSENT_VERSION,",
              "which is \"1.1\" today. A string rather than a number so that",
              "\"1.10\" can follow \"1.9\" instead of sorting before it.",
              "",
              "The pattern is deliberately loose about the digits: pinning it",
              "to the current version would reject every older record on any",
              "future write, and existing records keep the version they were",
              "actually given."
            ],
            ".validate": "newData.isString() && newData.val().matches(/^[0-9]{1,3}\\.[0-9]{1,3}$/)"
          },
          "$other": {
            ".validate": false
          }
        }
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
          "//": [
            "The .read HERE, on history itself, is load-bearing. A .read on",
            "$sequenceId alone grants each entry but never the list: RTDB",
            "permission on a child does not grant a read of its parent. The",
            "Alert tab queries history (orderByKey().limitToLast) and the mock",
            "sender reads history.json?shallow=true for its next sequence id;",
            "without this line both got 401 Permission denied. It exposes",
            "nothing new, since every entry is already readable one by one.",
            "Writes stay per entry, so each one is still validated."
          ],
          ".read": true,
          "$sequenceId": {
            "//": [
              "Append-only (Option A+): '!data.exists()' lets a new entry be",
              "written and refuses an overwrite or a delete, which would",
              "otherwise let anyone erase a panic. SCHEMA.md already calls",
              "history append-only, and the mock sender consumes each id once.",
              "The Console bypasses rules, so a human can still clear it.",
              "",
              "The key must be a whole number: the app orders by key and",
              "relies on integer-like keys (CLAUDE.md, 'Alert data'). The",
              "field checks are latest's, which every valid payload already",
              "passes there."
            ],
            ".read": true,
            ".write": "!data.exists()",
            ".validate": "$sequenceId.matches(/^[0-9]+$/) && newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
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
