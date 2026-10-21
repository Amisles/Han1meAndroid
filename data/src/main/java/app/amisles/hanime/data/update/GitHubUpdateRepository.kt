package app.amisles.hanime.data.update

import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.AppUpdate
import app.amisles.hanime.domain.model.UpdateCheckResult
import app.amisles.hanime.domain.repository.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 通过 GitHub Releases API 检查更新。
 *
 * 几个刻意的设计决定：
 *
 * 1. **独立的 OkHttpClient**：不复用 NetworkService 的 client。后者挂了 HCookieJar，
 *    会把站点会话 Cookie 注入到 api.github.com 的请求里 —— 那是明确的凭据外泄。
 *    这里用不带 CookieJar、不带入口重写的裸 client。
 *
 * 2. **不带 GITHUB_TOKEN**：公开仓库的 Releases 接口无需认证（未认证限流 60 次/小时/IP），
 *    远超本应用的实际需求（启动节流 6 小时一次）。
 *
 * 3. **节流**：[checkForUpdate] 在非 force 情况下，距上次检查不足
 *    [UpdateRepository.CHECK_INTERVAL_MS] 时复用上次结果，避免频繁进出应用把限流打满。
 *
 * 4. **失败不是异常**：所有网络 / 解析错误都收敛为 [UpdateCheckResult.Failure]，
 *    调用方据此静默处理，绝不因为「检查更新失败」而打扰用户或崩溃。
 */
@Singleton
class GitHubUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UpdateRepository {

    companion object {
        /**
         * 仓库坐标。
         *
         * 必须与「关于」页展示的地址一致（`Amisles/Han1meAndroid`）：写错仓库名会让请求稳定返回
         * 404，而 404 被静默处理成「检查失败」，表现为「更新功能永久无效」且没有任何报错线索。
         */
        private const val OWNER = "Amisles"
        private const val REPO = "Han1meAndroid"

        /**
         * Releases API 地址。
         *
         * 取 100 条而非默认 30：项目迭代较密，若用户长期不更新，30 条可能全部是
         * 比当前版本旧的发行版，导致「明明有新版本却检不出来」。
         */
        private const val API_URL =
            "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=100"

        /** 一次只请求一个资源，8 秒足够；超时后静默失败，不阻塞启动。 */
        private const val TIMEOUT_SECONDS = 8L

        /** 未认证访问的用户代理；GitHub 要求必须提供。 */
        private const val USER_AGENT = "HanimeAndroid-UpdateChecker"

        private const val TAG = "UpdateChecker"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // 刻意不挂 CookieJar：见类注释第 1 点
        .build()

    /** 串行化检查：启动检查与用户手动检查可能同时发生，避免重复请求与状态互踩。 */
    private val checkMutex = Mutex()

    @Volatile
    private var lastCheckAt: Long = 0L

    @Volatile
    private var lastResult: UpdateCheckResult? = null

    private val _availableUpdate = MutableStateFlow<AppUpdate?>(null)
    override val availableUpdate: StateFlow<AppUpdate?> = _availableUpdate.asStateFlow()

    /** 当前安装版本。用 PackageManager 读，避免为取一个字符串就给 data 模块开 buildConfig。 */
    override val currentVersion: String by lazy {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "0" }
    }

    override val skippedVersion: String get() = Preferences.skippedUpdateVersion
    override val skippedVersionFlow: StateFlow<String> = Preferences.skippedUpdateVersionFlow

    override val autoCheckEnabled: Boolean get() = Preferences.autoCheckUpdateEnabled
    override val autoCheckEnabledFlow: StateFlow<Boolean> = Preferences.autoCheckUpdateEnabledFlow

    override fun setAutoCheckEnabled(enabled: Boolean) {
        Preferences.setAutoCheckUpdateEnabled(enabled)
    }

    override fun skipVersion(versionName: String) {
        val clean = versionName.trim()
        if (clean.isEmpty()) return
        Preferences.setSkippedUpdateVersion(clean)
        // 跳过即意味着当前不应再提示这个版本，立刻从可观察状态里撤下
        if (_availableUpdate.value?.versionName == clean) {
            _availableUpdate.value = null
        }
        AppLogger.log(TAG, "已跳过版本: $clean")
    }

    override fun clearSkippedVersion() {
        Preferences.setSkippedUpdateVersion("")
        AppLogger.log(TAG, "已清除「不再提示」记录")
    }

    override suspend fun checkForUpdate(
        includePrerelease: Boolean,
        force: Boolean
    ): UpdateCheckResult = checkMutex.withLock {
        val now = System.currentTimeMillis()
        // 非强制检查走节流：直接复用上次结果，不打网络。
        // 注意只复用「确定结果」—— 失败结果不该被缓存 6 小时。
        if (!force) {
            val cached = lastResult
            if (cached != null && cached !is UpdateCheckResult.Failure &&
                now - lastCheckAt < UpdateRepository.CHECK_INTERVAL_MS
            ) {
                AppLogger.log(TAG, "距上次检查不足节流间隔，复用上次结果")
                applyResult(cached)
                return@withLock cached
            }
        }

        val result = withContext(Dispatchers.IO) { fetch(includePrerelease) }
        lastCheckAt = System.currentTimeMillis()
        // 失败不写入 lastResult：让下一次启动仍会真正重试，而不是把一次临时断网
        // 固化成「6 小时内都不再检查」。
        if (result !is UpdateCheckResult.Failure) lastResult = result
        applyResult(result)
        result
    }

    /**
     * 把结果同步到 [availableUpdate]。
     *
     * 只在「确实是更新」且「用户没有跳过这个版本」时才发布非空值 ——
     * 用户点了「不再提示」后，重启应用也不应再次弹出。
     */
    private fun applyResult(result: UpdateCheckResult) {
        _availableUpdate.value = when (result) {
            is UpdateCheckResult.Available -> {
                val update = result.update
                if (update.versionName == Preferences.skippedUpdateVersion) {
                    AppLogger.log(TAG, "版本 ${update.versionName} 已被用户跳过，不再提示")
                    null
                } else {
                    update
                }
            }
            else -> null
        }
    }

    /** 真正发请求并解析。所有异常都转成 Failure，绝不向外抛。 */
    private fun fetch(includePrerelease: Boolean): UpdateCheckResult {
        val request = Request.Builder()
            .url(API_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    // 403 / 429 是未认证限流，属于「暂时查不了」，不该报成故障
                    response.code == 403 || response.code == 429 -> {
                        AppLogger.log(TAG, "GitHub 限流（HTTP ${response.code}），本次跳过检查")
                        UpdateCheckResult.Failure("检查过于频繁，请稍后再试")
                    }
                    !response.isSuccessful -> {
                        AppLogger.log(TAG, "GitHub 返回 HTTP ${response.code}")
                        UpdateCheckResult.Failure("检查更新失败（HTTP ${response.code}）")
                    }
                    else -> {
                        val body = response.body.string()
                        val latest = GitHubReleaseParser.parseLatest(
                            body, currentVersion, includePrerelease
                        )
                        if (latest == null) {
                            UpdateCheckResult.UpToDate(currentVersion)
                        } else {
                            AppLogger.log(
                                TAG,
                                "发现新版本 ${latest.versionName}（当前 $currentVersion）"
                            )
                            UpdateCheckResult.Available(latest, currentVersion)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            AppLogger.log(TAG, "检查更新失败（网络）: ${e.message}")
            UpdateCheckResult.Failure("网络不可用，请检查网络后重试", e)
        } catch (e: Exception) {
            AppLogger.logError(TAG, "检查更新失败: ${e.message}", e)
            UpdateCheckResult.Failure("检查更新失败", e)
        }
    }
}
