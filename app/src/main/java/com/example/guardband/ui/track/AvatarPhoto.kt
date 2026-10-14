package com.example.guardband.ui.track

import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import java.net.URI
import java.net.URISyntaxException

/**
 * Which photo, if any, the Track avatar loads. Pure, so it is unit-testable.
 *
 * Null means "keep the default icon", and the screen then makes no request at
 * all. Three rules, each a reason to return null:
 *
 *  - **Only a Google account counts.** An email/password account keeps the
 *    default icon even if its session somehow carries a photo link. The
 *    provider comes from [SignInProvider], which the repository reads off
 *    `providerData` (`"google.com"`), never from the URL's host.
 *  - **No link, no photo.** A null or blank `photoUrl`.
 *  - **`https` only.** Anything else, or anything that does not parse as a
 *    URL with a host, is refused rather than handed to the image loader.
 *
 * Takes `java.net.URI` rather than `android.net.Uri`, as
 * [ResetLinkParser][com.example.guardband.ui.reset.ResetLinkParser] does, so
 * the tests run on the plain JVM.
 */
internal object AvatarPhoto {

    /**
     * The pixel size requested from Google: 3x the avatar's 36dp, so it is
     * sharp on an xxhdpi screen without downloading a full-size photo.
     */
    const val SIZE_PX = 108

    /**
     * Google's size-and-crop suffix, `=s96-c`, at the very end of the link.
     * Only this exact shape is rewritten; any other link is loaded unchanged.
     */
    private val SIZE_SUFFIX = Regex("=s\\d+-c$")

    fun urlFor(user: User?, providers: Set<SignInProvider>): String? {
        if (SignInProvider.GOOGLE !in providers) return null

        val url = user?.photoUrl?.takeUnless { it.isBlank() } ?: return null
        if (!isHttps(url)) return null

        return SIZE_SUFFIX.replace(url, "=s$SIZE_PX-c")
    }

    private fun isHttps(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    } catch (e: URISyntaxException) {
        false
    }
}
