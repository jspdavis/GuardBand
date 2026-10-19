package com.example.guardband.ui.track

import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The band marker's face: photo or initials, and how the initials are made.
 * The photo rules themselves are [AvatarPhoto][com.example.guardband.ui.avatar.AvatarPhoto]'s
 * and tested there; this checks the marker uses them.
 */
class BandMarkerFaceTest {

    private val photo = "https://lh3.googleusercontent.com/a/abc=s96-c"
    private val google = setOf(SignInProvider.GOOGLE)

    // ── Photo or initials ─────────────────────────────────────────────────────

    @Test
    fun `a Google account with a photo gets the photo, resized for the marker`() {
        val face = BandMarkerFace.of(User(name = "Ana Reyes", photoUrl = photo), google)

        assertEquals("https://lh3.googleusercontent.com/a/abc=s288-c", face.photoUrl)
        assertEquals("AR", face.initials)
    }

    @Test
    fun `a Google account with no photo gets initials only`() {
        val face = BandMarkerFace.of(User(name = "Ana Reyes", photoUrl = null), google)

        assertNull(face.photoUrl)
        assertEquals("AR", face.initials)
    }

    @Test
    fun `a password account never gets a photo, even with a link`() {
        val face = BandMarkerFace.of(
            User(name = "Ana Reyes", photoUrl = photo),
            setOf(SignInProvider.PASSWORD)
        )

        assertNull(face.photoUrl)
    }

    @Test
    fun `no person gives a blank face`() {
        assertEquals(BandMarkerFace(null, ""), BandMarkerFace.of(null, emptySet()))
    }

    @Test
    fun `the same person gives an equal face, which is what stops a redraw`() {
        val person = User(name = "Ana Reyes", photoUrl = photo)

        assertEquals(BandMarkerFace.of(person, google), BandMarkerFace.of(person, google))
    }

    // ── Initials ──────────────────────────────────────────────────────────────

    @Test
    fun `first and last word`() {
        assertEquals("JC", BandMarkerFace.initialsOf("Juan dela Cruz", null))
    }

    @Test
    fun `a single name gives one letter`() {
        assertEquals("M", BandMarkerFace.initialsOf("Madonna", null))
    }

    @Test
    fun `lower case is upper-cased, and extra spaces do not count`() {
        assertEquals("AR", BandMarkerFace.initialsOf("  ana   reyes  ", null))
    }

    @Test
    fun `an empty or blank name falls back to the email's first letter`() {
        assertEquals("A", BandMarkerFace.initialsOf("", "ana@example.com"))
        assertEquals("A", BandMarkerFace.initialsOf("   ", "ana@example.com"))
        assertEquals("A", BandMarkerFace.initialsOf(null, "ana@example.com"))
    }

    @Test
    fun `no name and no email is empty, which draws a person glyph`() {
        assertEquals("", BandMarkerFace.initialsOf(null, null))
        assertEquals("", BandMarkerFace.initialsOf("", ""))
    }

    @Test
    fun `punctuation and emoji are skipped, not taken as a letter`() {
        assertEquals("JS", BandMarkerFace.initialsOf("J.R. Smith", null))
        assertEquals("A", BandMarkerFace.initialsOf("😀 Ana", null))
        assertEquals("A", BandMarkerFace.initialsOf("(Ana)", null))
    }

    @Test
    fun `letters in other scripts count`() {
        assertEquals("王", BandMarkerFace.initialsOf("王小明", null))
        assertEquals("АП", BandMarkerFace.initialsOf("Александр Пушкин", null))
        assertEquals("JÁ", BandMarkerFace.initialsOf("José Álvarez", null))
        assertEquals("ΣΠ", BandMarkerFace.initialsOf("σοφία παππά", null))
    }

    @Test
    fun `a letter outside the BMP is kept whole`() {
        // U+1D4D0 MATHEMATICAL BOLD SCRIPT CAPITAL A: two chars, one letter.
        val scriptA = String(Character.toChars(0x1D4D0))
        assertEquals(scriptA + "B", BandMarkerFace.initialsOf("${scriptA}lice Bob", null))
    }

    @Test
    fun `a letter with no one-letter capital stays one letter`() {
        // "ß".uppercase() would be "SS"; an initial must stay one letter.
        assertEquals("ß", BandMarkerFace.initialsOf("ßtraße", null))
    }
}
