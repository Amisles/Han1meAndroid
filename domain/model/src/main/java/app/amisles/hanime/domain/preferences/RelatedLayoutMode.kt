package app.amisles.hanime.domain.preferences

/**
 * 详情页相关推荐的布局模式。
 *
 * 未选择时由推荐卡片的形态决定默认值（竖版封面用网格、横版用列表），
 * 一旦显式选择过便固定为用户选择。
 */
enum class RelatedLayoutMode {
    LIST,
    GRID;

    companion object {
        fun fromNameOrNull(name: String?): RelatedLayoutMode? =
            name?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
