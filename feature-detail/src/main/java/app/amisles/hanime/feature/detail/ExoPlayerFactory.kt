package app.amisles.hanime.feature.detail

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 进程级 ExoPlayer 工厂。
 *
 * 针对详情页裸 ExoPlayer「加载慢、反复卡顿且不可恢复」的问题做多处理：
 * 1. SimpleCache 磁盘缓存 —— 再次进入同一视频秒开；卡顿后播放器可从本地缓存补数据，避免无限转圈。
 * 2. 浏览器 User-Agent 的 HTTP 数据源 —— 部分 CDN 会对默认 UA（ExoPlayer/...）做单连接限速，
 *    伪装成 Chrome Mobile 可绕过，拉到与 Edge 同档的带宽。
 * 3. 网络感知的 LoadControl —— 按 Wi-Fi / 移动数据 / 弱网动态切换缓冲区间（见 §5）。
 * 4. 下一集预缓存预热 —— [warmCacheFor] 在 IO 线程把相关视频首段写入 SimpleCache，进入即命中本地（见 §1）。
 *
 * SimpleCache 必须是进程级单例（同一缓存目录不能被实例化两次），故用 AtomicReference 缓存。
 */
@OptIn(UnstableApi::class)
object ExoPlayerFactory {

    // 伪装成 Chrome Mobile，避免 CDN 对 ExoPlayer 默认 UA 做单连接限速。
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // 磁盘缓存上限：512MB，按 LRU 自动淘汰最久未用的片段。
    private const val CACHE_MAX_BYTES = 512L * 1024 * 1024

    // 预缓存只取前 2MB，足够首帧 + 起播缓冲命中本地。
    private const val PREWARM_BYTES = 2L * 1024 * 1024

    private val cacheRef = AtomicReference<SimpleCache?>(null)

    // 预热作用域：仅用于 Application 启动期在 IO 线程构建 SimpleCache（见 prewarmCache）。
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    private fun getCache(context: Context): SimpleCache {
        var cache = cacheRef.get()
        if (cache == null) {
            val cacheDir = File(context.applicationContext.cacheDir, "exoplayer-video-cache").apply {
                if (!exists()) mkdirs()
            }
            val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            cache = SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES),
                databaseProvider
            )
            cacheRef.set(cache)
        }
        return cache
    }

    /**
     * 后台预热 SimpleCache，避免首次进入详情页在组合期（主线程）同步执行
     * StandaloneDatabaseProvider 初始化与缓存目录扫描带来的首开卡顿（播放器审查 P2-7）。
     *
     * 典型路径下缓存会在用户导航到详情页之前于 IO 线程构建完成；
     * 若仍属冷启动（预热尚未完成即打开详情页），[getCache] 会回退到主线程同步构建，
     * [getCache] 的 @Synchronized 保证两种路径下都只会真实构建一次，不会重复实例化。
     */
    fun prewarmCache(context: Context) {
        warmupScope.launch {
            try {
                getCache(context)
            } catch (_: Exception) {
                // 预热失败不影响后续主线程兜底构建
            }
        }
    }

    /**
     * 预热下一集：把直链首段（[PREWARM_BYTES]）写入 SimpleCache，使进入下一集时首帧即可命中本地
     * （播放器审查 §1 预加载）。在 IO 协程执行；失败仅忽略，不影响当前播放。
     */
    fun warmCacheFor(url: String, context: Context) {
        if (url.isBlank()) return
        warmupScope.launch {
            try {
                val cache = getCache(context)
                val upstreamFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(BROWSER_UA)
                    .setConnectTimeoutMs(10_000)
                    .setReadTimeoutMs(10_000)
                    .setAllowCrossProtocolRedirects(true)
                val dataSource = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
                try {
                    val spec = DataSpec.Builder()
                        .setUri(url)
                        .setPosition(0L)
                        .setLength(PREWARM_BYTES)
                        .build()
                    dataSource.open(spec)
                    val buf = ByteArray(16 * 1024)
                    while (dataSource.read(buf, 0, buf.size) != C.RESULT_END_OF_INPUT) {
                        // 仅消耗并写入缓存，不持有数据
                    }
                } finally {
                    // 关闭失败仅吞掉，避免掩盖 try 块里真正的异常；预热本身失败也不影响播放
                    runCatching { dataSource.close() }
                }
            } catch (_: Exception) {
                // 预热失败不影响播放
            }
        }
    }

    /**
     * 网络类型分类，用于动态缓冲策略（播放器审查 §5）。
     */
    enum class NetworkClass { WIFI, CELLULAR, OTHER }

    /**
     * 读取当前网络类型。无权限/异常时回退 [NetworkClass.OTHER]。
     */
    fun getCurrentNetworkClass(context: Context): NetworkClass {
        // minSdk 为 API 30，可直接使用 NetworkCapabilities（API 21+），无需已废弃的 ConnectivityManager.TYPE_* 与 activeNetworkInfo
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkClass.OTHER
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkClass.OTHER
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> NetworkClass.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkClass.CELLULAR
            else -> NetworkClass.OTHER
        }
    }

    /**
     * 依据当前网络类型构建 LoadControl：
     * - Wi-Fi：大缓冲（30/90s），更稳；
     * - 移动数据：中小缓冲（15/45s），省流量、更快起播；
     * - 其它/弱网：更小缓冲（10/30s）且起播缓冲降到 1.5s，加速首帧。
     */
    fun buildLoadControlForNetwork(context: Context): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder().setPrioritizeTimeOverSizeThresholds(true)
        return when (getCurrentNetworkClass(context)) {
            NetworkClass.WIFI -> builder.setBufferDurationsMs(30_000, 90_000, 2_500, 5_000).build()
            NetworkClass.CELLULAR -> builder.setBufferDurationsMs(15_000, 45_000, 2_500, 5_000).build()
            NetworkClass.OTHER -> builder.setBufferDurationsMs(10_000, 30_000, 1_500, 4_000).build()
        }
    }

    fun buildVideoPlayer(context: Context): ExoPlayer {
        val cache = getCache(context)

        // 上游直连数据源：浏览器 UA + 跨协议重定向（部分直链会 http→https 跳转）+ 适度超时。
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_UA)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)

        // 缓存数据源包住直连：先读缓存，未命中再走网络并回写；缓存写入异常时回退直连，不中断播放。
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        // 缓冲：按网络类型动态选择（Wi-Fi 大缓冲更稳，移动/弱网更省流更快起播）。
        val loadControl = buildLoadControlForNetwork(context)

        return ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = false
                volume = 1f
            }
    }
}
