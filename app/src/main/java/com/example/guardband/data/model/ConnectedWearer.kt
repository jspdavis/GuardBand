package com.example.guardband.data.model

/**
 * A wearer this watcher account is linked to, with the display name read
 * from their profile and the device id of their band (if they have one).
 *
 * No coordinates or contact data: this is only what the switcher needs to
 * show the name and route the band reads to the right device id.
 *
 * @param wearerUid the wearer's Firebase Auth uid — the key under
 *   `wearer_watchers/{wearer_uid}` and `watcher_wearers/{watcher_uid}`.
 * @param displayName the value at `users/{wearer_uid}/name`, or an empty
 *   string when that node is absent or unreadable.
 * @param deviceId the wearer's band id from `users/{wearer_uid}/bands/`,
 *   or null when the wearer has no band yet.
 */
data class ConnectedWearer(
    val wearerUid: String,
    val displayName: String,
    val deviceId: String?
)
