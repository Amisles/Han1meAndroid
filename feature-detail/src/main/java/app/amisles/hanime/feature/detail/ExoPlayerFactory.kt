package app.amisles.hanime.feature.detail

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
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
 * 针对详情页裸 ExoPlayer「加载慢、反复卡顿且不可恢复」的问题做三处优化：
 * 1. SimpleCache 磁盘缓存 —— 再次进入同一视频秒开；卡顿后播放器可从本地缓存补数据，避免无限转圈。
 * 2. 浏览器 User-Agent 的 HTTP 数据源 —— 部分 CDN 会对默认 UA（ExoPlayer/...）做单连接限速，
 *    伪装成 Chrome Mobile 可绕过，拉到与 Edge 同档的带宽。
 * 3. 调优的 LoadControl —— 放大缓冲区间，给网络抖动留出余量，降低卡顿概率。
 *
 * SimpleCache 必须是进程级单例（同一缓存目录不能被实例化两次），故用 AtomicReference 缓存。
 */
object ExoPlayerFactory {

    // 伪装成 Chrome Mobile，避免 CDN 对 ExoPlayer 默认 UA 做单连接限速。
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // 磁盘缓存上限：512MB，按 LRU 自动淘汰最久未用的片段。
    private const val CACHE_MAX_BYTES = 512L * 1024 * 1024

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

        // 缓冲：最小 30s、最大 90s；起播缓冲 2.5s；rebuffer 后起播 5s；优先用时间维度判断而非大小。
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 90_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

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
