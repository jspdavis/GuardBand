package com.example.guardband.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * An emergency contact associated with a user.
 *
 * Mirrors one record under `users/{uid}/emergency_contacts/{id}`. The field
 * names match the database's, so the repository stores and reads them without
 * a translation step.
 *
 * Every property defaults, because the Realtime Database SDK needs a no-arg
 * constructor to deserialize a snapshot.
 *
 * [Parcelable] only so the sign-up wizard can carry staged contacts between
 * its steps as an Intent extra; nothing is written until the final submit.
 * The extras address components inside this app, and neither the phone nor the
 * name is ever logged.
 *
 * @param id           The record's key. Assigned by the repository on add, and
 *                     empty on a contact that has not been saved yet.
 * @param name         Contact's display name.
 * @param phone        Phone number in E.164, as
 *                     [normalizePhoneToE164][com.example.guardband.utils.InputValidator.normalizePhoneToE164]
 *                     produced it. Never logged: it is a third party's personal
 *                     data.
 * @param relationship Optional relationship label (e.g. "Friend", "Family").
 */
@Parcelize
data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val relationship: String = ""
) : Parcelable
