package app.amisles.hanime.data.download

/**
 * 下载前台服务与本包之间的协议常量。
 *
 * 两侧原本各写一份字符串并靠注释提醒"手工保持一致"：改漏一处只会表现为通知不更新或取消失效，
 * 编译期毫无提示。集中到这里后只有一处定义。
 */
object DownloadServiceContract {

    const val SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"

    const val CHANNEL_ID = "download_channel"

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_PROGRESS = "extra_progress"
    const val EXTRA_STATUS = "extra_status"
    const val EXTRA_SPEED_BPS = "extra_speed_bps"
    const val EXTRA_REMAINING_MS = "extra_remaining_ms"
    const val EXTRA_FILE_PATH = "extra_file_path"

    const val ACTION_REMOVE_TASK = "app.amisles.hanime.service.action.REMOVE_TASK"
    const val ACTION_PAUSE_ALL = "app.amisles.hanime.service.action.PAUSE_ALL"
    const val ACTION_RESUME_TASK = "app.amisles.hanime.service.action.RESUME_TASK"
    const val ACTION_CANCEL_TASK = "app.amisles.hanime.service.action.CANCEL_TASK"
    const val ACTION_RETRY_FAILED = "app.amisles.hanime.service.action.RETRY_FAILED"
}
