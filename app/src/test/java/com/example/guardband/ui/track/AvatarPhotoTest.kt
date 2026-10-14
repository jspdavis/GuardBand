package com.example.guardband.ui.track

import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarPhotoTest {

    private val google = setOf(SignInProvider.GOOGLE)
    private val password = setOf(SignInProvider.PASSWORD)

    private fun userWith(photoUrl: String?) =
        User(id = "uid-1", name = "Test User", email = "test@guardband.com", photoUrl = photoUrl)

    // ── Which accounts get a photo ────────────────────────────────────────────

    @Test
    fun `a Google account with a photo gets it`() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/photo",
            AvatarPhoto.urlFor(userWith("https://lh3.googleusercontent.com/a/photo"), google)
        )
    }

    @Test
    fun `a Google account without a photo keeps the default icon`() {
        assertNull(AvatarPhoto.urlFor(userWith(null), google))
    }

    @Test
    fun `an email account keeps the default icon even with a photo link`() {
        assertNull(
            AvatarPhoto.urlFor(userWith("https://lh3.googleusercontent.com/a/photo"), password)
        )
    }

    @Test
    fun `an account with both providers counts as Google`() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/photo",
            AvatarPhoto.urlFor(
                userWith("https://lh3.googleusercontent.com/a/photo"),
                setOf(SignInProvider.PASSWORD, SignInProvider.GOOGLE)
            )
        )
    }

    @Test
    fun `no recognised provider keeps the default icon`() {
        assertNull(
            AvatarPhoto.urlFor(userWith("https://lh3.googleusercontent.com/a/photo"), emptySet())
        )
    }

    @Test
    fun `no session keeps the default icon`() {
        assertNull(AvatarPhoto.urlFor(null, google))
    }

    // ── Which links are accepted ──────────────────────────────────────────────

    @Test
    fun `a blank link keeps the default icon`() {
        assertNull(AvatarPhoto.urlFor(userWith(""), google))
        assertNull(AvatarPhoto.urlFor(userWith("   "), google))
    }

    @Test
    fun `an http link is refused`() {
        assertNull(AvatarPhoto.urlFor(userWith("http://lh3.googleusercontent.com/a/photo"), google))
    }

    @Test
    fun `a link with another scheme is refused`() {
        assertNull(AvatarPhoto.urlFor(userWith("content://photos/1"), google))
        assertNull(AvatarPhoto.urlFor(userWith("file:///sdcard/photo.jpg"), google))
    }

    @Test
    fun `a link with no host is refused`() {
        assertNull(AvatarPhoto.urlFor(userWith("https:///a/photo"), google))
    }

    @Test
    fun `a link that does not parse is refused`() {
        assertNull(AvatarPhoto.urlFor(userWith("https://lh3.googleusercontent.com/a b"), google))
    }

    // ── The size suffix ───────────────────────────────────────────────────────

    @Test
    fun `Google's size suffix is rewritten to the avatar's size`() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocK=s${AvatarPhoto.SIZE_PX}-c",
            AvatarPhoto.urlFor(userWith("https://lh3.googleusercontent.com/a/ACg8ocK=s96-c"), google)
        )
    }

    @Test
    fun `a link without the size suffix is left unchanged`() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8ocK",
            AvatarPhoto.urlFor(userWith("https://lh3.googleusercontent.com/a/ACg8ocK"), google)
        )
    }

    @Test
    fun `a size suffix that is not at the end is left alone`() {
        assertEquals(
            "https://example.com/p=s96-c/photo.jpg",
            AvatarPhoto.urlFor(userWith("https://example.com/p=s96-c/photo.jpg"), google)
        )
    }
}
