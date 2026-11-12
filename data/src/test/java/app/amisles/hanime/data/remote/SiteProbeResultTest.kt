package app.amisles.hanime.data.remote

import app.amisles.hanime.domain.model.SiteProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SiteProbeResultTest {

    @Test
    fun `test probeHostOf with valid url`() {
        assertEquals("hanime1.me", NetworkDiagnostics().probeHostOf("https://hanime1.me"))
    }

    @Test
    fun `test probeHostOf with trailing path`() {
        assertEquals("hanime1.me", NetworkDiagnostics().probeHostOf("https://hanime1.me/enter?id=1"))
    }

    @Test
    fun `test probeHostOf with invalid url`() {
        assertNull(NetworkDiagnostics().probeHostOf("not-a-url"))
    }

    @Test
    fun `test probeHostOf with empty url`() {
        assertNull(NetworkDiagnostics().probeHostOf(""))
    }

    @Test
    fun `test probeHostOf with blank host`() {
        assertNull(NetworkDiagnostics().probeHostOf("http://"))
    }

    @Test
    fun `test probeFailureReason unknown host is dns`() {
        val reason = NetworkDiagnostics().probeFailureReason(UnknownHostException("dns"))
        assertEquals(SiteProbeResult.Reason.DNS, reason)
    }

    @Test
    fun `test probeFailureReason socket timeout is timeout`() {
        val reason = NetworkDiagnostics().probeFailureReason(SocketTimeoutException("timeout"))
        assertEquals(SiteProbeResult.Reason.TIMEOUT, reason)
    }

    @Test
    fun `test probeFailureReason connect exception is io`() {
        val reason = NetworkDiagnostics().probeFailureReason(ConnectException("refused"))
        assertEquals(SiteProbeResult.Reason.IO, reason)
    }

    @Test
    fun `test probeFailureReason generic io is io`() {
        val reason = NetworkDiagnostics().probeFailureReason(IOException("io"))
        assertEquals(SiteProbeResult.Reason.IO, reason)
    }
}
