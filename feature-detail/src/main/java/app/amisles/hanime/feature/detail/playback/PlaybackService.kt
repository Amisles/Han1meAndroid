package app.amisles.hanime.feature.detail.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.amisles.hanime.core.ui.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 播放前台服务：把页面持有的 ExoPlayer 挂到一个 [MediaSession] 上，
 * 由系统据此渲染通知栏 / 锁屏 / 蓝牙耳机控件，并托管 mediaPlayback 前台状态。
 *
 * **不持有播放器所有权。** 播放器由页面构建、由页面释放
 * （[app.amisles.hanime.feature.detail.ExoPlayerFactory] 要求构建与释放都在主线程）。
 * 本服务只借用同一个实例建会话，因此不需要也不应该调用 player.release()。
 *
 * 生命周期由 [PlaybackSessionBridge] 驱动：页面开始播放 → 拉起服务 → 建会话 → 通知出现；
 * 页面销毁 → 停服务 → 释放会话 → 通知消失。
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
        // 小图标必须显式指定：默认是 Media3 内置音符，与本应用品牌不符
        provider.setSmallIcon(R.drawable.ic_stat_playback)
        setMediaNotificationProvider(provider)
        // 暂停 / 播放结束后通知保留一段时间，避免「一暂停就闪没」；最终仍由系统回收
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务由页面 startService 拉起，此时没有 Controller 连接，onGetSession 不会被调用；
        // 若不在这里主动建会话，通知永远不会出现。
        ensureSession()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // 系统媒体组件（锁屏、蓝牙、MediaSession 浏览器）连进来时复用同一会话
        ensureSession()
        return session
    }

    /**
     * 建立会话。幂等，且在拿不到播放器时安全退出。
     *
     * 关键：拿不到播放器时不能让服务空转——Media3 对「普通 start intent 且无会话」的处理是
     * 立刻 stopSelf，若桥接层仍记着 serviceStarted=true，之后就再也拉不起来。
     * 因此这里主动停止服务，由 [PlaybackSessionBridge] 在下次 bind 时重试。
     */
    private fun ensureSession() {
        if (session != null) return
        val player = PlaybackSessionBridge.player
        if (player == null) {
            stopSelf()
            return
        }
        val sessionActivity = buildSessionActivity()
        if (sessionActivity == null) {
            stopSelf()
            return
        }
        val newSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(PlaybackSessionCallback())
            .build()
        session = newSession
        addSession(newSession)
        // 快进 / 快退按钮只对能 seek 的播放器有意义；不可 seek 时不下发，避免出现点了没反应的按钮
        if (player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            newSession.setMediaButtonPreferences(buildSeekButtons(this))
        }
        // 会话加入时播放器可能已经在播，主动刷新一次通知，避免「暂停一下才出现通知」
        triggerNotificationUpdate()
    }

    /** 处理通知栏的「快退 / 快进 15 秒」自定义按钮。 */
    private inner class PlaybackSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // 让控制器知道本会话支持这两个自定义命令，否则系统侧不会下发
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SESSION_COMMAND_SEEK_BACK)
                .add(SESSION_COMMAND_SEEK_FORWARD)
                .add(SESSION_COMMAND_STOP)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SEEK_BACK -> session.player.seekBack()
                ACTION_SEEK_FORWARD -> session.player.seekForward()
                ACTION_STOP -> {
                    // 移除播放器：暂停并撤掉通知与前台服务，页面随之回到暂停态
                    PlaybackSessionBridge.stopPlayback()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * 通知点击目标。feature 模块不能引用 app 模块的 MainActivity，
     * 因此用包级启动 Intent（等价于点桌面图标）。
     */
    private fun buildSessionActivity(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onDestroy() {
        // 只释放会话，不碰播放器：播放器归页面所有，重复释放会崩溃
        session?.let { s ->
            removeSession(s)
            runCatching { s.release() }
        }
        session = null
        // 通知桥接层：本服务已结束，下次播放可重新拉起
        PlaybackSessionBridge.onServiceStopped()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_SEEK_BACK = "app.amisles.hanime.playback.SEEK_BACK"
        const val ACTION_SEEK_FORWARD = "app.amisles.hanime.playback.SEEK_FORWARD"
        const val ACTION_STOP = "app.amisles.hanime.playback.STOP"

        val SESSION_COMMAND_SEEK_BACK = SessionCommand(ACTION_SEEK_BACK, android.os.Bundle.EMPTY)
        val SESSION_COMMAND_SEEK_FORWARD = SessionCommand(ACTION_SEEK_FORWARD, android.os.Bundle.EMPTY)
        val SESSION_COMMAND_STOP = SessionCommand(ACTION_STOP, android.os.Bundle.EMPTY)

        /**
         * 通知栏按钮：快退 15s、播放/暂停（中间槽位由系统按播放状态自动切换）、快进 15s。
         *
         * 必须显式 setSlots：默认槽位推导只认 Media3 内置的 seek-to-previous/next 图标，
         * 自定义命令若不给槽位会落到 overflow（折叠进「更多」），用户就看不到快进快退。
         */
        fun buildSeekButtons(context: android.content.Context): List<CommandButton> = listOf(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
                .setSessionCommand(SESSION_COMMAND_SEEK_BACK)
                .setSlots(CommandButton.SLOT_BACK)
                .setDisplayName(context.getString(R.string.playback_seek_back))
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
                .setSessionCommand(SESSION_COMMAND_SEEK_FORWARD)
                .setSlots(CommandButton.SLOT_FORWARD)
                .setDisplayName(context.getString(R.string.playback_seek_forward))
                .build(),
            // 移除播放器：折叠在「更多」里，避免误触把正在看的视频关掉
            CommandButton.Builder(CommandButton.ICON_STOP)
                .setSessionCommand(SESSION_COMMAND_STOP)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setDisplayName(context.getString(R.string.playback_stop))
                .build()
        )
    }
}
