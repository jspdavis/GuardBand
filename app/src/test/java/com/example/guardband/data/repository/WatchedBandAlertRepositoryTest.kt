package com.example.guardband.data.repository

import com.example.guardband.testing.FakeAlertRepository
import com.example.guardband.testing.FakeAlertRepository.Companion.alert
import com.example.guardband.testing.FakeBandRepository
import com.example.guardband.testing.FakeBandRepository.Companion.watcher
import com.example.guardband.testing.FakeBandRepository.Companion.wearer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alert repository that follows the account's watched band: reads go to
 * that band, a change of band closes the old listener and opens the new one,
 * and an account with no band gets [AlertError.NoBand] rather than a read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchedBandAlertRepositoryTest {

    private val bands = FakeBandRepository(listOf(wearer("band-a")))

    private val bandA = FakeAlertRepository(
        history = AlertHistory(alerts = listOf(alert(1, "PANIC", deviceId = "band-a")), rawCount = 1)
    )
    private val bandB = FakeAlertRepository(
        history = AlertHistory(alerts = listOf(alert(7, "CHECKIN", deviceId = "band-b")), rawCount = 1)
    )

    /** Every band id a per-band repository was built for, in order. */
    private val built = mutableListOf<String>()

    private val repository = WatchedBandAlertRepository(
        bands = bands,
        forBand = { id ->
            built += id
            when (id) {
                "band-a" -> bandA
                "band-b" -> bandB
                else -> error("no fake for $id")
            }
        }
    )

    @Test
    fun `reads go to the watched band`() = runTest {
        val history = repository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW).first()

        assertEquals("band-a", history.getOrThrow().alerts.single().deviceId)
        assertEquals(listOf(AlertRepository.DEFAULT_HISTORY_WINDOW), bandA.requestedLimits)
    }

    @Test
    fun `the own band is read before any watched one`() = runTest {
        bands.bands.value = listOf(watcher("band-a"), wearer("band-b"))

        val history = repository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW).first()

        assertEquals("band-b", history.getOrThrow().alerts.single().deviceId)
    }

    @Test
    fun `an account with no band gets NoBand on every read and opens no listener`() = runTest {
        bands.bands.value = emptyList()

        assertEquals(AlertError.NoBand, repository.observeAlertHistory(10).first().exceptionOrNull())
        assertEquals(AlertError.NoBand, repository.observeLatestAlert().first().exceptionOrNull())
        assertEquals(AlertError.NoBand, repository.observeLatestCheckIn().first().exceptionOrNull())
        assertEquals(AlertError.NoBand, repository.observeIncident(5).first().exceptionOrNull())
        assertEquals(AlertError.NoBand, repository.observeStatuses().first().exceptionOrNull())
        assertTrue(built.isEmpty())
    }

    @Test
    fun `a change of band closes the old listener and reads the new band`() = runTest {
        val seen = mutableListOf<Result<AlertHistory>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW).toList(seen)
        }
        runCurrent()
        assertEquals(1, bandA.activeHistoryCollectors)

        bands.bands.value = listOf(wearer("band-b"))
        runCurrent()

        assertEquals(0, bandA.activeHistoryCollectors)
        assertEquals(1, bandB.activeHistoryCollectors)
        assertEquals("band-b", seen.last().getOrThrow().alerts.single().deviceId)
    }

    @Test
    fun `the same band again is not read again`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW).collect {}
        }
        runCurrent()

        // A new watched band is added; the selection is still band-a.
        bands.bands.value = listOf(wearer("band-a"), watcher("band-b"))
        runCurrent()

        assertEquals(1, bandA.historySubscriptions)
        assertEquals(listOf("band-a"), built)
    }

    @Test
    fun `losing the band mid-read closes the listener and says NoBand`() = runTest {
        val seen = mutableListOf<Result<AlertHistory>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW).toList(seen)
        }
        runCurrent()

        bands.bands.value = emptyList()
        runCurrent()

        assertEquals(0, bandA.activeHistoryCollectors)
        assertEquals(AlertError.NoBand, seen.last().exceptionOrNull())
    }

    @Test
    fun `a failed read of the band passes through unchanged`() = runTest {
        bandA.failHistory(AlertError.Network)

        val result = repository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW).first()

        assertEquals(AlertError.Network, result.exceptionOrNull())
    }

    @Test
    fun `acknowledge goes to the watched band, and fails with no band`() = runTest {
        assertTrue(repository.acknowledge(3).isSuccess)
        assertEquals(listOf(3L), bandA.requestedAcknowledge)

        bands.bands.value = emptyList()

        assertEquals(AlertError.NoBand, repository.acknowledge(4).exceptionOrNull())
        assertEquals(AlertError.NoBand, repository.resolve(4).exceptionOrNull())
        assertEquals(listOf(3L), bandA.requestedAcknowledge)
    }

    @Test
    fun `switching selectionStore switches repository to the selected band`() = runTest {
        val selectionStore = com.example.guardband.testing.FakeBandSelectionStore()
        bands.bands.value = listOf(wearer("band-a"), watcher("band-b"))

        val repoWithStore = WatchedBandAlertRepository(
            bands = bands,
            forBand = { id ->
                when (id) {
                    "band-a" -> bandA
                    "band-b" -> bandB
                    else -> error("no fake for $id")
                }
            },
            selectionStore = selectionStore
        )

        val seen = mutableListOf<Result<AlertHistory>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repoWithStore.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW).toList(seen)
        }
        runCurrent()

        assertEquals("band-a", seen.last().getOrThrow().alerts.single().deviceId)

        selectionStore.select("band-b")
        runCurrent()

        assertEquals("band-b", seen.last().getOrThrow().alerts.single().deviceId)
    }

    @Test
    fun `clearAlertHistory goes to the watched band, and fails with no band`() = runTest {
        assertTrue(repository.clearAlertHistory().isSuccess)
        assertEquals(1, bandA.clearAlertHistoryCalls)

        bands.bands.value = emptyList()

        assertEquals(AlertError.NoBand, repository.clearAlertHistory().exceptionOrNull())
        assertEquals(1, bandA.clearAlertHistoryCalls)
    }
}
