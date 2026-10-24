package app.amisles.hanime.domain.model

/** 下载被阻塞的原因。多个条件可同时不满足。 */
enum class DownloadGateReason {
    NEED_WIFI,
    NEED_CHARGING,
    NEED_NIGHT
}

/**
 * 下载调度的闸门状态。
 *
 * 只表达「现在能不能下载」，不含任何时间戳或计数：调用方每次读取都重新求值，
 * 避免把过期结论当成事实。
 */
data class DownloadGateState(val blockedReasons: List<DownloadGateReason> = emptyList()) {
    val allowed: Boolean get() = blockedReasons.isEmpty()

    /** 是否因为闸门而阻塞（区别于并发已满导致的等待）。 */
    val blocked: Boolean get() = blockedReasons.isNotEmpty()

    companion object {
        val OPEN = DownloadGateState()
    }
}
