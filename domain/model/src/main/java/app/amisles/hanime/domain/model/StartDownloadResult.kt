package app.amisles.hanime.domain.model

/** 发起下载的结果：界面按分支决定提示，不再依赖"负数结果码"的约定。 */
sealed interface StartDownloadResult {

    /** 已新建任务，或复用既有任务并触发续传。 */
    data class Started(val taskId: Int) : StartDownloadResult

    /** 直链非法或落盘路径越界。 */
    data object InvalidUrl : StartDownloadResult

    /** 同一文件已有进行中或等待中的任务。 */
    data object AlreadyActive : StartDownloadResult

    /** 同一文件已下载完成。 */
    data object AlreadyCompleted : StartDownloadResult
}
