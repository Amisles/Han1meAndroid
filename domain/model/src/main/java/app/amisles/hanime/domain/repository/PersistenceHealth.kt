package app.amisles.hanime.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** 普通设置写入的健康状态：true 表示最近一次落盘失败，仅在状态切换时通知界面。 */
interface PersistenceHealth {
    val writeFailed: StateFlow<Boolean>
}
