package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueuePolicyTest {

    private fun task(id: Int, status: DownloadStatus = DownloadStatus.PENDING) = DownloadTask(
        id = id,
        title = "t$id",
        quality = "1080p",
        url = "https://example.com/$id",
        status = status
    )

    @Test
    fun `queue order wins over id order`() {
        val picked = DownloadQueuePolicy.pickPendingTask(
            tasks = listOf(task(1), task(2), task(3)),
            activeJobIds = emptySet(),
            exclude = emptySet(),
            queueOrder = listOf(3, 1),
            activeSlots = 0,
            concurrencyLimit = 3
        )
        assertEquals(3, picked?.id)
    }

    @Test
    fun `tasks outside queue order fall back to ascending id`() {
        val picked = DownloadQueuePolicy.pickPendingTask(
            tasks = listOf(task(7), task(4), task(9)),
            activeJobIds = emptySet(),
            exclude = emptySet(),
            queueOrder = emptyList(),
            activeSlots = 0,
            concurrencyLimit = 3
        )
        assertEquals(4, picked?.id)
    }

    @Test
    fun `queue order entries take precedence over smaller ids`() {
        val picked = DownloadQueuePolicy.pickPendingTask(
            tasks = listOf(task(2), task(5)),
            activeJobIds = emptySet(),
            exclude = emptySet(),
            queueOrder = listOf(5),
            activeSlots = 0,
            concurrencyLimit = 2
        )
        assertEquals(5, picked?.id)
    }

    @Test
    fun `skips running excluded and non pending tasks`() {
        val picked = DownloadQueuePolicy.pickPendingTask(
            tasks = listOf(
                task(1, DownloadStatus.DOWNLOADING),
                task(2),
                task(3),
                task(4, DownloadStatus.COMPLETED)
            ),
            activeJobIds = setOf(2),
            exclude = setOf(3),
            queueOrder = emptyList(),
            activeSlots = 0,
            concurrencyLimit = 4
        )
        assertNull(picked)
    }

    @Test
    fun `returns null when slots are exhausted`() {
        val picked = DownloadQueuePolicy.pickPendingTask(
            tasks = listOf(task(1)),
            activeJobIds = emptySet(),
            exclude = emptySet(),
            queueOrder = emptyList(),
            activeSlots = 3,
            concurrencyLimit = 3
        )
        assertNull(picked)
    }

    @Test
    fun `progress percent floors and guards zero total`() {
        assertEquals(33, DownloadQueuePolicy.progressPercent(1, 3))
        assertEquals(0, DownloadQueuePolicy.progressPercent(5, 0))
        assertEquals(100, DownloadQueuePolicy.progressPercent(10, 10))
    }

    @Test
    fun `persist throttle honours interval boundary`() {
        assertFalse(DownloadQueuePolicy.shouldPersistProgress(lastPersistAt = 1_000, now = 3_999, intervalMs = 3_000))
        assertTrue(DownloadQueuePolicy.shouldPersistProgress(lastPersistAt = 1_000, now = 4_000, intervalMs = 3_000))
        assertTrue(DownloadQueuePolicy.shouldPersistProgress(lastPersistAt = 0, now = 5_000, intervalMs = 3_000))
    }

    @Test
    fun `progress forwarding keeps status changes and percent deltas`() {
        assertTrue(DownloadQueuePolicy.shouldForwardProgress(statusChanged = true, lastPercent = 10, percent = 10))
        assertTrue(DownloadQueuePolicy.shouldForwardProgress(statusChanged = false, lastPercent = null, percent = 10))
        assertFalse(DownloadQueuePolicy.shouldForwardProgress(statusChanged = false, lastPercent = 10, percent = 10))
        assertTrue(DownloadQueuePolicy.shouldForwardProgress(statusChanged = false, lastPercent = 10, percent = 11))
    }
}
