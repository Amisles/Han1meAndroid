package app.amisles.hanime.domain.model

/**
 * 收藏的视频（领域模型）。
 *
 * 持久化由 `:data` 的 `FavoriteVideoEntity` 负责，本类不再携带 Room 注解：
 * 领域层不应感知持久化框架，否则 `:domain:model` 无法作为纯 Kotlin 模块被复用与快速测试。
 */
data class FavoriteVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val author: String,
    val duration: String,
    val likeRate: String,
    val viewCount: String,
    val createdAt: Long,
    /** 所属分组 id；空串表示未分组。 */
    val groupId: String = "",
    /** 用户自定义标签，已去空、去重。 */
    val tags: List<String> = emptyList()
)

/** 收藏分组。 */
data class FavoriteGroup(
    val id: String,
    val name: String,
    /** 创建时间，用于稳定排序；系统分组为 0。 */
    val createdAt: Long = 0L
) {
    companion object {
        /** 「未分组」不是真实分组记录，用固定 id 表示。 */
        const val UNGROUPED_ID = ""
    }
}
