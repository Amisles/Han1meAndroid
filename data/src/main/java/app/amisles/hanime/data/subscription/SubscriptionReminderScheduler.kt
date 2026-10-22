package app.amisles.hanime.data.subscription

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 订阅提醒的调度入口。
 *
 * 频率固定每日一次并带初始延迟：定时抓页面会被站点限流，
 * 且默认关闭，只有用户显式开启后才入队。
 */
object SubscriptionReminderScheduler {

    private const val WORK_NAME = "subscription_reminder"

    /** @param workerClass 由 app 模块传入，避免 data 层反向依赖具体 Worker。 */
    fun schedule(context: Context, workerClass: Class<out ListenableWorker>) {
        val request = PeriodicWorkRequest.Builder(workerClass, 1, TimeUnit.DAYS)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
