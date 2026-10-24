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
