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
 * - 真正开始播放时 [ensureService] 拉起前台服务，通知栏 / 锁屏 / 耳机按键由此接管；
 * - 页面销毁时 [unbind] 停止服务并释放会话。
 *
 * **所有权校验**：同一时刻可能存在多个播放器（详情页 → 本地播放页转场、连播切集时新旧页面重叠）。
 * 因此 [unbind] 必须校验「当前登记的确实是自己」，否则先销毁的旧页面会把新页面刚登记的
 * 播放器清空，导致正在播放的会话失去通知。
 */
object PlaybackSessionBridge {

    /** 当前登记的播放器。只在主线程读写。 */
    internal var player: ExoPlayer? = null
        private set

    /** 最近一次启动服务用的应用上下文；服务由它拉起，也由它停止。 */
    private var appContext: Context? = null

    /** 服务是否已被拉起且尚未收到 onDestroy 回报。 */
    @Volatile
    private var serviceStarted = false

    /** 记录当前页面的播放器实例；必须在主线程调用（与播放器构建同一线程）。 */
    fun bind(player: ExoPlayer, context: Context) {
        this.player = player
        appContext = context.applicationContext
        // 服务已起但当时没拿到播放器（见 PlaybackService.ensureSession 的失败分支）：
        // 这里补一次启动，避免通知永久缺席
        if (serviceStarted) {
            startServiceSafely()
        }
    }

    /**
     * 拉起播放服务。只在真正开始播放时调用：
     * 提前启动会让一个永远不会进入前台的服务挂在后台，且 Android 12+ 对后台启动前台服务有限制。
     */
    fun ensureService(context: Context) {
        if (serviceStarted) return
        appContext = context.applicationContext
        startServiceSafely()
    }

    /**
     * 启动服务。
     *
     * 前台服务启动受限时（例如应用已在后台）静默降级：播放不受影响，只是暂时没有媒体通知；
     * 回到前台后 [bind] / 下次播放状态变化会重试。
     */
    private fun startServiceSafely() {
        val ctx = appContext ?: return
        runCatching {
            ctx.startService(Intent(ctx, PlaybackService::class.java))
            serviceStarted = true
        }
    }

    /**
     * 页面销毁时解绑。
     *
     * 只有当前登记者才能解绑：转场期间新旧页面会短暂共存，先销毁的旧页面若无条件清空，
     * 会把新页面刚登记的播放器抹掉，正在播放的会话随之失去通知。
     */
    fun unbind(player: ExoPlayer) {
        if (this.player !== player) return
        this.player = null
        if (!serviceStarted) return
        serviceStarted = false
        val ctx = appContext ?: return
        runCatching { ctx.stopService(Intent(ctx, PlaybackService::class.java)) }
    }

    /**
     * 「移除播放器」：用户在通知栏点关闭时调用。
     *
     * 语义是「停止播放并撤掉通知」——暂停播放器（页面侧随之回到暂停态）后停掉服务，
     * 会话与通知一起消失。播放器实例仍归页面所有，不由这里释放。
     */
    fun stopPlayback() {
        player?.let { p -> runCatching { p.pause() } }
        if (!serviceStarted) return
        serviceStarted = false
        val ctx = appContext ?: return
        runCatching { ctx.stopService(Intent(ctx, PlaybackService::class.java)) }
    }

    /** 服务已销毁的回调：复位状态，使下次播放能重新拉起。 */
    internal fun onServiceStopped() {
        serviceStarted = false
    }
}
