{
  "//": [
    "GuardBand Realtime Database Security Rules - DRAFT. NOT DEPLOYED.",
    "",
    "The live rules have never been read from the tooling side. Everything",
    "here is written against the app's code, not against what is deployed, so",
    "this file must be MERGED with the live rules by hand rather than pasted",
    "over them. See 'Merging into the Console' below.",
    "",
    "Project guardband-aae65, asia-southeast1, Spark plan.",
    "Related: CLAUDE.md, docs/privacy-and-consent.md, SCHEMA.md (the devices",
    "payload contract, which is authoritative for that shape)."
  ],
  "// Merging into the Console": [
    "1. Console -> Realtime Database -> Rules. COPY THE LIVE RULES OUT FIRST",
    "   and keep them somewhere; nobody has a record of them.",
    "2. Diff them against this file. Anything live that is not here is",
    "   something this draft does not know about - keep it, do not drop it.",
    "3. Take the users/ block from here wholesale. It is the part this draft",
    "   is authoritative for.",
    "4. Leave the devices/ block exactly as the live rules have it. The block",
    "   in this file is unchanged from the previous draft and is reproduced",
    "   only so the file stays a complete document.",
    "5. Delete the legacy nodes' rules (they are already gone from this file):",
    "   password_resets, phone_index, email_index. Grepped against app/src and",
    "   referenced by nothing.",
    "6. EXPORT THE DATA AT THOSE THREE NODES BEFORE DELETING THE DATA, then",
    "   delete the nodes themselves in the Data tab. Rules and data are",
    "   separate: removing a rule does not remove what is stored under it, and",
    "   with the rule gone the data becomes unreachable rather than deleted.",
    "7. Publish, then run every Playground test below.",
    "",
    "Rules cannot be unit-tested without the emulator suite, which this",
    "project does not set up, so the Playground list IS the test suite."
  ],
  "// Rules Playground tests": [
    "Console -> Realtime Database -> Rules -> Playground. Run all eight.",
    "Use a real uid for AUTH_UID and a different one for OTHER_UID.",
    "",
    "1. READ  /users/AUTH_UID            authenticated as AUTH_UID   -> ALLOW",
    "2. WRITE /users/AUTH_UID  value null, authenticated as AUTH_UID -> ALLOW",
    "     (this is Delete account. If it denies, a .validate is being",
    "      evaluated on a delete and something above is wrong.)",
    "3. READ  /users/AUTH_UID            authenticated as OTHER_UID  -> DENY",
    "4. READ  /users/AUTH_UID            unauthenticated             -> DENY",
    "5. WRITE /users/AUTH_UID/emergency_contacts/c1",
    "     {\"name\":\"A\",\"phone\":\"0917123456\",\"relationship\":\"\"}",
    "     authenticated as AUTH_UID                                 -> DENY",
    "     (not E.164 and not a PH mobile: ten digits, no +63)",
    "     Then the same with \"+639171234567\"                        -> ALLOW",
    "6. WRITE /users/AUTH_UID/consent",
    "     {\"accepted\":false,\"acceptedAt\":0,\"version\":\"1.1\"}",
    "     authenticated as AUTH_UID                                 -> DENY",
    "     (accepted must be true)",
    "     Then {\"accepted\":true,\"version\":\"1.1\"} with no acceptedAt -> DENY",
    "     Then all three, acceptedAt = a future ms timestamp        -> DENY",
    "     Then all three, acceptedAt = now                          -> ALLOW",
    "7. READ  /devices/guardband-001/latest     unauthenticated      -> ALLOW",
    "8. WRITE /devices/guardband-001/history/1  unauthenticated,",
    "     a full valid payload per SCHEMA.md                        -> ALLOW",
    "     (7 and 8 must still pass. The mock sender writes",
    "      unauthenticated and the Track and Alert tabs read; if either",
    "      flips, the demo breaks.)"
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
    "DELIBERATELY UNCHANGED, pending device pairing.",
    "",
    "It is world-readable AND world-writable: '.read': true and '.write': true",
    "on the wearer's coordinates. That is not an oversight, it is what the",
    "current design needs - the debug mock sender PUTs here unauthenticated",
    "over REST, as the band's firmware will, and the Track and Alert tabs read",
    "it. There is one shared device id (guardband-001) because nothing pairs a",
    "band to an account yet.",
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
              "children, so InputValidator.MIN_CONTACTS (3) and MAX_CONTACTS",
              "(10) stay the repository's job. These rules only police the",
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
