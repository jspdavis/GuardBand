package com.example.guardband.ui.device

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAlertRepository.Companion.alert
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.ui.alert.AlertPlaces
import com.example.guardband.ui.alert.AlertUiState
import com.example.guardband.ui.avatar.AvatarPhoto
import com.example.guardband.ui.contacts.ContactsUiState
import com.example.guardband.ui.track.BandStatus
import com.example.guardband.ui.track.Elapsed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Device tab's state: the header and connected account from the session,
 * the band card from the alert screens' state (paired with data, paired with
 * none), and the contacts count from the Contacts tab's.
 *
 * Every ViewModel is built inside its test, never in a field, because it
 * launches the profile read in `init` (handoff §9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeUserProfileRepository()

    private fun viewModel(
        providers: Set<SignInProvider> = setOf(SignInProvider.PASSWORD),
        emailVerified: Boolean = true,
        user: User = FakeAuthRepository.DEFAULT_USER,
        signedIn: Boolean = true
    ) = DeviceViewModel(
        FakeAuthRepository(
            signedIn = signedIn,
            providers = providers,
            emailVerified = emailVerified,
            user = user
        ),
        profiles,
        bandId = BAND_ID
    )

    private val DeviceViewModel.band: BandCard
        get() = (uiState.value.pairing as DevicePairing.Paired).band

    /** 2026-10-27T12:00:00Z, the alert screens' "now". */
    private val now = BandStatus.parseIsoUtcMillis("2026-10-27T12:00:00Z")!!

    private fun loaded(
        newestReport: Alert?,
        latest: Alert? = null,
        placeNames: Map<String, String> = emptyMap(),
        nowMillis: Long = now
    ) = AlertUiState(
        isLoading = false,
        latest = latest,
        history = listOfNotNull(latest),
        newestReport = newestReport,
        placeNames = placeNames,
        nowMillis = nowMillis
    )

    private fun contact(id: String, emergency: Boolean = false) =
        EmergencyContact(id = id, name = "Contact $id", phone = "+639171234567", isEmergency = emergency)

    // ── Pairing ───────────────────────────────────────────────────────────────

    @Test
    fun `the band is always paired, with the watched band's id`() {
        val vm = viewModel()

        val pairing = vm.uiState.value.pairing
        assertTrue(pairing is DevicePairing.Paired)
        assertEquals(BAND_ID, (pairing as DevicePairing.Paired).bandId)
    }

    // ── Paired, no data ───────────────────────────────────────────────────────

    @Test
    fun `before the first read the band card is loading, not empty`() {
        val vm = viewModel()

        assertTrue(vm.band.isLoading)
        assertFalse(vm.band.showsNoData)
    }

    @Test
    fun `a band that never reported shows the no-data state`() {
        val vm = viewModel()

        vm.onAlertsChanged(loaded(newestReport = null))

        val band = vm.band
        assertFalse(band.isLoading)
        assertFalse(band.hasReported)
        assertTrue(band.showsNoData)
        assertFalse(band.isReporting)
        assertNull(band.lastSeen)
        assertNull(band.battery)
        assertNull(band.lastAlert)
        assertEquals(BandPosition.None, band.position)
    }

    @Test
    fun `a failed first read says so instead of the empty state`() {
        val vm = viewModel()

        vm.onAlertsChanged(AlertUiState(isLoading = false, errorMessage = "failed"))

        assertEquals(DeviceViewModel.MSG_BAND_UNAVAILABLE, vm.band.errorMessage)
        assertFalse(vm.band.showsNoData)
    }

    // ── Paired, with data ─────────────────────────────────────────────────────

    @Test
    fun `a recent report reads as reporting, with its battery and the last alert`() {
        val vm = viewModel()
        val panic = alert(5, "PANIC", timestamp = "2026-10-27T11:58:00Z")
        val key = AlertPlaces.keyOf(panic.location!!)

        vm.onAlertsChanged(
            loaded(newestReport = panic, latest = panic, placeNames = mapOf(key to "Osmeña Blvd, Cebu City"))
        )

        val band = vm.band
        assertTrue(band.hasReported)
        assertTrue(band.isReporting)
        assertEquals(Elapsed.Minutes(2), band.lastSeen)
        assertEquals(64, band.battery?.percent)
        assertFalse(band.isBatteryLow)
        assertEquals(5L, band.lastAlert?.alert?.sequenceId)
        assertEquals(Elapsed.Minutes(2), band.lastAlert?.elapsed)
        assertEquals("Osmeña Blvd, Cebu City", band.lastAlert?.placeName)
        assertEquals(BandPosition.Named("Osmeña Blvd, Cebu City"), band.position)
    }

    @Test
    fun `an older report reads as last seen, and ages on the alert screens' tick`() {
        val vm = viewModel()
        val checkIn = alert(3, "CHECKIN", timestamp = "2026-10-27T11:55:00Z")

        vm.onAlertsChanged(loaded(newestReport = checkIn, latest = checkIn))
        assertFalse(vm.band.isReporting)
        assertEquals(Elapsed.Minutes(5), vm.band.lastSeen)

        vm.onAlertsChanged(loaded(newestReport = checkIn, latest = checkIn, nowMillis = now + 30 * 60_000L))
        assertEquals(Elapsed.Minutes(35), vm.band.lastSeen)
    }

    @Test
    fun `a low battery reading is flagged`() {
        val vm = viewModel()
        val low = alert(4, "LOW_BATTERY", timestamp = "2026-10-27T11:59:00Z")
            .copy(battery = Alert.Battery(percent = 20, isCharging = false))

        vm.onAlertsChanged(loaded(newestReport = low, latest = low))

        assertEquals(20, vm.band.battery?.percent)
        assertTrue(vm.band.isBatteryLow)
    }

    @Test
    fun `a newer tracking update moves the position off the named alert`() {
        val vm = viewModel()
        val panic = alert(5, "PANIC", timestamp = "2026-10-27T11:58:00Z")
        val tracking = alert(6, "TRACKING_UPDATE", timestamp = "2026-10-27T11:59:00Z")
            .copy(location = Alert.Location(10.3300, 123.9000, 6.0))
        val names = mapOf(AlertPlaces.keyOf(panic.location!!) to "Osmeña Blvd, Cebu City")

        vm.onAlertsChanged(loaded(newestReport = tracking, latest = panic, placeNames = names))

        // The tracking point is never named; the last alert keeps its own name.
        assertEquals(BandPosition.Moving, vm.band.position)
        assertEquals(5L, vm.band.lastAlert?.alert?.sequenceId)
        assertEquals("Osmeña Blvd, Cebu City", vm.band.lastAlert?.placeName)
        assertEquals(Elapsed.Minutes(1), vm.band.lastSeen)
    }

    @Test
    fun `an alert position with no name yet reads as unnamed, never as coordinates`() {
        val vm = viewModel()
        val checkIn = alert(3, "CHECKIN", timestamp = "2026-10-27T11:59:00Z")

        vm.onAlertsChanged(loaded(newestReport = checkIn, latest = checkIn))

        assertEquals(BandPosition.Unnamed, vm.band.position)
        assertNull(vm.band.lastAlert?.placeName)
    }

    @Test
    fun `a report without a fix keeps the last known position`() {
        val vm = viewModel()
        val panic = alert(5, "PANIC", timestamp = "2026-10-27T11:58:00Z")
        val noFix = alert(6, "TRACKING_UPDATE", timestamp = "2026-10-27T11:59:00Z")
            .copy(location = null, battery = null)
        val names = mapOf(AlertPlaces.keyOf(panic.location!!) to "Osmeña Blvd, Cebu City")

        vm.onAlertsChanged(loaded(newestReport = panic, latest = panic, placeNames = names))
        vm.onAlertsChanged(loaded(newestReport = noFix, latest = panic, placeNames = names))

        assertEquals(BandPosition.Named("Osmeña Blvd, Cebu City"), vm.band.position)
        assertEquals(64, vm.band.battery?.percent)
    }

    @Test
    fun `a retry in flight keeps what the card already shows`() {
        val vm = viewModel()
        val checkIn = alert(3, "CHECKIN", timestamp = "2026-10-27T11:59:00Z")
        vm.onAlertsChanged(loaded(newestReport = checkIn, latest = checkIn))

        vm.onAlertsChanged(AlertUiState(isLoading = true, latest = checkIn, newestReport = checkIn, nowMillis = now))

        assertFalse(vm.band.isLoading)
        assertTrue(vm.band.hasReported)
        assertEquals(64, vm.band.battery?.percent)
    }

    @Test
    fun `a failure after the band has reported keeps the card and shows no error`() {
        val vm = viewModel()
        val tracking = alert(6, "TRACKING_UPDATE", timestamp = "2026-10-27T11:59:00Z")
        vm.onAlertsChanged(loaded(newestReport = tracking))

        // The alert screens raise their error with only tracking updates on hand.
        vm.onAlertsChanged(
            AlertUiState(isLoading = false, newestReport = tracking, errorMessage = "failed", nowMillis = now)
        )

        assertNull(vm.band.errorMessage)
        assertTrue(vm.band.hasReported)
    }

    // ── Contacts ──────────────────────────────────────────────────────────────

    @Test
    fun `the contacts count waits for the first read`() {
        val vm = viewModel()

        vm.onContactsChanged(ContactsUiState(isLoading = true))

        assertEquals(ContactsSummary.Loading, vm.uiState.value.contacts)
    }

    @Test
    fun `contacts are counted, with how many are starred`() {
        val vm = viewModel()

        vm.onContactsChanged(
            ContactsUiState(contacts = listOf(contact("a", emergency = true), contact("b"), contact("c")))
        )

        assertEquals(ContactsSummary.Counted(total = 3, starred = 1), vm.uiState.value.contacts)
    }

    @Test
    fun `the count is of every contact, not the search's slice`() {
        val vm = viewModel()

        vm.onContactsChanged(
            ContactsUiState(
                contacts = listOf(contact("a", emergency = true), contact("b", emergency = true)),
                query = "nobody matches this"
            )
        )

        assertEquals(ContactsSummary.Counted(total = 2, starred = 2), vm.uiState.value.contacts)
    }

    @Test
    fun `a failed first contacts read has no count`() {
        val vm = viewModel()

        vm.onContactsChanged(ContactsUiState(loadFailed = true))

        assertEquals(ContactsSummary.Unavailable, vm.uiState.value.contacts)
    }

    @Test
    fun `a failed read after a good one keeps the last count`() {
        val vm = viewModel()

        vm.onContactsChanged(ContactsUiState(contacts = listOf(contact("a")), loadFailed = true))

        assertEquals(ContactsSummary.Counted(total = 1, starred = 0), vm.uiState.value.contacts)
    }

    // ── Sign-in method and verification ───────────────────────────────────────

    @Test
    fun `a verified email account shows email and verified`() {
        val account = viewModel(providers = setOf(SignInProvider.PASSWORD), emailVerified = true)
            .uiState.value.account

        assertEquals(SignInMethod.EMAIL, account.signInMethod)
        assertTrue(account.emailVerified)
    }

    @Test
    fun `an unverified email account shows not verified`() {
        val account = viewModel(providers = setOf(SignInProvider.PASSWORD), emailVerified = false)
            .uiState.value.account

        assertEquals(SignInMethod.EMAIL, account.signInMethod)
        assertFalse(account.emailVerified)
    }

    @Test
    fun `a Google account is always verified`() {
        val account = viewModel(providers = setOf(SignInProvider.GOOGLE), emailVerified = false)
            .uiState.value.account

        assertEquals(SignInMethod.GOOGLE, account.signInMethod)
        assertTrue(account.emailVerified)
    }

    @Test
    fun `an account with both providers says so`() {
        val account = viewModel(providers = setOf(SignInProvider.PASSWORD, SignInProvider.GOOGLE))
            .uiState.value.account

        assertEquals(SignInMethod.EMAIL_AND_GOOGLE, account.signInMethod)
    }

    @Test
    fun `an account with no known provider is unknown`() {
        val account = viewModel(providers = emptySet()).uiState.value.account

        assertEquals(SignInMethod.UNKNOWN, account.signInMethod)
    }

    // ── Header ────────────────────────────────────────────────────────────────

    @Test
    fun `the header is filled from the session, with initials for an email account`() {
        val header = viewModel().uiState.value.header

        assertEquals(FakeAuthRepository.DEFAULT_USER.name, header.name)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, header.email)
        assertEquals("TU", header.face.initials)
        assertNull(header.face.photoUrl)
    }

    @Test
    fun `a Google session's photo becomes the avatar`() {
        val header = viewModel(
            providers = setOf(SignInProvider.GOOGLE),
            user = FakeAuthRepository.DEFAULT_USER.copy(photoUrl = "https://lh3.googleusercontent.com/a/x=s96-c")
        ).uiState.value.header

        assertEquals("https://lh3.googleusercontent.com/a/x=s${AvatarPhoto.SIZE_PX}-c", header.face.photoUrl)
    }

    @Test
    fun `the stored record replaces the session's name and email`() = runTest {
        profiles.fetchProfileResult =
            Result.success(User(id = "uid-1", name = "Alex Rivera", email = "alex@guardband.com"))
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals("Alex Rivera", vm.uiState.value.header.name)
        assertEquals("alex@guardband.com", vm.uiState.value.header.email)
    }

    @Test
    fun `a failed or blank profile read keeps the session's values`() = runTest {
        profiles.fetchProfileResult = Result.failure(AuthError.Network)
        val failed = viewModel()
        advanceUntilIdle()
        assertEquals(FakeAuthRepository.DEFAULT_USER.name, failed.uiState.value.header.name)

        profiles.fetchProfileResult = Result.success(User(id = "uid-1", name = "  ", email = ""))
        val blank = viewModel()
        advanceUntilIdle()
        assertEquals(FakeAuthRepository.DEFAULT_USER.name, blank.uiState.value.header.name)
        assertEquals(FakeAuthRepository.DEFAULT_USER.email, blank.uiState.value.header.email)
    }

    @Test
    fun `no session means no profile read, and the name falls back`() = runTest {
        val vm = viewModel(signedIn = false)
        advanceUntilIdle()

        assertEquals(0, profiles.fetchProfileCalls)
        assertEquals(DeviceViewModel.DEFAULT_USER_NAME, vm.uiState.value.header.name)
    }

    private companion object {
        const val BAND_ID = "guardband-001"
    }
}
