package com.example.guardband.data.repository

import com.google.firebase.database.FirebaseDatabase

/**
 * Singleton accessor for the GuardBand Firebase Realtime Database instance.
 *
 * All repository-layer operations on Realtime Database MUST reference
 * [DatabaseManager.database] rather than constructing their own [FirebaseDatabase]
 * instances, ensuring a single connection is shared across the app.
 *
 * URL: https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/
 */
object DatabaseManager {
    private const val DATABASE_URL =
        "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/"

    val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(DATABASE_URL)
    }
}
