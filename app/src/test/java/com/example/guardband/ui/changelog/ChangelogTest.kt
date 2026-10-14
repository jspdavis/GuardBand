package com.example.guardband.ui.changelog

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The changelog is appended by hand, so these keep a new entry in the shape
 * the screen expects: newest first, one card per day, nothing blank.
 */
class ChangelogTest {

    private val entries = Changelog.entries

    private fun parse(iso: String): Date = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(iso)!!
    } catch (e: ParseException) {
        fail("Not a yyyy-MM-dd date: $iso")
        throw e
    }

    @Test
    fun `there is at least one entry`() {
        assertTrue(entries.isNotEmpty())
    }

    @Test
    fun `every date is a real yyyy-MM-dd date`() {
        entries.forEach { parse(it.date) }
    }

    @Test
    fun `entries are newest first, one per day`() {
        val dates = entries.map { parse(it.date) }

        dates.zipWithNext().forEach { (newer, older) ->
            assertTrue("$newer should come after $older", newer.after(older))
        }
    }

    @Test
    fun `every entry has a title and at least one change, none blank`() {
        entries.forEach { entry ->
            assertTrue("Blank title on ${entry.date}", entry.title.isNotBlank())
            assertTrue("No changes on ${entry.date}", entry.changes.isNotEmpty())
            entry.changes.forEach {
                assertTrue("Blank change on ${entry.date}", it.isNotBlank())
            }
        }
    }

    @Test
    fun `every commit is a distinct short hash`() {
        val shortHash = Regex("^[0-9a-f]{7}$")

        entries.forEach {
            assertTrue("Bad commit ${it.commit} on ${it.date}", shortHash.matches(it.commit))
        }
        assertEquals(entries.size, entries.map { it.commit }.toSet().size)
    }
}
