package app.amisles.hanime.core.ui.util

import android.content.Context
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import app.amisles.hanime.core.ui.R

enum class ErrorKind {
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    TLS,
    NETWORK,
    UNKNOWN
}

object ErrorMapper {

    fun kindOf(throwable: Throwable?): ErrorKind = when (throwable) {
        null -> ErrorKind.UNKNOWN
        is UnknownHostException,
        is ConnectException,
        is NoRouteToHostException -> ErrorKind.NETWORK_UNAVAILABLE
        is SocketTimeoutException,
        is InterruptedIOException -> ErrorKind.TIMEOUT
        is SSLException,
        is CertificateException -> ErrorKind.TLS
        is IOException -> ErrorKind.NETWORK
        else -> ErrorKind.UNKNOWN
    }

    fun message(context: Context, throwable: Throwable?, fallback: String): String =
        when (kindOf(throwable)) {
            ErrorKind.NETWORK_UNAVAILABLE -> context.getString(R.string.error_network_unavailable)
            ErrorKind.TIMEOUT -> context.getString(R.string.error_network_timeout)
            ErrorKind.TLS -> context.getString(R.string.error_network_tls)
            ErrorKind.NETWORK -> context.getString(R.string.error_network_generic)
            ErrorKind.UNKNOWN -> fallback
        }
}
