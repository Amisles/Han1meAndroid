package app.amisles.hanime.data.parser

/**
 * 解析器公共工具函数
 */
object ParserUtils {

    // 繁简体转换映射表
    val traditionalToSimplified = mapOf(
        "最新上市" to "最新上市",
        "最新上傳" to "最新上传",
        "他們在看" to "他们在看",
        "裏番" to "里番",
        "泡麵番" to "泡面番",
        "Motion Anime" to "Motion Anime",
        "3DCG" to "3DCG",
        "2.5D" to "2.5D",
        "2D動畫" to "2D动画",
        "AI生成" to "AI生成",
        "MMD" to "MMD",
        "Cosplay" to "Cosplay",
        "H動漫" to "H动漫",
        "影片" to "影片",
        "播放清單" to "播放清单"
    )

    // 清理点赞率文本，移除图标和标签前缀，只保留数字百分比
    fun cleanLikeRate(text: String): String {
        return text.replace("👍", "")
            .replace("thumb-up", "")
            .replace("thumb_up", "")
            .replace("thumb up", "")
            .trim()
    }

    // 繁体转简体
    fun convertToSimplified(text: String): String {
        var result = text
        for ((traditional, simplified) in traditionalToSimplified) {
            result = result.replace(traditional, simplified)
        }
        return result
    }

    // 从URL中提取视频ID
    fun extractVideoId(url: String): String {
        val regex = Regex("v=(\\d+)")
        val match = regex.find(url)
        return match?.groupValues?.get(1) ?: ""
    }

    // 生成占位缩略图URL
    fun generatePlaceholderThumbnail(videoId: String): String {
        return "https://vdownload.hembed.com/image/thumbnail/${videoId}l.jpg"
    }
}
