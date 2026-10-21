package app.amisles.hanime.feature.detail.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.amisles.hanime.core.ui.R

/**
 * 播放前台服务。
 *
 * 不持有播放器所有权：`ExoPlayer.Builder().build()` 必须在主线程、由页面负责释放，
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_notification_channel)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        setMediaNotificationProvider(provider)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务由页面用 startService 拉起，没有 Controller 连接时 onGetSession 不会触发，
        // 因此在这里主动建会话；否则通知永远不会出现。
        ensureSession()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        ensureSession()
        return session
    }

    private fun ensureSession() {
        if (session != null) return
        val player = PlaybackSessionBridge.player ?: return
        // 用桌面启动 Intent 作为通知点击目标：feature 模块不能引用 app 模块的 MainActivity
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val newSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        session = newSession
        addSession(newSession)
        // 会话加入时播放器可能已经在播，主动触发一次通知刷新，避免「暂停一下才出现通知」
        triggerNotificationUpdate()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务里划掉应用：仍在播放就保留通知，否则直接退出
        if (!PlaybackSessionBridge.isPlaying()) stopSelf()
    }

    override fun onDestroy() {
        // 播放器由页面释放，这里只释放会话；顺序颠倒或重复释放都不应导致崩溃
        runCatching { session?.release() }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001
    }
}
