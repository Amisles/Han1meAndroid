package app.amisles.hanime.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** PIN 应用锁：开启后冷启动与每次从后台回到前台都要先输入 4 位 PIN。 */
interface AppLockSettings {

    val appLockEnabledFlow: StateFlow<Boolean>

    /**
     * 写入新 PIN 并开启应用锁。
     *
     * @return 写入是否落到加密存储；false 时调用方不得认为应用锁已生效。
     */
    suspend fun setPin(pin: String): Boolean

    fun disableAppLock()

    suspend fun verifyPin(pin: String): AppLockResult
}

/** PIN 校验结果：区分「PIN 不对」与「校验值读不到」，否则加密存储失效时用户会被永久挡在锁屏外。 */
enum class AppLockResult {
    UNLOCKED,
    MISMATCH,
    UNAVAILABLE
}
