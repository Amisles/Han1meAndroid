package app.amisles.hanime.domain.model

/**
 * 从视频 URL 中提取数字 ID。
 *
 * 下沉到 domain 的理由：**详情页与多个解析器都需要它**，而它只是纯粹的 URL 规则
 * （取 v= 后的数字），与 Jsoup、OkHttp 等数据层设施无关。原先放在 data.parser，
 * 导致详情页为了取一个 ID 而 import 整个解析层。
 *
 * 注意对相对路径同样有效（/watch?v=123）。
 */
fun extractVideoId(url: String): String {
    val match = VIDEO_ID_REGEX.find(url)
    return match?.groupValues?.get(1) ?: ""
}

/** 视频 ID 的正则：与站点 watch?v=123 的形态保持一致。 */
private val VIDEO_ID_REGEX = Regex("v=(\\d+)")
