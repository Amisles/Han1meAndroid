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

/** 详情页里的单页缩略图；[number] 从 1 开始。 */
data class ComicPageThumb(
    val number: Int,
    val thumbnailUrl: String
)

/**
 * 漫画详情（/comic/{id}）。
 *
 * [title] 取站点 h3（通常是英文名），[originalTitle] 取 h4（原文名），两者都可能为空。
 * [pageThumbnails] 只用于详情页预览，不参与阅读。
 */
data class ComicDetail(
    val id: String,
    val title: String,
    val originalTitle: String,
    val coverUrl: String,
    val tags: List<String>,
    val artists: List<String>,
    val languages: List<String>,
    val categories: List<String>,
    val pageCount: Int,
    val uploadedAt: String,
    val pageThumbnails: List<ComicPageThumb>,
    val relatedSections: List<ComicSection>
) {
    /** 标题为空时用 id 兜底，避免界面出现空行。 */
    val displayTitle: String get() = title.ifBlank { originalTitle }.ifBlank { "#$id" }
}

/**
 * 漫画正文（/comic/{id}/{page}）。
 *
 * [pages] 下标 0 对应第 1 页。站点用 gallery 前缀 + 页码 + 每页扩展名拼出全部图片，
 * 因此请求一次即可拿到整话地址，翻页不再发请求。
 */
data class ComicChapter(
    val comicId: String,
    val pageCount: Int,
    val pages: List<String>
) {
    fun pageUrl(page: Int): String? = pages.getOrNull(page - 1)
    val isBlank: Boolean get() = pages.isEmpty()
}

/** 阅读进度。[lastPage] 从 1 开始。 */
data class ComicProgress(
    val comicId: String,
    val title: String,
    val coverUrl: String,
    val pageCount: Int,
    val lastPage: Int,
    val updatedAt: Long
) {
    /** 看完的判定与看完即重置的边界一致：最后一页视为读完。 */
    val isFinished: Boolean get() = pageCount > 0 && lastPage >= pageCount
}

/** 标签 / 作者目录（/comics/tags、/comics/artists）的条目；[count] 为站点给出的作品数文案（如 4.2k），可能为空。 */
data class ComicDirectoryEntry(
    val name: String,
    val count: String,
    val url: String
)

/** 目录种类：标签与作者共用同一套页面结构与解析逻辑。 */
enum class ComicDirectoryKind { TAG, ARTIST }

/** 标签 / 作者作品列表的排序方式，与站点路径后缀一一对应。 */
enum class ComicSort { LATEST, POPULAR_TODAY, POPULAR_WEEK, POPULAR }

/** 标签 / 作者作品列表（/tags/{name}[/{sort}]）解析结果。 */
data class ComicDirectoryResult(
    val items: List<ComicItem>,
    val currentPage: Int,
    val totalPages: Int,
    val hasNextPage: Boolean
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/** 阅读方式。 */
enum class ComicReadMode {
    /** 单页横向翻页。 */
    Paged,

    /** 竖向连续滚动。 */
    Continuous
}
