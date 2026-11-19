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
import app.amisles.hanime.domain.result.AppException
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

    fun message(context: Context, throwable: Throwable?, fallback: String): String {
        if (throwable is AppException) return domainText(context, throwable.code)
        return when (kindOf(throwable)) {
            ErrorKind.NETWORK_UNAVAILABLE -> context.getString(R.string.error_network_unavailable)
            ErrorKind.TIMEOUT -> context.getString(R.string.error_network_timeout)
            ErrorKind.TLS -> context.getString(R.string.error_network_tls)
            ErrorKind.NETWORK -> context.getString(R.string.error_network_generic)
            ErrorKind.UNKNOWN -> fallback
        }
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
            DomainError.AUTHOR_PARSE_FAILED -> R.string.error_author_parse_failed
            DomainError.CLOUDFLARE_REQUIRED -> R.string.error_cloudflare_required
            DomainError.LOGIN_FAILED -> R.string.login_failed
            DomainError.LOGIN_REJECTED -> R.string.error_login_rejected
            DomainError.LOGIN_VERIFY_INCONCLUSIVE -> R.string.error_login_verify_inconclusive
            DomainError.NETWORK_UNAVAILABLE -> R.string.error_network_unavailable
            DomainError.NETWORK_TIMEOUT -> R.string.error_network_timeout
            DomainError.NETWORK_TLS -> R.string.error_network_tls
            DomainError.COMMENT_POST_FAILED -> R.string.comment_post_failed
            DomainError.COMMENT_LIKE_FAILED -> R.string.comment_like_failed
            DomainError.COMMENT_REPLY_FAILED -> R.string.comment_reply_failed
            DomainError.PLAYLIST_SAVE_FAILED -> R.string.playlist_save_failed
            DomainError.PLAYLIST_CREATE_FAILED -> R.string.playlist_create_failed
            DomainError.PLAYLIST_RESPONSE_INVALID -> R.string.playlist_response_invalid
        }
    )
}
