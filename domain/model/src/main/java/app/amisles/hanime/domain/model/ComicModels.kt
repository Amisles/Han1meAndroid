package app.amisles.hanime.domain.model

/** 漫画条目。[id] 取自 `/comic/{id}` 路径，用于去重与后续详情跳转。 */
data class ComicItem(
    val id: String,
    val title: String,
    val coverUrl: String,
    val detailUrl: String
)

/** 漫画首页的一个分区（如「發燒漫畫」「最新上傳」）。 */
data class ComicSection(
    val title: String,
    val items: List<ComicItem>
)

/** 漫画首页解析结果：分区列表 + 分页信息。 */
data class ComicPage(
    val sections: List<ComicSection>,
    val currentPage: Int,
    val totalPages: Int,
    val hasNextPage: Boolean
) {
    val isEmpty: Boolean get() = sections.all { it.items.isEmpty() }
}
