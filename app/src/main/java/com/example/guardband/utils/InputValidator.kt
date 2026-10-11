package com.example.guardband.utils

import android.util.Patterns

/**
 * Input predicates shared by the screen ViewModels.
 *
 * Predicates only: error messages differ per screen and stay in each
 * ViewModel. Semantics mirror the MVP presenters (AuthPresenter,
 * ForgotPresenter) exactly — notably, nothing here trims its input except
 * [isBlank], which treats whitespace-only text as blank.
 *
 * The contact rules below are the one exception to "predicates only":
 * [normalizePhoneToE164] returns a value, because the stored form of a phone
 * number is a data-layer concern and the repository must not re-derive it.
 * It is pure Kotlin, so unlike [isValidEmail] it needs no Robolectric.
 */
object InputValidator {

    /** True for empty or whitespace-only text (same as `String.isBlank()`). */
    fun isBlank(text: String): Boolean = text.isBlank()

    /** Matches [Patterns.EMAIL_ADDRESS] against [email] as given (untrimmed). */
    fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /**
     * True when [password] has at least [min] characters (no trimming).
     *
     * The default is the app's rule, and it is stricter than Firebase's own
     * minimum of 6, so a password that passes here is never rejected as
     * [AuthError.WeakPassword][com.example.guardband.data.repository.AuthError.WeakPassword].
     */
    fun isPasswordLongEnough(password: String, min: Int = 8): Boolean =
        password.length >= min

    /** Exact, case-sensitive equality. */
    fun passwordsMatch(a: String, b: String): Boolean = a == b

    // ── Emergency contacts ────────────────────────────────────────────────────

    /**
     * The stored form of [raw], or null when it is not a number we accept.
     *
     * Separators humans type — spaces, hyphens, parentheses, dots — are
     * stripped first, so "0917 123 4567" and "(0917) 123-4567" both work.
     *
     * A Philippine mobile number is recognised in its three local spellings
     * and always stored as `+639XXXXXXXXX`:
     *
     * ```
     * 09171234567    →  +639171234567
     * 639171234567   →  +639171234567
     * +639171234567  →  +639171234567
     * ```
     *
     * A number carrying the PH country code is held to the mobile shape and
     * nothing else: if it starts `+63` but is not `+639` plus nine
     * digits, it is refused outright rather than falling through to the
     * generic rule below. Without that guard a mistyped mobile such as
     * `+6391712345` was simply stored as typed — it is valid E.164 on length
     * alone, so neither this validator nor the Security Rules could catch it,
     * and the band would text a number that cannot ring. The cost is that a PH
     * landline written `+6321234567` is now refused too; emergency contacts
     * are expected to be reachable by SMS, so a landline was never usable.
     *
     * Anything else is accepted only if it is already valid E.164 — a leading
     * `+` and 8 to 15 digits — and is then stored exactly as typed. That is
     * what lets a guardian abroad be reached.
     */
    fun normalizePhoneToE164(raw: String): String? {
        val compact = raw.filterNot { it.isWhitespace() || it in PHONE_SEPARATORS }
        phMobileSubscriberPart(compact)?.let { return PH_COUNTRY_CODE + it }
        if (compact.startsWith(PH_COUNTRY_CODE)) return null
        return compact.takeIf { E164.matches(it) }
    }

    /** Convenience predicate over [normalizePhoneToE164]. */
    fun isValidPhone(raw: String): Boolean = normalizePhoneToE164(raw) != null

    /** True when [name] is not blank and fits [MAX_CONTACT_NAME_LENGTH] once trimmed. */
    fun isValidContactName(name: String): Boolean =
        name.trim().let { it.isNotEmpty() && it.length <= MAX_CONTACT_NAME_LENGTH }

    /**
     * True when [relationship] fits [MAX_RELATIONSHIP_LENGTH] once trimmed.
     *
     * Blank passes: the field is optional (D1), and a blank one is stored as an
     * empty string rather than rejected.
     */
    fun isValidContactRelationship(relationship: String): Boolean =
        relationship.trim().length <= MAX_RELATIONSHIP_LENGTH

    /** True when another contact fits under [MAX_CONTACTS]. */
    fun canAddContact(currentCount: Int): Boolean = currentCount < MAX_CONTACTS

    /**
     * True when deleting one of [currentCount] contacts still leaves
     * [MIN_CONTACTS].
     *
     * So a user holding exactly the minimum cannot delete their way below it;
     * they have to add a replacement first. Editing is always allowed, which is
     * the way out for a contact that is merely wrong.
     */
    fun canDeleteContact(currentCount: Int): Boolean =
        currentCount - 1 >= MIN_CONTACTS

    /** Count the Contacts tab asks every user to reach (spec objective). */
    const val MIN_CONTACTS = 3

    /** Upper bound, so one account cannot fan an alert out indefinitely. */
    const val MAX_CONTACTS = 10

    const val MAX_CONTACT_NAME_LENGTH = 100
    const val MAX_RELATIONSHIP_LENGTH = 50

    private const val PH_COUNTRY_CODE = "+63"

    /** Separators stripped from a typed number before it is classified. */
    private val PHONE_SEPARATORS = charArrayOf('-', '(', ')', '.').concatToString()

    private val E164 = Regex("""^\+[0-9]{8,15}$""")

    /**
     * The `9XXXXXXXXX` subscriber part of a PH mobile number in [compact], or
     * null when [compact] is not one.
     */
    private fun phMobileSubscriberPart(compact: String): String? {
        val rest = when {
            compact.startsWith("+63") -> compact.substring(3)
            compact.startsWith("63") -> compact.substring(2)
            compact.startsWith("0") -> compact.substring(1)
            else -> return null
        }
        return rest.takeIf {
            it.length == PH_SUBSCRIBER_LENGTH && it.startsWith('9') && it.all(Char::isDigit)
        }
    }

    /** `9` plus nine digits. */
    private const val PH_SUBSCRIBER_LENGTH = 10
}
