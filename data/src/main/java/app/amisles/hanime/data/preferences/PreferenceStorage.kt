package app.amisles.hanime.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.amisles.hanime.core.common.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 偏好文件的唯一入口：热存储（普通 SharedPreferences）、会话存储（加密）与一次性迁移。
 *
 * 设置与会话各自持有 StateFlow 与业务规则，本类只负责读写原语和存储可用性，
 * 不再认识语言、下载、播放器等任何具体设置。
 */
@Singleton
class PreferenceStorage @Inject constructor(
    @ApplicationContext context: Context
) {

    private val appContext = context.applicationContext ?: context
    private val preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secureSession = SecureSession()
    private val sessionReadyListeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var sessionReadyNotified = false

    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed.asStateFlow()

    val prefs: SharedPreferences

    init {
        val hot = appContext.getSharedPreferences(PreferenceKeys.HOT_NAME, Context.MODE_PRIVATE)
        if (!runCatching { hot.getBoolean(PreferenceKeys.HOT_SPLIT_DONE, false) }.getOrDefault(false)) {
            migrateHotKeys(hot)
        }
        prefs = SplitPreferences(hot, secureSession, PreferenceKeys.SECURE_KEYS)
        secureSession.onDemandOpen = { preferencesScope.launch { openSecureStore() } }
        if (secureSession.isReady()) {
            notifySessionReady()
        } else if (!secureSession.hasFailed() && sessionFile().exists()) {
            preferencesScope.launch { openSecureStore() }
        }
    }

    fun readString(key: String, default: String?): String? =
        runCatching { prefs.getString(key, default) }.getOrDefault(default)

    fun readBoolean(key: String, default: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)

    fun readInt(key: String, default: Int): Int =
        runCatching { prefs.getInt(key, default) }.getOrDefault(default)

    fun readFloat(key: String, default: Float): Float =
        runCatching { prefs.getFloat(key, default) }.getOrDefault(default)

    /**
     * 异步提交；用于可容忍进程在写盘前被杀的普通设置。
     *
     * apply 只保证内存态生效，磁盘失败不可见；这里在同一写线程补一次 commit 校验，
     * 让设置页能区分「已保存」和「仅当前进程有效」。
     */
    fun write(block: SharedPreferences.Editor.() -> Unit) {
        runCatching {
            val editor = prefs.edit()
            editor.block()
            editor.apply()
        }.onFailure { e ->
            _writeFailed.value = true
            AppLogger.logError("Preferences", "偏好写入失败: ${e.message}", e)
        }
        preferencesScope.launch {
            // 只校验热存储：SplitPreferences 的 commit 会连带空写加密存储，未就绪时会把正常设置误判为失败。
            val hot = appContext.getSharedPreferences(PreferenceKeys.HOT_NAME, Context.MODE_PRIVATE)
            val committed = runCatching { hot.edit().commit() }.getOrDefault(false)
            _writeFailed.value = !committed
        }
    }

    /** 同步提交并返回真实结果；会话凭据与备份恢复必须走这里。 */
    fun writeBlocking(block: SharedPreferences.Editor.() -> Unit): Boolean = runCatching {
        val editor = prefs.edit()
        editor.block()
        editor.commit()
    }.getOrElse { e ->
        AppLogger.logError("Preferences", "偏好同步写入失败: ${e.message}", e)
        false
    }

    fun isSessionReady(): Boolean = secureSession.isReady()

    fun hasSessionFailed(): Boolean = secureSession.hasFailed()

    /** 会话存储就绪（或确认失败）后回调一次；已就绪时立即执行。 */
    fun onSessionReady(listener: () -> Unit) {
        if (secureSession.isReady() || secureSession.hasFailed()) {
            listener()
            return
        }
        sessionReadyListeners.add(listener)
    }

    private fun notifySessionReady() {
        if (sessionReadyNotified) return
        sessionReadyNotified = true
        _sessionReady.value = true
        val listeners = sessionReadyListeners.toList()
        sessionReadyListeners.clear()
        listeners.forEach { runCatching { it() } }
    }

    @Synchronized
    private fun openSecureStore() {
        if (secureSession.isReady()) {
            notifySessionReady()
            return
        }
        val opened = runCatching { openSecure() }.getOrNull()
        secureSession.attach(opened)
        if (opened != null) notifySessionReady() else if (secureSession.hasFailed()) {
            // 失败也要让会话层停止等待，避免界面一直停留在「加载中」
            notifySessionReady()
        }
    }

    private fun sessionFile(): File =
        File(appContext.filesDir.parentFile, "shared_prefs/${PreferenceKeys.SECURE_NAME}.xml")

    /**
     * 把加密文件里的非会话键搬进热存储，只在分层存储启用后的第一次启动执行。
     *
     * 这一次必须同步打开加密存储：迁移前热存储是空的，语言与主题一旦用默认值，首帧就是错的。
     */
    private fun migrateHotKeys(hot: SharedPreferences) {
        if (!sessionFile().exists()) {
            // 全新安装没有历史数据可搬，直接落标记，省掉一次 Keystore 解密
            runCatching {
                val editor = hot.edit()
                editor.putBoolean(PreferenceKeys.HOT_SPLIT_DONE, true)
                editor.commit()
            }
            return
        }
        val secure = runCatching { openSecure() }.getOrNull()
        if (secure == null) {
            secureSession.attach(null)
            return
        }
        secureSession.attach(secure)
        val carried = runCatching { secure.all }.getOrDefault(emptyMap())
            .filterKeys {
                it !in PreferenceKeys.SECURE_KEYS && it != PreferenceKeys.MIGRATED_TO_ENCRYPTED
            }
        runCatching {
            val hotEditor = hot.edit()
            carried.forEach { (key, value) -> putValue(hotEditor, key, value) }
            hotEditor.putBoolean(PreferenceKeys.HOT_SPLIT_DONE, true)
            hotEditor.commit()
            val secureEditor = secure.edit()
            carried.keys.forEach { secureEditor.remove(it) }
            secureEditor.commit()
        }.onFailure { e ->
            AppLogger.logError("Preferences", "偏好分层迁移失败: ${e.message}", e)
        }
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    /**
     * 打开会话凭据所在的加密存储。
     *
     * 首次从明文旧文件迁移：读取旧值 → 删除旧文件 → 写入加密文件，避免明文会话残留。
     * 换机恢复后旧密文无法解密时清空重建，而不是把加密文件当明文读写。
     *
     * 局部抑制 DEPRECATION：EncryptedSharedPreferences 与 MasterKey 在
     * security-crypto 1.1.0 被标记弃用，但 AndroidX 没有给出替代 API。
     */
    @Suppress("DEPRECATION")
    private fun openSecure(): SharedPreferences? {
        val masterKey = runCatching {
            MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }.getOrNull() ?: return null

        val encrypted = createEncrypted(masterKey)
        val legacyFile = sessionFile()
        val alreadyMigrated = runCatching {
            encrypted?.getBoolean(PreferenceKeys.MIGRATED_TO_ENCRYPTED, false) == true
        }.getOrDefault(false)
        if (encrypted != null && !alreadyMigrated &&
            legacyFile.exists() && isLegacyPlaintextPrefs()
        ) {
            val legacy = appContext.getSharedPreferences(PreferenceKeys.SECURE_NAME, Context.MODE_PRIVATE)
            val alreadyLogin = legacy.getBoolean(PreferenceKeys.ALREADY_LOGIN, false)
            val loginCookie = legacy.getString(PreferenceKeys.LOGIN_COOKIE, "").orEmpty()
            val cfCookie = legacy.getString(PreferenceKeys.CF_COOKIE, "").orEmpty()
            val savedUserId = legacy.getString(PreferenceKeys.SAVED_USER_ID, "").orEmpty()
            val savedUserName = legacy.getString(PreferenceKeys.SAVED_USER_NAME, "").orEmpty()
            val maxConcurrent = legacy.getInt(PreferenceKeys.MAX_DOWNLOAD_CONCURRENT, 3)
            val baseUrl = legacy.getString(PreferenceKeys.BASE_URL, "").orEmpty()
            val appLang = legacy.getString(PreferenceKeys.APP_LANGUAGE, "").orEmpty()
            val themeMode = legacy.getString(PreferenceKeys.THEME_MODE, "").orEmpty()
            appContext.deleteSharedPreferences(PreferenceKeys.SECURE_NAME)
            // 删除明文文件后需要重建：旧实例仍指向已删除的文件，复用会读到空数据
            val enc = createEncrypted(masterKey) ?: return null
            val editor = enc.edit()
            editor.putBoolean(PreferenceKeys.ALREADY_LOGIN, alreadyLogin)
            editor.putString(PreferenceKeys.LOGIN_COOKIE, loginCookie)
            editor.putString(
                PreferenceKeys.LOGIN_COOKIE_DOMAIN,
                runCatching { android.net.Uri.parse(baseUrl).host?.lowercase() }.getOrNull() ?: ""
            )
            editor.putString(PreferenceKeys.CF_COOKIE, cfCookie)
            editor.putString(PreferenceKeys.SAVED_USER_ID, savedUserId)
            editor.putString(PreferenceKeys.SAVED_USER_NAME, savedUserName)
            editor.putInt(PreferenceKeys.MAX_DOWNLOAD_CONCURRENT, maxConcurrent)
            editor.putString(PreferenceKeys.BASE_URL, baseUrl)
            editor.putString(PreferenceKeys.APP_LANGUAGE, appLang)
            editor.putString(PreferenceKeys.THEME_MODE, themeMode)
            editor.commit()
            AppLogger.log("Preferences", "已将明文偏好迁移至 EncryptedSharedPreferences")
            val mark = enc.edit()
            mark.putBoolean(PreferenceKeys.MIGRATED_TO_ENCRYPTED, true)
            mark.commit()
            return enc
        }

        encrypted?.let { enc ->
            if (!alreadyMigrated) {
                runCatching {
                    val editor = enc.edit()
                    editor.putBoolean(PreferenceKeys.MIGRATED_TO_ENCRYPTED, true)
                    editor.commit()
                }
            }
            return enc
        }

        // 创建失败有两种截然不同的原因，必须区分：
        // 1) 换机恢复后 Keystore 主密钥不匹配 —— 旧密文确实读不出来，清空重建是正确恢复；
        // 2) 传入的 Context 尚不可用 —— 数据本身完好，清空重建会抹掉用户的全部配置。
        val prefsAccessible = runCatching {
            appContext.getSharedPreferences(PreferenceKeys.SECURE_NAME, Context.MODE_PRIVATE)
            true
        }.getOrDefault(false)
        if (!prefsAccessible) {
            AppLogger.logError(
                "Preferences",
                "加密存储创建失败且无法访问偏好文件（多为 Context 尚未就绪），为避免丢失配置，本次不清空重建"
            )
            return null
        }

        AppLogger.logError(
            "Preferences",
            "加密存储创建失败（常见于换机恢复后 Keystore 主密钥不匹配），清空旧偏好后重建，需重新登录"
        )
        appContext.deleteSharedPreferences(PreferenceKeys.SECURE_NAME)
        return createEncrypted(masterKey)
    }

    @Suppress("DEPRECATION")
    private fun createEncrypted(masterKey: MasterKey): SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            appContext,
            PreferenceKeys.SECURE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    private fun isLegacyPlaintextPrefs(): Boolean {
        val legacy = appContext.getSharedPreferences(PreferenceKeys.SECURE_NAME, Context.MODE_PRIVATE)
        return legacy.contains(PreferenceKeys.APP_LANGUAGE) ||
            legacy.contains(PreferenceKeys.THEME_MODE) ||
            legacy.contains(PreferenceKeys.ALREADY_LOGIN) ||
            legacy.contains(PreferenceKeys.BASE_URL)
    }
}
