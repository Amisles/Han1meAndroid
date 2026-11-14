package app.amisles.hanime.data.preferences

import android.content.SharedPreferences

/**
 * 把偏好键分流到热存储（普通 SharedPreferences）与会话存储（加密）两个文件。
 *
 * 加密存储创建要经 Android Keystore，落在首帧关键路径上会让冷启动多等数十毫秒，而首帧真正
 * 需要的只有语言、主题这类非敏感项。会话键在 [SecureSession] 就绪前读出默认值，就绪后由
 * [Preferences] 回填；就绪前的写入先排队，随存储打开一并落盘。
 */
internal class SplitPreferences(
    private val hot: SharedPreferences,
    private val secure: SecureSession,
    private val sessionKeys: Set<String>
) : SharedPreferences {

    private fun isSession(key: String): Boolean = key in sessionKeys

    override fun getAll(): Map<String, *> = hot.all + secure.all()

    override fun getString(key: String, defValue: String?): String? =
        if (isSession(key)) secure.getString(key, defValue) else hot.getString(key, defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        if (isSession(key)) secure.getStringSet(key, defValues) else hot.getStringSet(key, defValues)

    override fun getInt(key: String, defValue: Int): Int =
        if (isSession(key)) secure.getInt(key, defValue) else hot.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long): Long =
        if (isSession(key)) secure.getLong(key, defValue) else hot.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float =
        if (isSession(key)) secure.getFloat(key, defValue) else hot.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (isSession(key)) secure.getBoolean(key, defValue) else hot.getBoolean(key, defValue)

    override fun contains(key: String): Boolean =
        if (isSession(key)) secure.contains(key) else hot.contains(key)

    override fun edit(): SharedPreferences.Editor = SplitEditor(this, hot.edit(), secure.edit())

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = hot.registerOnSharedPreferenceChangeListener(listener)

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = hot.unregisterOnSharedPreferenceChangeListener(listener)

    internal fun isSessionKey(key: String): Boolean = isSession(key)
}

private class SplitEditor(
    private val owner: SplitPreferences,
    private val hot: SharedPreferences.Editor,
    private val secure: SharedPreferences.Editor
) : SharedPreferences.Editor {

    private fun target(key: String): SharedPreferences.Editor =
        if (owner.isSessionKey(key)) secure else hot

    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        target(key).putString(key, value)
        return this
    }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
        target(key).putStringSet(key, values)
        return this
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        target(key).putInt(key, value)
        return this
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        target(key).putLong(key, value)
        return this
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        target(key).putFloat(key, value)
        return this
    }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        target(key).putBoolean(key, value)
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        target(key).remove(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        hot.clear()
        secure.clear()
        return this
    }

    override fun commit(): Boolean {
        val hotCommitted = hot.commit()
        val secureCommitted = secure.commit()
        return hotCommitted && secureCommitted
    }

    override fun apply() {
        hot.apply()
        secure.apply()
    }
}

/**
 * 会话存储的句柄：加密存储由后台线程打开，本类负责在打开前后提供一致的读写语义。
 *
 * 打开前的写入按顺序排队，打开后一次性补写；[written] 用于让回填逻辑识别「本次进程已经写过
 * 新会话」，避免刚登录就被磁盘上的旧值覆盖。打开失败后不再重试，凭据只留在内存里。
 */
internal class SecureSession {

    private val lock = Any()
    private val pending = mutableListOf<(SharedPreferences.Editor) -> Unit>()

    @Volatile
    private var store: SharedPreferences? = null

    @Volatile
    private var failed = false

    @Volatile
    var written: Boolean = false
        private set

    /** 首次写入会话时的补开入口：未登录的设备启动时不会碰 Keystore，登录后才需要它。 */
    @Volatile
    var onDemandOpen: (() -> Unit)? = null

    fun isReady(): Boolean = store != null

    fun hasFailed(): Boolean = failed

    fun all(): Map<String, *> = store?.all ?: emptyMap<String, Any?>()

    fun getString(key: String, defValue: String?): String? = store?.getString(key, defValue) ?: defValue

    fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        store?.getStringSet(key, defValues) ?: defValues

    fun getInt(key: String, defValue: Int): Int = store?.getInt(key, defValue) ?: defValue

    fun getLong(key: String, defValue: Long): Long = store?.getLong(key, defValue) ?: defValue

    fun getFloat(key: String, defValue: Float): Float = store?.getFloat(key, defValue) ?: defValue

    fun getBoolean(key: String, defValue: Boolean): Boolean = store?.getBoolean(key, defValue) ?: defValue

    fun contains(key: String): Boolean = store?.contains(key) ?: false

    fun edit(): SharedPreferences.Editor = DeferredEditor(this)

    fun submit(ops: List<(SharedPreferences.Editor) -> Unit>, commit: Boolean) {
        written = true
        val target = synchronized(lock) {
            val current = store
            if (current == null) pending += ops
            current
        }
        if (target == null) {
            if (!failed) onDemandOpen?.invoke()
            return
        }
        replay(target, ops, commit)
    }

    fun attach(opened: SharedPreferences?) {
        val queued: List<(SharedPreferences.Editor) -> Unit>
        synchronized(lock) {
            if (store != null) return
            store = opened
            failed = opened == null
            queued = pending.toList()
            pending.clear()
        }
        if (opened != null && queued.isNotEmpty()) replay(opened, queued, commit = true)
    }

    private fun replay(
        target: SharedPreferences,
        ops: List<(SharedPreferences.Editor) -> Unit>,
        commit: Boolean
    ) {
        val editor = target.edit()
        ops.forEach { it(editor) }
        if (commit) editor.commit() else editor.apply()
    }
}

private class DeferredEditor(private val session: SecureSession) : SharedPreferences.Editor {

    private val ops = mutableListOf<(SharedPreferences.Editor) -> Unit>()

    private fun record(op: (SharedPreferences.Editor) -> Unit): SharedPreferences.Editor {
        ops += op
        return this
    }

    override fun putString(key: String, value: String?): SharedPreferences.Editor =
        record { it.putString(key, value) }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
        record { it.putStringSet(key, values) }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor =
        record { it.putInt(key, value) }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor =
        record { it.putLong(key, value) }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
        record { it.putFloat(key, value) }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
        record { it.putBoolean(key, value) }

    override fun remove(key: String): SharedPreferences.Editor = record { it.remove(key) }

    override fun clear(): SharedPreferences.Editor = record { it.clear() }

    override fun commit(): Boolean {
        session.submit(ops, commit = true)
        return true
    }

    override fun apply() {
        session.submit(ops, commit = false)
    }
}
