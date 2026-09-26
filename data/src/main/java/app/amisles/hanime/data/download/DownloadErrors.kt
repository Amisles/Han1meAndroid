package app.amisles.hanime.data.download

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 下载失败的标记异常与面向用户的错误文案归类。
 *
 * 三个异常都是「需换策略」的信号，由下载链路捕获后决定回退单连接、整份重下或直接失败。
 */
// 服务器忽略 Range、对分块请求返回 200 时抛出的标记异常
internal class RangeNotSupportedException(message: String) : IOException(message)

// 源文件内容/大小已变化（Content-Range 总量与本地 totalBytes 不符）；续传会拼出坏文件，须整份重下
internal class SourceChangedException(message: String) : IOException(message)

// 分块路径在本机/当前网络下持续无收益（反复被驱逐），据此降级为单连接续传而非判失败
internal class ChunkedInefficientException(message: String) : IOException(message)

internal fun classifyError(e: Throwable): String {
    return when (e) {
        is SocketTimeoutException -> "网络超时，请检查网络后重试"
        is UnknownHostException -> "无法解析服务器地址（DNS 失败）"
        is SSLException -> "安全连接失败（SSL 错误）"
        is ConnectException -> "无法建立连接，请检查网络"
        is java.io.IOException -> "网络读写错误：${e.message ?: "未知"}"
        else -> "下载失败：${e.message ?: "未知错误"}"
    }
}

internal fun classifyHttpError(code: Int): String {
    return when (code) {
        401, 403 -> "资源不可用（无权限，HTTP $code）"
        404 -> "资源不存在（HTTP 404）"
        in 400..499 -> "请求被拒绝（HTTP $code）"
        in 500..599 -> "服务器错误（HTTP $code）"
        else -> "下载失败（HTTP $code）"
    }
}
