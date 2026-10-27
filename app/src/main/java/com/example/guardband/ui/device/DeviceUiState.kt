package com.example.guardband.ui.device

import com.example.guardband.data.model.Alert
import com.example.guardband.ui.track.BandMarkerFace
import com.example.guardband.ui.track.Elapsed

/**
 * Render state for [DeviceFragment]: the account header, the band (through
 * [pairing]), the account connected to it, and the emergency-contacts count.
 */
data class DeviceUiState(
    /** Avatar, display name and email at the top. */
    val header: DeviceHeader,

    /**
     * Whether a band is paired, and if so what it says. **Always
     * [DevicePairing.Paired] today**, with the one hard-coded band; the
     * sealed shape is there so pairing can add [DevicePairing.Unpaired]
     * without reshaping the screen.
     */
    val pairing: DevicePairing,

    /** How the account signs in, and whether its email is verified. */
    val account: ConnectedAccount,

    /** "Emergency contacts: N, M starred". */
    val contacts: ContactsSummary = ContactsSummary.Loading
)

/**
 * The header: [face] is the same photo-or-initials the band's marker uses
 * ([BandMarkerFace.of]), so the two can never disagree about whose picture
 * it is. [name] already falls back to "User".
 */
data class DeviceHeader(
    val name: String,
    val email: String,
    val face: BandMarkerFace
)

/** Pairing's two cases. No pairing logic exists yet; see [DeviceUiState.pairing]. */
sealed interface DevicePairing {

    /** No band linked to this account. Unreachable until pairing exists. */
    object Unpaired : DevicePairing

    /**
     * A band is linked: [bandId] is the id it reports under, [band] what it
     * has said.
     */
    data class Paired(val bandId: String, val band: BandCard) : DevicePairing
}

/**
 * The band card. Distinguished by which fields are set, like Track's state,
 * so the card keeps what it showed while a later read fails.
 */
data class BandCard(
    /** True until the history window's first read completes. */
    val isLoading: Boolean = true,

    /** The read failed with nothing to show; the card says so instead. */
    val errorMessage: String? = null,

    /** The band has reported at all. False is the "No data from the band yet" state. */
    val hasReported: Boolean = false,

    /** Reported within the last 180 s ([com.example.guardband.ui.track.BandStatus.ONLINE_THRESHOLD_MS]). */
    val isReporting: Boolean = false,

    /** How long ago it last reported, for "Last seen 5 min ago"; null when unreadable. */
    val lastSeen: Elapsed? = null,

    /** Last known battery, retained across reports without one. */
    val battery: Alert.Battery? = null,

    /** At or below FR-11's 20%: the battery shows orange. */
    val isBatteryLow: Boolean = false,

    /** The newest panic, check-in, low-battery or unknown alert (D2's latest), if any. */
    val lastAlert: LastAlert? = null,

    /** The band's last known position, as a place name only. */
    val position: BandPosition = BandPosition.None
) {
    /** The empty state: read fine, and the band has never said anything. */
    val showsNoData: Boolean get() = !isLoading && errorMessage == null && !hasReported
}

/**
 * The band's latest non-tracking alert, its age (null when its timestamp is
 * unreadable) and its place name once the alert screens have found one.
 */
data class LastAlert(
    val alert: Alert,
    val elapsed: Elapsed?,
    val placeName: String?
)

/**
 * What the "Last known position" line says. **Never coordinates.**
 *
 * Only an alert's position is ever named, by the name the alert screens
 * already asked the geocoder for. A tracking update's position is never sent
 * to the geocoder (the Privacy Notice covers alert positions only), so while
 * one is newer the line points to the map instead.
 */
sealed interface BandPosition {

    /** The band has never sent a fix. */
    object None : BandPosition

    /** An alert's position, named. */
    data class Named(val name: String) : BandPosition

    /** An alert's position with no name: not looked up yet, or none exists. */
    object Unnamed : BandPosition

    /** The newest fix is a tracking update: the band is moving. */
    object Moving : BandPosition
}

/** The connected-account card. */
data class ConnectedAccount(
    val signInMethod: SignInMethod,
    /** Google accounts always count as verified ([com.example.guardband.ui.account.EmailVerification]). */
    val emailVerified: Boolean
)

/** How the account signs in, from its providers. */
enum class SignInMethod { EMAIL, GOOGLE, EMAIL_AND_GOOGLE, UNKNOWN }

/** The contacts row, from the Contacts tab's own list. */
sealed interface ContactsSummary {

    /** The first read has not landed. */
    object Loading : ContactsSummary

    /** The first read failed, so there is no count to give. */
    object Unavailable : ContactsSummary

    /** [total] contacts, [starred] of them marked Emergency. */
    data class Counted(val total: Int, val starred: Int) : ContactsSummary
}
