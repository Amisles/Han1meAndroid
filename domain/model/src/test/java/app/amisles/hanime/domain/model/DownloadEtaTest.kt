package app.amisles.hanime.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadEtaTest {
    @Test
    fun `unknown speed or size has no estimate`() {
        assertEquals(0L, DownloadEta.remainingMs(10, 100, 0))
        assertEquals(0L, DownloadEta.remainingMs(0, 0, 100))
    }

    @Test
    fun `remaining time uses the current speed`() {
        assertEquals(10_000L, DownloadEta.remainingMs(0, 100, 10))
        assertEquals(5_000L, DownloadEta.remainingMs(50, 100, 10))
    }

    @Test
    fun `completed or over-reported progress never goes negative`() {
        assertEquals(0L, DownloadEta.remainingMs(100, 100, 10))
        assertEquals(0L, DownloadEta.remainingMs(120, 100, 10))
    }
}
