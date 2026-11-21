package app.amisles.hanime.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStatusPolicyTest {

    @Test
    fun `paused is not active`() {
        assertTrue(DownloadStatusPolicy.isActive(DownloadStatus.DOWNLOADING))
        assertTrue(DownloadStatusPolicy.isActive(DownloadStatus.PENDING))
        assertFalse(DownloadStatusPolicy.isActive(DownloadStatus.PAUSED))
        assertFalse(DownloadStatusPolicy.isActive(DownloadStatus.FAILED))
        assertFalse(DownloadStatusPolicy.isActive(DownloadStatus.COMPLETED))
    }

    @Test
    fun `aggregate prefers active over completed`() {
        assertEquals(
            DownloadStatus.DOWNLOADING,
            DownloadStatusPolicy.aggregate(
                listOf(DownloadStatus.COMPLETED, DownloadStatus.DOWNLOADING)
            )
        )
        assertEquals(
            DownloadStatus.PENDING,
            DownloadStatusPolicy.aggregate(
                listOf(DownloadStatus.COMPLETED, DownloadStatus.PENDING)
            )
        )
    }

    @Test
    fun `aggregate prefers paused and failed over completed`() {
        assertEquals(
            DownloadStatus.PAUSED,
            DownloadStatusPolicy.aggregate(
                listOf(DownloadStatus.COMPLETED, DownloadStatus.PAUSED)
            )
        )
        assertEquals(
            DownloadStatus.FAILED,
            DownloadStatusPolicy.aggregate(
                listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED)
            )
        )
    }

    @Test
    fun `empty statuses aggregate to null`() {
        assertNull(DownloadStatusPolicy.aggregate(emptyList()))
    }
}
