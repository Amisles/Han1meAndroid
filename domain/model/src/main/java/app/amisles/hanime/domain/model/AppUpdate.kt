package app.amisles.hanime.domain.model

/**
 * 一个可用的应用更新。
 *
 * 位于 domain：更新页（feature-settings）与启动弹窗（app）都只负责展示，
 * 不应为了取「版本号 / 变更说明 / 下载地址」而依赖 `:data` 的具体网络实现。
 */
data class AppUpdate(
    /** 发行版标签，例如 `v1.6.0`；未必带 `v` 前缀，展示时按原样保留。 */
    val tagName: String,
    /** 去掉前缀后的规范版本，例如 `1.6.0`。用于比较与「跳过此版本」的存储键。 */
    val versionName: String,
    /** 发行版标题；发布时未填则回退为 [tagName]。 */
    val title: String,
    /** 变更说明（Markdown 原文）。GitHub 的 body 是 Markdown，界面按纯文本展示即可。 */
    val releaseNotes: String,
    /** 发行版页面地址，供「在浏览器中打开」。 */
    val htmlUrl: String,
    /** 是否是预发布版（prerelease）。默认不提示预发布版。 */
    val isPrerelease: Boolean = false,
    /** 发布时间（ISO 8601 原文），仅供展示，不参与比较。 */
    val publishedAt: String = "",
    /** 安装包直链（取自该发行版的 .apk 资产）；为空表示需要跳转浏览器手动下载。 */
    val apkUrl: String = "",
    /** 安装包大小（字节）；0 表示未知。 */
    val apkSizeBytes: Long = 0L
) {
    /** 是否拿得到可下载的安装包。 */
    val hasApk: Boolean get() = apkUrl.isNotBlank()
}

/**
 * 更新检查的结果。
 *
 * 用密封类而非「返回 null 表示已是最新」：检查失败（网络 / 限流 / 解析异常）与
 * 「确实没有新版本」是两种截然不同的状态，界面必须能区分 —— 前者应提示「检查失败」，
 * 后者应提示「已是最新版本」。合并成同一个 null 会让后者永远无法被正确表达。
 */
sealed class UpdateCheckResult {
    /** 已是最新版本；[currentVersion] 用于展示。 */
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()

    /** 发现新版本。 */
    data class Available(
        val update: AppUpdate,
        val currentVersion: String
    ) : UpdateCheckResult()

    /** 检查失败；[message] 已是可直接展示的文案。 */
    data class Failure(val message: String, val throwable: Throwable? = null) : UpdateCheckResult()
}
