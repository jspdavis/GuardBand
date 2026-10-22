package com.example.guardband.ui.contacts

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.utils.InputValidator

/**
 * Which relationship category the chip row is filtering by.
 *
 * [All] shows every contact. [Family] is a convenience alias for the three
 * family sub-types. Each named filter matches by its [label] against the
 * contact's [EmergencyContact.relationship] field (case-insensitive). [All]
 * skips the match entirely.
 *
 * The labels here must stay in sync with the string resources used in
 * [ContactsFragment] to populate the chip row.
 */
sealed class ContactRelationshipFilter(val label: String) {

    /** Show all contacts regardless of relationship. */
    object All : ContactRelationshipFilter("")

    /**
     * Convenience alias: matches contacts whose relationship is one of the
     * three family sub-types (Parent, Sibling, Relative). Case-insensitive.
     */
        object Family : ContactRelationshipFilter("Family")

    /**
     * Matches contacts designated as primary emergency contact (starred / favorited).
     */
    object Emergency : ContactRelationshipFilter("Emergency")

    data class Specific(val relationship: String) : ContactRelationshipFilter(relationship)

    /**
     * True when [contact] should be included under this filter.
     *
     * [All] always returns true. [Family] accepts Parent, Sibling or Relative.
     * [Emergency] matches contacts designated as emergency contacts (isEmergency = true).
     * [Specific] requires a case-insensitive exact match against the stored
     * relationship.
     */
    fun matches(contact: EmergencyContact): Boolean = when (this) {
        All -> true
        Family -> contact.relationship.trim().lowercase() in FAMILY_LABELS
        Emergency -> contact.isEmergency
        is Specific -> contact.relationship.trim().equals(relationship.trim(), ignoreCase = true)
    }

    companion object {
        /** The relationship labels that count as "Family". */
        private val FAMILY_LABELS = setOf("parent", "sibling", "relative")
    }
}

/**
 * Why the list is showing nothing — distinguishes three distinct empty states
 * so the Fragment can show the right message and the right action.
 */
enum class ContactsEmptyReason {
    /** No contacts have been added yet. */
    NO_CONTACTS,

    /** Contacts exist but none match the current search query. */
    NO_SEARCH_RESULTS,

    /** Contacts exist but none belong to the active relationship filter. */
    NO_FILTER_RESULTS,
}

/** Render state for [ContactsFragment]. */
data class ContactsUiState(
    /** True until the first read of the contact list completes. */
    val isLoading: Boolean = false,

    /**
     * True while an add, edit or delete is in flight.
     *
     * Separate from [isLoading] so a mutation does not make the list look like
     * it is reloading, and so the double-submit guard does not also block on a
     * slow first read.
     */
    val isMutating: Boolean = false,

    /** The raw, unfiltered list as it arrives from the repository. */
    val contacts: List<EmergencyContact> = emptyList(),

    /**
     * True when the last read failed. [contacts] then still holds the last
     * good list, which is deliberate: stale contacts are more use in an
     * emergency than none.
     */
    val loadFailed: Boolean = false,

    /** The current search query. Empty string means no active search. */
    val query: String = "",

    /** The active relationship chip filter. Defaults to showing all contacts. */
    val selectedRelationship: ContactRelationshipFilter = ContactRelationshipFilter.All,
) {
    /**
     * Contacts after applying both [selectedRelationship] and [query].
     *
     * Filtering is purely in-memory: the full list already arrived via
     * [observeContacts], so there is no database read per keystroke.
     *
     * The query matches case-insensitively against name and phone, matching
     * the prompt "Search by name or phone number."
     */
    val filteredContacts: List<EmergencyContact>
        get() {
            var result = contacts.filter { selectedRelationship.matches(it) }
            val trimmed = query.trim()
            if (trimmed.isNotEmpty()) {
                result = result.filter { contact ->
                    contact.name.contains(trimmed, ignoreCase = true) ||
                        contact.phone.contains(trimmed, ignoreCase = true)
                }
            }
            return result
        }

    /** Drives the "n of MIN minimum" notice; hidden once the user is at or above it. */
    val showMinimumNotice: Boolean
        get() = !isLoading && contacts.size < InputValidator.MIN_CONTACTS

    /**
     * Non-null when the filtered list is empty and we should show an empty
     * state; null when the list has content (or is still loading).
     *
     * Deliberately does NOT fire when [loadFailed]: the Fragment should keep
     * the stale list visible rather than replacing it with an empty-state view.
     */
    val emptyReason: ContactsEmptyReason?
        get() {
            if (isLoading || loadFailed) return null
            if (contacts.isEmpty()) return ContactsEmptyReason.NO_CONTACTS
            if (filteredContacts.isNotEmpty()) return null
            // Contacts exist but are filtered out — determine which filter caused it.
            return if (query.trim().isNotEmpty()) {
                ContactsEmptyReason.NO_SEARCH_RESULTS
            } else {
                ContactsEmptyReason.NO_FILTER_RESULTS
            }
        }
}
