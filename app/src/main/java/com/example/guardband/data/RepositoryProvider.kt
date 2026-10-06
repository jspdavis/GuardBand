package com.example.guardband.data

import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.data.repository.FirebaseAlertRepository
import com.example.guardband.data.repository.FirebaseAuthRepository
import com.example.guardband.data.repository.FirebaseContactRepository
import com.example.guardband.data.repository.FirebaseUserProfileRepository
import com.example.guardband.data.repository.UserProfileRepository

/**
 * Manual wiring for repositories (no DI framework).
 *
 * Auth, the user profile, contacts and alerts are all on Firebase.
 *
 * The InMemory* implementations stay in the codebase as the unit-test
 * doubles and are deliberately not wired here. Firebase instances
 * come from [FirebaseProvider], so this object has no Firebase imports of its
 * own.
 */
object RepositoryProvider {

    val userProfileRepository: UserProfileRepository by lazy {
        FirebaseUserProfileRepository(FirebaseProvider.database)
    }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(FirebaseProvider.auth, userProfileRepository)
    }

    val contactRepository: ContactRepository by lazy {
        FirebaseContactRepository(FirebaseProvider.database, FirebaseProvider.auth)
    }

    val alertRepository: AlertRepository by lazy {
        FirebaseAlertRepository(FirebaseProvider.database, DeviceConstants.DEFAULT_DEVICE_ID)
    }
}
