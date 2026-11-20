package app.amisles.hanime.domain.result

/**
 * 会话凭据的落盘结果。
 *
 * [PERSISTED] 表示已写入磁盘，重启后仍有效；[MEMORY_ONLY] 表示只在本次进程内有效，
 * 调用方必须让用户知道重启后需要重新登录；[FAILED] 表示写入被拒绝或失败。
 */
enum class SessionSaveResult {
    PERSISTED,
    MEMORY_ONLY,
    FAILED;

    val isPersisted: Boolean get() = this == PERSISTED
}
