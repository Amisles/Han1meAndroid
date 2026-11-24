package app.amisles.hanime.domain.model

/**
 * 已完成下载重命名的结果。
 *
 * 界面按分支给提示：除 SUCCESS 外都不应静默失败，否则用户会以为改名成功而文件仍是旧名。
 */
enum class DownloadRenameResult {
    SUCCESS,
    NOT_COMPLETED,
    EMPTY_NAME,
    NAME_TAKEN,
    FILE_MISSING,
    FAILED
}
