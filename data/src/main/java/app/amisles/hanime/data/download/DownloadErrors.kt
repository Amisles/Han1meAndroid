package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadErrorCode
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 下载失败的标记异常与面向用户的错误归类。
 *
 * 几个异常都是「需换策略」或「不可重试」的信号，由下载链路捕获后决定回退单连接、整份重下或直接失败。
 */
// 服务器忽略 Range、对分块请求返回 200 时抛出的标记异常
internal class RangeNotSupportedException(message: String) : IOException(message)

// 源文件内容/大小已变化（Content-Range 总量与本地 totalBytes 不符）；续传会拼出坏文件，须整份重下
internal class SourceChangedException(message: String) : IOException(message)

// 分块路径在本机/当前网络下持续无收益（反复被驱逐），据此降级为单连接续传而非判失败
internal class ChunkedInefficientException(message: String) : IOException(message)

// 存储空间不足：不可重试（重试只会空转），需直接失败并给出明确原因
internal class NotEnoughSpaceException(message: String) : IOException(message)

internal fun classifyError(e: Throwable): DownloadErrorCode = when (e) {
    is NotEnoughSpaceException -> DownloadErrorCode.STORAGE_FULL
    is SocketTimeoutException -> DownloadErrorCode.NETWORK_TIMEOUT
    is UnknownHostException -> DownloadErrorCode.NETWORK_DNS
    is SSLException -> DownloadErrorCode.NETWORK_TLS
    is ConnectException -> DownloadErrorCode.NETWORK_CONNECT
    is IOException -> DownloadErrorCode.NETWORK_IO
    else -> DownloadErrorCode.UNKNOWN
}

internal fun classifyHttpError(code: Int): DownloadErrorCode = when (code) {
    in 400..499 -> DownloadErrorCode.HTTP_REJECTED
    in 500..599 -> DownloadErrorCode.HTTP_SERVER
    else -> DownloadErrorCode.UNKNOWN
}
