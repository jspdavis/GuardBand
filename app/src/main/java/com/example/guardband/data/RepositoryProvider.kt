package com.example.guardband.data

import android.content.Context
import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.BandRepository
import com.example.guardband.data.repository.BandSelectionStore
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.data.repository.FirebaseAlertRepository
import com.example.guardband.data.repository.FirebaseAuthRepository
import com.example.guardband.data.repository.FirebaseBandRepository
import com.example.guardband.data.repository.FirebaseContactRepository
import com.example.guardband.data.repository.FirebaseUserProfileRepository
import com.example.guardband.data.repository.FirebaseWearerConnectionRepository
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.data.repository.WatchedBandAlertRepository
import com.example.guardband.data.repository.WearerConnectionRepository

/**
 * Manual wiring for repositories (no DI framework).
 *
 * Auth, the user profile, contacts and alerts are all on Firebase.
 *
 * The InMemory* implementations stay in the codebase as the unit-test
 * doubles and are deliberately not wired here. Firebase instances
 * come from [FirebaseProvider], so this object has no Firebase imports of its
 * own.
 *
 * [bandSelectionStore] requires the application [Context] for
 * SharedPreferences; call [init] once in Application.onCreate before any
 * repository is used.
 */
object RepositoryProvider {

    private lateinit var _bandSelectionStore: BandSelectionStore

    /**
     * Must be called in Application.onCreate before any lazy val below is
     * first accessed. Safe to call more than once (idempotent after the
     * first call).
     */
    fun init(context: Context) {
        if (!::_bandSelectionStore.isInitialized) {
            _bandSelectionStore = BandSelectionStore(
                context.getSharedPreferences("band_selection", Context.MODE_PRIVATE)
            )
        }
    }

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
     * The bands the account wears or watches, read live from Firebase.
     * Replaces FixedBandRepository now that pairing exists.
     */
    val bandRepository: BandRepository by lazy {
        FirebaseBandRepository(FirebaseProvider.database, FirebaseProvider.auth)
    }

    /** The watcher's explicit Track-tab band choice, persisted across restarts. */
    val bandSelectionStore: BandSelectionStore
        get() = _bandSelectionStore

    /** Invite-code pairing between wearer and watcher accounts. */
    val wearerConnectionRepository: WearerConnectionRepository by lazy {
        FirebaseWearerConnectionRepository(FirebaseProvider.database, FirebaseProvider.auth)
    }

    /**
     * Follows [bandRepository] rather than capturing a band id, so one
     * instance serves every account this process signs in: it can never keep
     * reading the previous account's band. ViewModels never choose which band
     * to read.
     */
    val alertRepository: AlertRepository by lazy {
        WatchedBandAlertRepository(bandRepository, ::alertRepositoryFor, _bandSelectionStore)
    }

    /**
     * Reads one named band. For code that has already picked the band and
     * needs the id and the reads to agree (the alert watch keys its
     * high-water mark by it); screens use [alertRepository].
     */
    fun alertRepositoryFor(bandId: String): AlertRepository =
        FirebaseAlertRepository(FirebaseProvider.database, bandId, FirebaseProvider.auth)
}