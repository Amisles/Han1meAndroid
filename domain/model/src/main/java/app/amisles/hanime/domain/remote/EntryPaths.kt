package app.amisles.hanime.domain.remote

/** 站点入口路径约定：与 HTTP 拦截器共用一份，避免内置浏览器与后台请求走到不同路径。 */
object EntryPaths {

    val DEFAULT_OFFICIAL_DOMAINS = listOf("hanime1.me", "hanimeone.me")

    /**
     * 站点真实入口路径：官方域名即根路径，镜像站的根路径是门页，内容在 /enter。
     */
    fun entryPathFor(host: String): String =
        if (DEFAULT_OFFICIAL_DOMAINS.any { host == it || host.endsWith(".$it") }) "/" else "/enter"
}
