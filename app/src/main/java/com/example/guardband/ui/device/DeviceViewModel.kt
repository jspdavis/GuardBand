package com.example.guardband.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.ui.account.EmailVerification
import com.example.guardband.ui.alert.AlertPlaces
import com.example.guardband.ui.alert.AlertUiState
import com.example.guardband.ui.contacts.ContactsUiState
import com.example.guardband.ui.track.BandMarkerFace
import com.example.guardband.ui.track.BandReport
import com.example.guardband.ui.track.BandStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Device tab: the band, the account connected to it, and a placeholder for
 * pairing. The Profile tab until 2026-10-27; its header (avatar, name,
 * email) is the top of this screen.
 *
 * **No listener of its own.** Everything live is handed over by the Fragment
 * from the two host-scoped ViewModels that already read it, as Track's pins
 * are:
 *  - [onAlertsChanged]: the history window behind Track's sheet and Alert
 *    history ([AlertUiState]). The band's newest report
 *    ([AlertUiState.newestReport], tracking updates included) feeds
 *    [BandReport], the same rules Track applies to `latest`; the last alert
 *    is [AlertUiState.latest] (D2) and its place name the one the alert
 *    screens already found. "Now" is [AlertUiState.nowMillis], which that
 *    ViewModel moves on every 30 s, so "Last seen" ages with no ticker here.
 *  - [onContactsChanged]: the Contacts tab's list, for the count.
 *
 * The header and the connected-account card come from the session, read
 * synchronously; the `users/{uid}` record then replaces the name and email,
 * as the Profile tab did. That one-shot read is the only call this makes.
 *
 * **Pairing is a shape, not logic.** [DeviceUiState.pairing] is always
 * [DevicePairing.Paired] with [bandId], the one band every install watches.
 *
 * No events: the contacts row and the Settings row navigate through
 * [HomeViewModel][com.example.guardband.ui.home.HomeViewModel]. Nothing here
 * logs: the state holds the band's position (as a place name), an email and
 * a photo link.
 *
 * @param bandId the band's id, for display only: which band is read is the
 *   repository's wiring ([RepositoryProvider.watchedBandId]), never this.
 */
class DeviceViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val bandId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(sessionState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    /** What the band's reports add up to, by Track's rules. */
    private var band: BandReport = BandReport.NONE

    init {
        loadProfile()
    }

    /**
     * The alert screens' state changed: a read, a place name, or their 30 s
     * tick. A read still loading (the first, or a retry) changes nothing, so
     * a retry never blanks the card; a loaded window with no entries is the
     * band never having reported.
     */
    fun onAlertsChanged(alerts: AlertUiState) {
        if (!alerts.isLoading) band = band.after(alerts.newestReport)
        val card = bandCard(alerts)
        _uiState.update { it.copy(pairing = DevicePairing.Paired(bandId, card)) }
    }

    /**
     * The Contacts tab's list changed. The count is of every contact, not
     * the search's or the chips' slice. A failed read keeps the last count;
     * only a first read that failed has nothing to say.
     */
    fun onContactsChanged(contacts: ContactsUiState) {
        val summary = when {
            contacts.isLoading -> ContactsSummary.Loading
            contacts.loadFailed && contacts.contacts.isEmpty() -> ContactsSummary.Unavailable
            else -> ContactsSummary.Counted(
                total = contacts.contacts.size,
                starred = contacts.contacts.count { it.isEmergency }
            )
        }
        _uiState.update { it.copy(contacts = summary) }
    }

    private fun bandCard(alerts: AlertUiState): BandCard {
        val now = alerts.nowMillis
        val latest = alerts.latest
        return BandCard(
            isLoading = alerts.isLoading && !band.hasReported,
            errorMessage = if (alerts.errorMessage != null && !band.hasReported) MSG_BAND_UNAVAILABLE else null,
            hasReported = band.hasReported,
            isReporting = band.isReporting(now),
            lastSeen = band.elapsed(now),
            battery = band.battery,
            isBatteryLow = band.isBatteryLow,
            lastAlert = latest?.let { alert ->
                LastAlert(
                    alert = alert,
                    elapsed = BandStatus.parseIsoUtcMillis(alert.timestamp)
                        ?.let { BandStatus.elapsedSince(it, now) },
                    placeName = alerts.placeNameFor(alert)
                )
            },
            position = positionOf(alerts)
        )
    }

    /**
     * Only an alert's position is named, and only by the name the alert
     * screens already found for it: a tracking update's position is never
     * sent to the geocoder, so it reads as [BandPosition.Moving].
     */
    private fun positionOf(alerts: AlertUiState): BandPosition {
        val location = band.location ?: return BandPosition.None
        if (band.locationFromTracking) return BandPosition.Moving
        return alerts.placeNames[AlertPlaces.keyOf(location)]
            ?.let { BandPosition.Named(it) }
            ?: BandPosition.Unnamed
    }

    private fun loadProfile() {
        val uid = authRepository.currentUser()?.id ?: return
        viewModelScope.launch {
            // A missing record or a failed read keeps the session's values:
            // the user's own name and address are still worth showing.
            userProfileRepository.fetchProfile(uid).onSuccess { profile ->
                _uiState.update { state ->
                    state.copy(
                        header = state.header.copy(
                            name = profile?.name?.takeUnless { it.isBlank() } ?: state.header.name,
                            email = profile?.email?.takeUnless { it.isBlank() } ?: state.header.email
                        )
                    )
                }
            }
        }
    }

    /** What the session alone can fill, before anything is read. */
    private fun sessionState(): DeviceUiState {
        val user = authRepository.currentUser()
        val providers = authRepository.currentUserProviders()
        return DeviceUiState(
            header = DeviceHeader(
                name = user?.name?.takeUnless { it.isBlank() } ?: DEFAULT_USER_NAME,
                email = user?.email.orEmpty(),
                face = BandMarkerFace.of(user, providers)
            ),
            pairing = DevicePairing.Paired(bandId, BandCard()),
            account = ConnectedAccount(
                signInMethod = signInMethodOf(providers),
                emailVerified = EmailVerification.of(providers, authRepository.isEmailVerified()).verified
            )
        )
    }

    private fun signInMethodOf(providers: Set<SignInProvider>): SignInMethod {
        val password = SignInProvider.PASSWORD in providers
        val google = SignInProvider.GOOGLE in providers
        return when {
            password && google -> SignInMethod.EMAIL_AND_GOOGLE
            password -> SignInMethod.EMAIL
            google -> SignInMethod.GOOGLE
            else -> SignInMethod.UNKNOWN
        }
    }

    companion object {
        const val DEFAULT_USER_NAME = "User"

        const val MSG_BAND_UNAVAILABLE = "Couldn't load your band's status."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.userProfileRepository,
                    RepositoryProvider.watchedBandId
                )
            }
        }
    }
}
