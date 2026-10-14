package com.example.guardband.data.model

/**
 * A stored consent record, `users/{uid}/consent`, as far as the app needs it.
 *
 * Only an **accepted** record is one: the Security Rules refuse to store
 * `accepted: false`, so the reader returns null for anything else rather than
 * a record that says "no". `acceptedAt` is not carried - nothing in the app
 * reads it; it is there for a human to see when somebody agreed.
 *
 * @param version the [CONSENT_VERSION][com.example.guardband.data.repository.UserProfileRepository.CONSENT_VERSION]
 *   the user agreed to, as stored. Empty when the record has none, which the
 *   rules have refused since 2026-10-11.
 */
data class ConsentRecord(val version: String)
