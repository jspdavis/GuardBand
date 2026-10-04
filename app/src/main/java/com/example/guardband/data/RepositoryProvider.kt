package com.example.guardband.data

import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.data.repository.FirebaseAuthRepository
import com.example.guardband.data.repository.FirebaseUserProfileRepository
import com.example.guardband.data.repository.InMemoryAlertRepository
import com.example.guardband.data.repository.InMemoryContactRepository
import com.example.guardband.data.repository.UserProfileRepository

/**
 * Manual wiring for repositories (no DI framework).
 *
 * Auth and the user profile are on Firebase. Contacts and alerts are still
 * in-memory: contacts move to the Realtime Database in Prompt 09, alerts when
 * the band's `devices/{deviceId}` reader lands.
 *
 * [InMemoryAuthRepository][com.example.guardband.data.repository.InMemoryAuthRepository]
 * stays in the codebase as the unit-test double and is deliberately not wired
 * here. Firebase instances come from [FirebaseProvider], so this object has no
 * Firebase imports of its own.
 */
object RepositoryProvider {

    val userProfileRepository: UserProfileRepository by lazy {
        FirebaseUserProfileRepository(FirebaseProvider.database)
    }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(FirebaseProvider.auth, userProfileRepository)
    }

    val contactRepository: ContactRepository by lazy { InMemoryContactRepository() }

    val alertRepository: AlertRepository by lazy {
        InMemoryAlertRepository(DeviceConstants.DEFAULT_DEVICE_ID)
    }
}
