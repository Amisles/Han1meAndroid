package app.amisles.hanime.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadGateState
import app.amisles.hanime.domain.repository.DownloadSettings
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 下载调度闸门：条件求值、条件变化触发源与「因断网自动暂停」的任务集合。
 *
 * 闸门只在「启动下一个任务」时判定：已经跑起来的任务不会被中途掐断，
 * 否则用户在移动网络与 Wi-Fi 之间切换会让任务反复重启。
 */
internal class DownloadGateController(
    private val context: Context,
    private val settings: DownloadSettings,
    private val scope: CoroutineScope
) {

    private val appContext = context.applicationContext ?: context

    private val _state = MutableStateFlow(DownloadGateState.OPEN)
    val state: StateFlow<DownloadGateState> = _state.asStateFlow()

    private var onOpen: (() -> Unit)? = null
    private var onNetworkLost: (() -> Unit)? = null
    private var onNetworkAvailable: (() -> Unit)? = null

    /**
     * 因断网而被自动暂停的任务 id。只有这些任务才会在网络恢复后自动续传 ——
     * 用户手动点暂停的任务必须保持暂停，否则「暂停省个流量」的意图会被下一次网络切换打断。
     */
    private val autoPaused = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var networkCallbackRegistered = false

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null

    @Volatile
    private var gateReceiverRegistered = false

    /** 持有引用：动态注册的接收器由系统持有，这里再存一份避免被优化掉。 */
    private var gateReceiver: BroadcastReceiver? = null

    /** 重新求值并广播；返回本次结论。 */
    fun evaluate(): DownloadGateState {
        val evaluated = DownloadGate.evaluate(
            appContext,
            settings.downloadWifiOnly,
            settings.downloadChargingOnly,
            settings.downloadNightOnly
        )
        _state.value = evaluated
        return evaluated
    }

    fun reevaluate() {
        if (evaluate().allowed) onOpen?.invoke()
    }

    fun markAutoPaused(taskId: Int) {
        autoPaused.add(taskId)
    }

    fun forgetAutoPaused(taskId: Int) {
        autoPaused.remove(taskId)
    }

    fun isAutoPaused(taskId: Int): Boolean = taskId in autoPaused

    fun clearAutoPaused() {
        autoPaused.clear()
    }

    fun start(
        onOpen: () -> Unit,
        onNetworkLost: () -> Unit,
        onNetworkAvailable: () -> Unit
    ) {
        this.onOpen = onOpen
        this.onNetworkLost = onNetworkLost
        this.onNetworkAvailable = onNetworkAvailable

        scope.launch {
            combine(
                settings.downloadWifiOnlyFlow,
                settings.downloadChargingOnlyFlow,
                settings.downloadNightOnlyFlow
            ) { wifi, charging, night -> Triple(wifi, charging, night) }
                .collect {
                    if (evaluate().allowed) onOpen()
                }
        }
        registerNetworkObserver()
        registerGateObservers()
    }

    /**
     * 网络变化监听：断网时把在途任务转为 PAUSED，恢复后自动续传。
     *
     * 原实现没有任何网络感知：断网后分块路径的块会被无限重新入队（在 30 秒零进度
     * 看门狗下反复全量重建连接），单连接路径要等 readTimeout=120s 才判失败；期间通知
     * 一直显示「下载中」，实际没有任何数据流动，纯耗电。
     */
    private fun registerNetworkObserver() {
        if (networkCallbackRegistered) return
        val cm = runCatching {
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull() ?: run {
            AppLogger.log("DownloadManager", "无法获取 ConnectivityManager，跳过网络监听")
            return
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                // 只在真的可用时才触发；onCapabilitiesChanged 会更晚且频繁，onAvailable 足够
                scope.launch { onNetworkAvailable?.invoke() }
            }

            override fun onLost(network: android.net.Network) {
                scope.launch { onNetworkLost?.invoke() }
            }
        }
        networkCallbackRegistered = runCatching {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
            connectivityManager = cm
            true
        }.getOrDefault(false)
        if (!networkCallbackRegistered) {
            AppLogger.log("DownloadManager", "注册网络监听失败，将依赖下载自身的重试与超时")
        }
    }

    /**
     * 闸门的「条件变化」触发源：充电插拔广播与夜间窗口边界。
     *
     * Wi-Fi 条件不在这里处理：网络恢复已由 [registerNetworkObserver] 的 onAvailable 触发。
     */
    private fun registerGateObservers() {
        if (gateReceiverRegistered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                AppLogger.log("DownloadManager", "电源状态变化，重新求值调度闸门: " + intent?.action)
                reevaluate()
            }
        }
        gateReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            true
        }.getOrDefault(false)
        if (gateReceiverRegistered) {
            gateReceiver = receiver
        } else {
            AppLogger.log("DownloadManager", "注册电源状态监听失败，闸门仍只在任务启动时求值")
        }

        // 夜间窗口边界：只做「下一次切换」，到点后重新计算，无需每天固定闹钟；
        // 设备休眠导致延迟唤醒时会自然落到边界之后，不会累积漂移。
        scope.launch {
            while (true) {
                val waitMs = DownloadGate.msUntilNextNightBoundary()
                delay(waitMs)
                if (settings.downloadNightOnly) {
                    AppLogger.log("DownloadManager", "夜间时段边界切换，重新求值调度闸门")
                    reevaluate()
                }
            }
        }
    }

    fun shutdown() {
        // 动态注册的接收器必须显式注销，否则单例被重建时会累积监听
        gateReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        gateReceiver = null
        gateReceiverRegistered = false
        networkCallback?.let { callback ->
            connectivityManager?.let { cm -> runCatching { cm.unregisterNetworkCallback(callback) } }
        }
        networkCallback = null
        connectivityManager = null
        networkCallbackRegistered = false
        onOpen = null
        onNetworkLost = null
        onNetworkAvailable = null
    }
}
