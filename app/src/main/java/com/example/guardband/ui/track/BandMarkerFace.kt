package com.example.guardband.ui.track

import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import com.example.guardband.ui.avatar.AvatarPhoto

/**
 * What the band's marker on the Track map shows: a photo, and the initials it
 * falls back on. Pure, so it is unit-testable; [BandMarkerIcon] draws it.
 *
 * [photoUrl] is already vetted by [AvatarPhoto] (Google sign-in only,
 * `https` only, resized), the same link the Device tab's header loads, so
 * the two share the image loader's caches. The header shows these same
 * initials too. Null means initials only, and no request
 * is made. [initials] are drawn while the photo loads, when it fails or is
 * offline, and for an account with no photo; empty means a person glyph.
 *
 * A data class on purpose: [BandMarkerIcon] rebuilds the marker only when
 * the face it is given is not equal to the one it has, so a new position
 * with the same face costs nothing.
 */
data class BandMarkerFace(
    val photoUrl: String?,
    val initials: String
) {
    companion object {

        /** The face for [person], who signs in with [providers]. */
        fun of(person: User?, providers: Set<SignInProvider>): BandMarkerFace =
            BandMarkerFace(
                photoUrl = AvatarPhoto.urlFor(person, providers),
                initials = initialsOf(person?.name, person?.email)
            )

        /**
         * Up to two letters: the first letter of the first and of the last
         * word of [name], or one for a single word. With no usable name, the
         * first letter of [email]; with neither, empty.
         *
         * Works on code points, not chars, so a letter outside the Basic
         * Multilingual Plane is never cut in half, and takes the first
         * **letter or digit** of a word, so "J.R." gives J and an emoji or a
         * bracket is skipped. Letters in any script count. Upper-cased one
         * code point at a time, so "ß" stays one letter rather than "SS".
         */
        fun initialsOf(name: String?, email: String?): String {
            val words = name.orEmpty().trim().split(WHITESPACE)
                .mapNotNull(::firstLetter)
            return when {
                words.size >= 2 -> words.first() + words.last()
                words.size == 1 -> words.single()
                else -> email?.let(::firstLetter).orEmpty()
            }
        }

        private fun firstLetter(text: String): String? {
            val codePoint = text.codePoints()
                .filter(Character::isLetterOrDigit)
                .findFirst()
            if (!codePoint.isPresent) return null
            return String(Character.toChars(Character.toUpperCase(codePoint.asInt)))
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
