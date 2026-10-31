package app.amisles.hanime.core.ui.util

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMapperTest {

    @Test
    fun unresolvableHostIsNetworkUnavailable() {
        assertEquals(
            ErrorKind.NETWORK_UNAVAILABLE,
            ErrorMapper.kindOf(UnknownHostException("hanime1.me"))
        )
    }

    @Test
    fun refusedConnectionIsNetworkUnavailable() {
        assertEquals(ErrorKind.NETWORK_UNAVAILABLE, ErrorMapper.kindOf(ConnectException("refused")))
    }

    @Test
    fun socketTimeoutIsTimeout() {
        assertEquals(ErrorKind.TIMEOUT, ErrorMapper.kindOf(SocketTimeoutException("timeout")))
    }

    @Test
    fun handshakeFailureIsTls() {
        assertEquals(ErrorKind.TLS, ErrorMapper.kindOf(SSLHandshakeException("bad cert")))
    }

    @Test
    fun certificateErrorIsTls() {
        assertEquals(ErrorKind.TLS, ErrorMapper.kindOf(CertificateException("expired")))
    }

    @Test
    fun plainIoFailureIsNetwork() {
        assertEquals(ErrorKind.NETWORK, ErrorMapper.kindOf(IOException("broken pipe")))
    }

    @Test
    fun nonIoFailureIsUnknown() {
        assertEquals(ErrorKind.UNKNOWN, ErrorMapper.kindOf(IllegalStateException("parse")))
    }

    @Test
    fun absentThrowableIsUnknown() {
        assertEquals(ErrorKind.UNKNOWN, ErrorMapper.kindOf(null))
    }
}
