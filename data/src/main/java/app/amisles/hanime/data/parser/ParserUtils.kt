package app.amisles.hanime.data.parser

object ParserUtils {

    // O3：删除恒等映射（key == value 的 replace 为无操作，纯属浪费），仅保留真正需要繁→简转换的项
    val traditionalToSimplified = mapOf(
        "最新上傳" to "最新上传",
        "他們在看" to "他们在看",
        "裏番" to "里番",
        "泡麵番" to "泡面番",
        "2D動畫" to "2D动画",
        "H動漫" to "H动漫",
        "播放清單" to "播放清单"
    )

    // 点赞率文本清理：去图标/标签前缀，仅留百分比
    fun cleanLikeRate(text: String): String {
        return text.replace("👍", "")
            .replace("thumb-up", "")
            .replace("thumb_up", "")
            .replace("thumb up", "")
            .trim()
    }

    fun convertToSimplified(text: String): String {
        var result = text
        for ((traditional, simplified) in traditionalToSimplified) {
            result = result.replace(traditional, simplified)
        }
        return result
    }

    private val VIDEO_ID_REGEX = Regex("v=(\\d+)")

    fun extractVideoId(url: String): String {
        val match = VIDEO_ID_REGEX.find(url)
        return match?.groupValues?.get(1) ?: ""
    }

    fun generatePlaceholderThumbnail(videoId: String): String {
        return "https://vdownload.hembed.com/image/thumbnail/${videoId}l.jpg"
    }
}
