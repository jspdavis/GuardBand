package com.example.guardband.data.model

/**
 * A band this account is linked to, and how.
 *
 * One account can wear a band and watch friends' bands at the same time, and
 * a band can have many watchers (docs/device-pairing.md §4). The relation
 * belongs to the link, never to the account: there is no stored "guardian" or
 * "wearer" on `users/{uid}`.
 *
 * No name or nickname: that would be new stored data, which needs the Privacy
 * Notice first.
 *
 * @param deviceId the band's id, the key under `devices/`.
 * @param relation whether this account wears the band or watches it.
 */
data class PairedBand(
    val deviceId: String,
    val relation: BandRelation
)

/** How an account is linked to a band. */
enum class BandRelation {
    /** The account's own band: it is on this person's wrist. */
    WEARER,

    /** A friend's band this account has been invited to watch. */
    WATCHER
}
