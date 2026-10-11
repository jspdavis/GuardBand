package com.example.guardband.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * The app's single [FirebaseAuth] and [FirebaseDatabase] instance.
 *
 * Repositories take these through their constructors, so no other class calls
 * `getInstance()`. [FirebaseDatabase.getInstance] with no argument reads the
 * database URL from `google-services.json` (the `asia-southeast1` instance) —
 * the URL is never hardcoded here.
 *
 * Kept out of [RepositoryProvider] so that the wiring object itself stays free
 * of Firebase imports.
 */
internal object FirebaseProvider {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
}
