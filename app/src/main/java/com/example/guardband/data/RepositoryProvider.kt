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

    /**
     * The band every screen watches: the one hard-coded id until pairing
     * exists (D4). The alert repository reads it, and the Device tab shows it
     * as the band's id; ViewModels never choose which band to read.
     */
    val watchedBandId: String = DeviceConstants.DEFAULT_DEVICE_ID

    val alertRepository: AlertRepository by lazy {
        FirebaseAlertRepository(FirebaseProvider.database, watchedBandId)
    }
}
