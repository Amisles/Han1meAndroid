package app.amisles.hanime.domain.remote

/**
 * 视频直链的防盗链 Referer 常量。
 *
 * 只有 [HEADER_NAME] 落在 domain：它是 HTTP 协议里写死的头名，播放器（feature-detail）
 * 与下载引擎（data）都要用，放在任何一侧都会造成另一方反向依赖。
 *
 * 至于"当前该注入哪个 Referer"，取决于用户在设置页选择的站点，属于配置读取，
 * 由 [app.amisles.hanime.domain.repository.SettingsRepository.baseUrl] 派生，
 * 不在这里持有 —— 那会让 domain 依赖 data 的 Preferences 实现。
 */
object VideoAntiHotlink {
    /** 防盗链请求头名称。 */
    const val HEADER_NAME = "Referer"

    /** 把基址转成可直接用作 Referer 的形态（结尾带 "/"）。 */
    fun refererOf(baseUrl: String): String =
        if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}
