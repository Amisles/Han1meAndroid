package app.amisles.hanime.core.common.util

/**
 * 全局 User-Agent。
 *
 * Cloudflare 的 `cf_clearance` 与 UA 绑定：内置浏览器里完成校验、后台却用另一串 UA 请求时，
 * 校验结果不生效且不会报错，只表现为继续 403。因此两边必须引用同一个常量。
 */
object BrowserUserAgent {
    const val VALUE =
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
}
