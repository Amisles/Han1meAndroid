package app.amisles.hanime.domain.repository

/** 崩溃提示的已读标记。 */
interface CrashSettings {
    /** 已提示过的崩溃记录文件名；为空表示尚未提示过任何崩溃。 */
    val acknowledgedCrashFile: String
    fun setAcknowledgedCrashFile(name: String)
}
