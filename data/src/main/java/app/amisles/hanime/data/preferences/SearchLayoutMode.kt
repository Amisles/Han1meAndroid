package app.amisles.hanime.data.preferences

/**
 * 搜索页结果区的布局模式。
 *
 * 与 [ThemeMode] 不同，这里额外保留「未选择」语义：用户从未点过切换时，界面按窗口宽度档位
 * 取默认值（手机列表 / 平板网格），从而不改变既有体验；一旦显式选择过，此后一律以用户选择为准。
 */
enum class SearchLayoutMode {
    LIST,
    GRID;

    companion object {
        /** 解析持久化的模式名；无法识别（含从未保存）时返回 null，表示「用户未选择」。 */
        fun fromNameOrNull(name: String?): SearchLayoutMode? =
            name?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
