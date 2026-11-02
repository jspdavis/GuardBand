package com.example.guardband.data

import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.BandRepository
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.data.repository.FirebaseAlertRepository
import com.example.guardband.data.repository.FirebaseAuthRepository
import com.example.guardband.data.repository.FirebaseContactRepository
import com.example.guardband.data.repository.FirebaseUserProfileRepository
import com.example.guardband.data.repository.FixedBandRepository
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.data.repository.WatchedBandAlertRepository

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
     * The bands the account wears or watches. Until pairing exists every
     * account wears the one hard-coded band (D4); a Firebase implementation
     * replaces this one (docs/device-pairing.md).
     */
    val bandRepository: BandRepository = FixedBandRepository(DeviceConstants.DEFAULT_DEVICE_ID)

    /**
     * Follows [bandRepository] rather than capturing a band id, so one
     * instance serves every account this process signs in: it can never keep
     * reading the previous account's band. ViewModels never choose which band
     * to read.
     */
    val alertRepository: AlertRepository by lazy {
        WatchedBandAlertRepository(bandRepository, ::alertRepositoryFor)
    }

    /**
     * Reads one named band. For code that has already picked the band and
     * needs the id and the reads to agree (the alert watch keys its
     * high-water mark by it); screens use [alertRepository].
     */
    fun alertRepositoryFor(bandId: String): AlertRepository =
        FirebaseAlertRepository(FirebaseProvider.database, bandId, FirebaseProvider.auth)
}
