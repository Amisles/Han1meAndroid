package app.amisles.hanime.feature.detail

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 视频请求头日志：包在 [HttpDataSource] 外层，每次 [open] 打印一条完整的请求头。
 *
 * 为什么需要这一层：ExoPlayer 没有「请求即将发出」的回调，而请求头分散在三处 ——
 * 工厂的默认头（UA / Referer）、单次请求的 [DataSpec.httpRequestHeaders]，以及
 * [androidx.media3.datasource.DefaultHttpDataSource] 内部按 position/length 自行推导的
 * Range 与 Accept-Encoding。只看工厂侧配置会漏掉后两者、与实际发出的请求对不上，
 * 因此在这里把三路合并后打印，合并顺序与 [androidx.media3.datasource.DefaultHttpDataSource]
 * 构造连接时一致，保证打印结果即真实请求。
 *
 * 纯只读包装：[getUri]、读取与异常都原样转发，不改动任何播放行为。
 *
 * @param requestIndex 同一播放器内跨数据源实例共享的请求序号
 * @param firstRequestLogged 同一播放器内共享的「首个请求」标记
 */
@OptIn(UnstableApi::class)
internal class LoggingHttpDataSource(
    private val upstream: HttpDataSource,
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String>,
    private val requestIndex: AtomicLong,
    private val firstRequestLogged: AtomicBoolean
) : HttpDataSource {

    override fun open(dataSpec: DataSpec): Long {
        val sequence = requestIndex.incrementAndGet()
        val isFirst = firstRequestLogged.compareAndSet(false, true)
        val url = dataSpec.uri.toString()
        val headers = collectRequestHeaders(dataSpec)

        Log.i(
            TAG,
            "[播放器请求头] 请求 #" + sequence + (if (isFirst) "（进入详情页后的首个请求）" else "") + "\n" +
                "请求地址: " + url + "\n" +
                "请求范围: position=" + dataSpec.position + ", length=" + describeLength(dataSpec.length) + "\n" +
                "请求头(共 " + headers.size + " 项):\n" + formatHeaders(headers)
        )

        return try {
            upstream.open(dataSpec)
        } catch (e: IOException) {
            // 失败时再打一次完整上下文：403 / 超时这类问题，恰恰需要对照当时发出的头
            Log.i(
                TAG,
                "[播放器请求头] 请求失败 #" + sequence + "\n" +
                    "请求地址: " + url + "\n" +
                    "请求头:\n" + formatHeaders(headers) + "\n" +
                    "异常: " + e.javaClass.simpleName + ": " + e.message
            )
            throw e
        }
    }

    /**
     * 合并三路请求头，顺序与 DefaultHttpDataSource#makeConnection 写连接的顺序一致
     * （后写入的覆盖先写入的）：默认头 → 单次请求头 → Range → User-Agent → Accept-Encoding。
     */
    private fun collectRequestHeaders(dataSpec: DataSpec): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        defaultRequestProperties.forEach { (name, value) -> putHeader(headers, name, value) }
        dataSpec.httpRequestHeaders.forEach { (name, value) -> putHeader(headers, name, value) }
        // 上游按 position / length 推导 Range；整段读取时不发该头
        buildRangeHeader(dataSpec)?.let { putHeader(headers, HEADER_RANGE, it) }
        if (userAgent.isNotEmpty()) putHeader(headers, HEADER_USER_AGENT, userAgent)
        // 未显式指定时，上游固定写入 Accept-Encoding: identity（Media3 默认不允许 gzip）
        if (!containsHeader(headers, HEADER_ACCEPT_ENCODING)) {
            putHeader(headers, HEADER_ACCEPT_ENCODING, ACCEPT_ENCODING_IDENTITY)
        }
        return headers
    }

    /** 头名大小写不敏感：重复写入时先删旧键，避免日志里出现大小写重复项。 */
    private fun putHeader(headers: MutableMap<String, String>, name: String, value: String) {
        headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let { headers.remove(it) }
        headers[name] = value
    }

    private fun containsHeader(headers: Map<String, String>, name: String): Boolean =
        headers.keys.any { it.equals(name, ignoreCase = true) }

    /** 与 androidx.media3.datasource.HttpUtil#buildRangeRequestHeader 的规则一致。 */
    private fun buildRangeHeader(dataSpec: DataSpec): String? {
        val position = dataSpec.position
        val length = dataSpec.length
        if (position == 0L && length == C.LENGTH_UNSET.toLong()) return null
        val end = if (length == C.LENGTH_UNSET.toLong()) "" else (position + length - 1).toString()
        return "bytes=" + position + "-" + end
    }

    private fun formatHeaders(headers: Map<String, String>): String {
        if (headers.isEmpty()) return "（无）"
        return headers.entries.joinToString("\n") { (name, value) -> "- " + name + ": " + value }
    }

    private fun describeLength(length: Long): String =
        if (length == C.LENGTH_UNSET.toLong()) "未指定（读到末尾）" else length.toString()

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseCode(): Int = upstream.responseCode

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun setRequestProperty(name: String, value: String) = upstream.setRequestProperty(name, value)

    override fun clearRequestProperty(name: String) = upstream.clearRequestProperty(name)

    override fun clearAllRequestProperties() = upstream.clearAllRequestProperties()

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun close() = upstream.close()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)

    private companion object {
        // 统一 TAG：logcat 里按 HanimePlayer 过滤，即可只看播放器请求头日志
        const val TAG = "HanimePlayer"

        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_RANGE = "Range"
        const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"

        // DefaultHttpDataSource 在未允许 gzip 时写入的值
        const val ACCEPT_ENCODING_IDENTITY = "identity"
    }
}

/**
 * 让 [HttpDataSource.Factory] 产出的每个数据源都套上 [LoggingHttpDataSource]。
 * 默认请求头与 TransferListener 均转发给上游，行为与直接使用上游工厂一致。
 *
 * 序号与「首个请求」标记放在工厂里：同一次播放可能创建多个数据源实例，
 * 由工厂共享才能让日志里的请求序号连续。
 */
@OptIn(UnstableApi::class)
internal class LoggingHttpDataSourceFactory(
    private val upstream: HttpDataSource.Factory,
    private val userAgent: String,
    defaultRequestProperties: Map<String, String>
) : HttpDataSource.Factory {

    private val requestIndex = AtomicLong(0L)
    private val firstRequestLogged = AtomicBoolean(false)

    /** 实际生效的默认请求头，新建数据源时取当前快照。 */
    @Volatile
    private var requestProperties: Map<String, String> = LinkedHashMap(defaultRequestProperties)

    override fun createDataSource(): HttpDataSource = LoggingHttpDataSource(
        upstream = upstream.createDataSource(),
        userAgent = userAgent,
        defaultRequestProperties = requestProperties,
        requestIndex = requestIndex,
        firstRequestLogged = firstRequestLogged
    )

    override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory =
        apply {
            requestProperties = LinkedHashMap(defaultRequestProperties)
            upstream.setDefaultRequestProperties(defaultRequestProperties)
        }
}
