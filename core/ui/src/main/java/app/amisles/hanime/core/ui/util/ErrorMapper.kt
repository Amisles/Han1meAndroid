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
import app.amisles.hanime.domain.result.DomainError

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

    fun message(
        context: Context,
        throwable: Throwable?,
        code: DomainError?,
        fallback: String
    ): String = code?.let { domainText(context, it) } ?: message(context, throwable, fallback)

    private fun domainText(context: Context, code: DomainError): String = context.getString(
        when (code) {
            DomainError.COMMENT_LOGIN_REQUIRED -> R.string.comment_login_required
            DomainError.COMMENT_PARSE_FAILED -> R.string.error_comment_parse_failed
            DomainError.LIKE_LOGIN_REQUIRED -> R.string.error_login_required_like
            DomainError.REPLY_LOGIN_REQUIRED -> R.string.error_login_required_reply
            DomainError.REPLY_PARSE_FAILED -> R.string.error_reply_parse_failed
            DomainError.SUBSCRIBE_LOGIN_REQUIRED -> R.string.detail_subscribe_login_required
            DomainError.SUBSCRIBE_ARTIST_MISSING -> R.string.detail_subscribe_artist_missing
            DomainError.SUBSCRIBE_FAILED -> R.string.detail_subscribe_failed
            DomainError.CSRF_MISSING -> R.string.detail_csrf_missing
            DomainError.USER_ID_MISSING -> R.string.error_user_id_missing
            DomainError.SESSION_EXPIRED -> R.string.error_session_expired
            DomainError.ACCOUNT_UPDATE_FAILED -> R.string.error_account_update_failed
            DomainError.DETAIL_PARSE_FAILED -> R.string.error_detail_parse_failed
        }
    )
}
