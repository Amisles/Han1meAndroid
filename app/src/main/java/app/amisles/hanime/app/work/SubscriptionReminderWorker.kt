package app.amisles.hanime.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.amisles.hanime.MainActivity
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.subscription.SubscriptionUpdateChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 订阅更新检测任务。
 *
 * 站点没有推送能力，只能定时抓订阅页比对。为降低被限流的风险：
 * 默认每日一次、带随机抖动、仅在用户开启后调度。
 */
@HiltWorker
class SubscriptionReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checker: SubscriptionUpdateChecker
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!Preferences.subscriptionReminder) return Result.success()
        return runCatching {
            val updates = checker.detectUpdates()
            if (updates.isNotEmpty()) notify(updates.size, updates.first().title)
            Result.success()
        }.getOrElse { e ->
            AppLogger.logError("SubscriptionReminder", "检测失败: ${e.message}", e)
            Result.retry()
        }
    }

    private fun notify(count: Int, firstTitle: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.subscription_reminder_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { setShowBadge(true) }
            )
        }
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle(applicationContext.getString(R.string.subscription_reminder_title))
            .setContentText(
                if (count == 1) {
                    firstTitle
                } else {
                    applicationContext.getString(R.string.subscription_reminder_body, count)
                }
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "subscription_reminder_channel"
        const val NOTIFICATION_ID = 3001
    }
}
