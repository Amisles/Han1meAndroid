package app.amisles.hanime.data.download

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import app.amisles.hanime.domain.model.DownloadGateReason
import app.amisles.hanime.domain.model.DownloadGateState
import java.util.Calendar

/** 夜间闲时的时间窗（本地时区，左闭右开）。 */
internal const val NIGHT_START_HOUR = 0
internal const val NIGHT_END_HOUR = 6

/**
 * 下载调度闸门。
 *
 * 只在「启动下一个任务」这一处判定：已经跑起来的任务不会被中途掐断，
 * 否则用户在移动网络与 Wi-Fi 之间切换会让任务反复重启。
 */
internal object DownloadGate {

    fun evaluate(
        context: Context,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        nightOnly: Boolean,
        nowHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): DownloadGateState {
        val reasons = mutableListOf<DownloadGateReason>()
        if (wifiOnly && getCurrentNetworkClass(context) != NetworkClass.WIFI) {
            reasons += DownloadGateReason.NEED_WIFI
        }
        if (chargingOnly && !isCharging(context)) {
            reasons += DownloadGateReason.NEED_CHARGING
        }
        if (nightOnly && !isNightHour(nowHour)) {
            reasons += DownloadGateReason.NEED_NIGHT
        }
        return if (reasons.isEmpty()) DownloadGateState.OPEN else DownloadGateState(reasons)
    }

    fun isNightHour(hour: Int): Boolean = hour in NIGHT_START_HOUR until NIGHT_END_HOUR

    /**
     * 距离下一个夜间窗口边界的毫秒数（窗口内指向当日 [NIGHT_END_HOUR]，窗口外指向次日
     * [NIGHT_START_HOUR]）。
     *
     * 只计算「下一次切换」而不是每天固定闹钟：调用方到点后重新调用，设备休眠造成的延迟唤醒会
     * 自然落到边界之后，不会一天天累积漂移。
     */
    fun msUntilNextNightBoundary(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (isNightHour(cal.get(Calendar.HOUR_OF_DAY))) {
            cal.set(Calendar.HOUR_OF_DAY, NIGHT_END_HOUR)
        } else {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, NIGHT_START_HOUR)
        }
        // 至少 1 秒：恰好落在边界上时不能让调用方空转
        return (cal.timeInMillis - now).coerceAtLeast(1_000L)
    }

    /** 读粘性广播的充电状态；读不到一律视为未充电（宁可等待，也不误用流量/电量）。 */
    private fun isCharging(context: Context): Boolean {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
}
