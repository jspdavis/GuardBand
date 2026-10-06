package com.example.guardband.data.repository

import com.example.guardband.data.DeviceConstants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InMemoryAlertRepository], the test double that has to stay interchangeable
 * with [FirebaseAlertRepository] — same ordering, same window semantics.
 */
class InMemoryAlertRepositoryTest {

    private val repository = InMemoryAlertRepository(DeviceConstants.DEFAULT_DEVICE_ID)

    @Test
    fun `history comes back newest first`() = runTest {
        val history = repository.observeAlertHistory().first().getOrThrow()

        assertEquals(listOf(4L, 3L, 2L, 1L), history.alerts.map { it.sequenceId })
    }

    @Test
    fun `the seeded window reports its raw count and no malformed entries`() = runTest {
        val history = repository.observeAlertHistory().first().getOrThrow()

        assertEquals(4, history.rawCount)
        assertEquals(0, history.malformedCount)
    }

    @Test
    fun `the limit keeps the newest entries, not the first ones`() = runTest {
        val history = repository.observeAlertHistory(limit = 2).first().getOrThrow()

        assertEquals(listOf(4L, 3L), history.alerts.map { it.sequenceId })
        assertEquals(2, history.rawCount)
    }

    @Test
    fun `a limit larger than the stored history returns everything`() = runTest {
        val history = repository.observeAlertHistory(limit = 500).first().getOrThrow()

        assertEquals(4, history.alerts.size)
    }

    @Test
    fun `tracking updates are still present because filtering is the ViewModel's job`() = runTest {
        val history = repository.observeAlertHistory().first().getOrThrow()

        assertTrue(history.alerts.any { it.type == "TRACKING_UPDATE" })
    }

    @Test
    fun `latest is the highest sequenceId`() = runTest {
        val latest = repository.observeLatestAlert().first().getOrThrow()

        assertEquals(4L, latest?.sequenceId)
    }

    @Test
    fun `another device sees no alerts rather than this device's`() = runTest {
        val other = InMemoryAlertRepository("guardband-999")

        assertTrue(other.observeAlertHistory().first().getOrThrow().alerts.isEmpty())
        assertEquals(null, other.observeLatestAlert().first().getOrThrow())
    }
}
