package app.amisles.hanime.feature.detail.playback

import android.content.Context
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer

/**
 * 播放器与播放服务之间的进程内桥接。
 *
 * 播放器仍由页面创建与释放（[app.amisles.hanime.feature.detail.ExoPlayerFactory] 的线程约束不变：
 * 构建与释放都必须在主线程），服务只借用同一个实例来建立 [androidx.media3.session.MediaSession]，
 * 从而避免把播放器所有权搬到 Service 里引发的大规模重构。
 *
 * 生命周期：
 * - 页面构建播放器后 [bind]；
 * - 首次真正开始播放时 [ensureService] 拉起前台服务，通知栏 / 锁屏 / 耳机按键由此接管；
 * - 页面销毁时 [unbind] 停止服务并释放会话。
 */
object PlaybackSessionBridge {

    @Volatile
    internal var player: ExoPlayer? = null
        private set

    @Volatile
    private var serviceStarted = false

    /** 记录当前页面的播放器实例；必须在主线程调用（与播放器构建同一线程）。 */
    fun bind(player: ExoPlayer) {
        this.player = player
    }

    /**
     * 拉起播放服务。只在真正开始播放时调用：
     * 提前启动会让一个永远不会进入前台的服务挂在后台，且 Android 12+ 对后台启动前台服务有限制。
     */
    fun ensureService(context: Context) {
        if (serviceStarted) return
        val appContext = context.applicationContext
        // 前台服务启动受限时（例如已经在后台）静默降级：播放不受影响，只是没有媒体通知
        runCatching {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
            serviceStarted = true
        }
    }

    /** 页面销毁时解绑：先停服务（会话随之释放），调用方随后再释放播放器。 */
    fun unbind(context: Context) {
        player = null
        if (!serviceStarted) return
        serviceStarted = false
        val appContext = context.applicationContext
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
    }

    internal fun isPlaying(): Boolean = player?.isPlaying == true
}
