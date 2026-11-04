package app.amisles.hanime.domain.preferences

/**
 * 详情页相关推荐的布局模式。
 *
 * 竖版封面（里番 / 泡面番）恒为网格，不受该设置影响；其余类型未选择时按卡片形态取默认
 * （横版列表），一旦显式选择过便固定为用户选择。
 */
enum class RelatedLayoutMode {
    LIST,
    GRID;

    companion object {
        fun fromNameOrNull(name: String?): RelatedLayoutMode? =
            name?.let { runCatching { valueOf(it) }.getOrNull() }

        fun usesGrid(choice: RelatedLayoutMode?, hasPortraitCards: Boolean): Boolean =
            hasPortraitCards || choice == GRID
    }
}
