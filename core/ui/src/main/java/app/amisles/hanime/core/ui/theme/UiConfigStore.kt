package app.amisles.hanime.core.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UiConfigStore {

    private const val NAME = "hanime_ui_config"
    private const val KEY_VERSION = "schema_version"
    private const val CURRENT_VERSION = 1

    private const val K_FONT_SCALE = "font_scale"
    private const val K_ASPECT_RATIO = "aspect_ratio"
    private const val K_CARD_RADIUS = "card_radius"
    private const val K_LIST_THUMB_W = "list_thumb_w"
    private const val K_LIST_THUMB_RADIUS = "list_thumb_radius"
    private const val K_CARD_TITLE_TOP = "card_title_top"
    private const val K_CARD_META_SPACING = "card_meta_spacing"
    private const val K_LIST_ITEM_PADDING = "list_item_padding"
    private const val K_LIST_ITEM_SPACING = "list_item_spacing"
    private const val K_LIST_META_SPACING = "list_meta_spacing"
    private const val K_CARD_TITLE_FS = "card_title_fs"
    private const val K_CARD_META_FS = "card_meta_fs"
    private const val K_GRID_TITLE_FS = "grid_title_fs"
    private const val K_GRID_TITLE_LH = "grid_title_lh"
    private const val K_GRID_META_FS = "grid_meta_fs"
    private const val K_LIST_TITLE_FS = "list_title_fs"
    private const val K_LIST_META_FS = "list_meta_fs"
    private const val K_BADGE_FS = "badge_fs"
    private const val K_EMOJI_FS = "emoji_fs"
    private const val K_COLS_COMPACT = "cols_compact"
    private const val K_COLS_MEDIUM = "cols_medium"
    private const val K_COLS_EXPANDED = "cols_expanded"
    private const val K_GRID_TARGET_W = "grid_target_w"
    private const val K_GRID_GAP = "grid_gap"
    private const val K_GRID_MIN_COLS = "grid_min_cols"
    private const val K_GRID_MAX_COLS = "grid_max_cols"
    private const val K_THUMB_QUALITY = "thumb_quality"
    private const val K_SECTION_VISIBLE_COUNT = "section_visible_count"
    private const val K_SECTION_LAYOUTS = "section_layouts"
    private const val K_SECTION_ORDER = "section_order"

    @Volatile
    private var initialized = false

    private lateinit var sp: SharedPreferences

    private val _config = MutableStateFlow(UiConfig.Default)
    val config: StateFlow<UiConfig> = _config.asStateFlow()

    private val _homeSections = MutableStateFlow(HomeSectionConfig.Default)
    val homeSections: StateFlow<HomeSectionConfig> = _homeSections.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        sp = (context.applicationContext ?: context).getSharedPreferences(NAME, Context.MODE_PRIVATE)
        _config.value = runCatching { read() }.getOrElse { UiConfig.Default }
        _homeSections.value = runCatching { readHomeSections() }.getOrElse { HomeSectionConfig.Default }
        initialized = true
    }

    fun set(config: UiConfig) {
        val safe = config.sanitized()
        if (safe == _config.value) return
        if (initialized) write(safe)
        _config.value = safe
    }

    fun update(transform: (UiConfig) -> UiConfig) = set(transform(_config.value))

    fun setHomeSections(config: HomeSectionConfig) {
        val safe = config.sanitized()
        if (safe == _homeSections.value) return
        if (initialized) writeHomeSections(safe)
        _homeSections.value = safe
    }

    fun updateHomeSections(transform: (HomeSectionConfig) -> HomeSectionConfig) =
        setHomeSections(transform(_homeSections.value))

    fun reset() {
        set(UiConfig.Default)
        setHomeSections(HomeSectionConfig.Default)
    }

    /** 导出当前完整配置为可分享文本。 */
    fun exportToText(): String = UiConfigCodec.export(_config.value, _homeSections.value)

    /** 导入配置；仅在解析成功时落盘，失败不改变现状。 */
    fun importFromText(raw: String): UiConfigImport {
        val result = UiConfigCodec.decode(raw)
        if (result is UiConfigImport.Success) {
            set(result.config)
            setHomeSections(result.homeSections)
        }
        return result
    }

    private fun read(): UiConfig {
        val stored = runCatching { sp.getInt(KEY_VERSION, CURRENT_VERSION) }
            .getOrDefault(CURRENT_VERSION)
        // 降级安装时版本高于本版，字段语义可能已变，直接回退默认而非误读
        if (stored > CURRENT_VERSION) return UiConfig.Default
        val d = UiConfig.Default
        return UiConfig(
            fontScale = readFloat(K_FONT_SCALE, d.fontScale),
            thumbnailAspectRatio = readFloat(K_ASPECT_RATIO, d.thumbnailAspectRatio),
            cardCornerRadius = readFloat(K_CARD_RADIUS, d.cardCornerRadius.value).dp,
            listThumbnailWidth = readFloat(K_LIST_THUMB_W, d.listThumbnailWidth.value).dp,
            listThumbnailCornerRadius = readFloat(K_LIST_THUMB_RADIUS, d.listThumbnailCornerRadius.value).dp,
            cardTitleTopPadding = readFloat(K_CARD_TITLE_TOP, d.cardTitleTopPadding.value).dp,
            cardMetaSpacing = readFloat(K_CARD_META_SPACING, d.cardMetaSpacing.value).dp,
            listItemPadding = readFloat(K_LIST_ITEM_PADDING, d.listItemPadding.value).dp,
            listItemSpacing = readFloat(K_LIST_ITEM_SPACING, d.listItemSpacing.value).dp,
            listMetaSpacing = readFloat(K_LIST_META_SPACING, d.listMetaSpacing.value).dp,
            cardTitleFontSize = readFloat(K_CARD_TITLE_FS, d.cardTitleFontSize.value).sp,
            cardMetaFontSize = readFloat(K_CARD_META_FS, d.cardMetaFontSize.value).sp,
            gridTitleFontSize = readFloat(K_GRID_TITLE_FS, d.gridTitleFontSize.value).sp,
            gridTitleLineHeight = readFloat(K_GRID_TITLE_LH, d.gridTitleLineHeight.value).sp,
            gridMetaFontSize = readFloat(K_GRID_META_FS, d.gridMetaFontSize.value).sp,
            listTitleFontSize = readFloat(K_LIST_TITLE_FS, d.listTitleFontSize.value).sp,
            listMetaFontSize = readFloat(K_LIST_META_FS, d.listMetaFontSize.value).sp,
            badgeFontSize = readFloat(K_BADGE_FS, d.badgeFontSize.value).sp,
            placeholderEmojiFontSize = readFloat(K_EMOJI_FS, d.placeholderEmojiFontSize.value).sp,
            gridColumnsCompact = readInt(K_COLS_COMPACT, d.gridColumnsCompact),
            gridColumnsMedium = readInt(K_COLS_MEDIUM, d.gridColumnsMedium),
            gridColumnsExpanded = readInt(K_COLS_EXPANDED, d.gridColumnsExpanded),
            gridTargetWidth = readFloat(K_GRID_TARGET_W, d.gridTargetWidth.value).dp,
            gridGap = readFloat(K_GRID_GAP, d.gridGap.value).dp,
            gridMinColumns = readInt(K_GRID_MIN_COLS, d.gridMinColumns),
            gridMaxColumns = readInt(K_GRID_MAX_COLS, d.gridMaxColumns),
            thumbnailQuality = readQuality(d.thumbnailQuality),
        ).sanitized()
    }

    private fun write(cfg: UiConfig) {
        sp.edit {
            putInt(KEY_VERSION, CURRENT_VERSION)
            putFloat(K_FONT_SCALE, cfg.fontScale)
            putFloat(K_ASPECT_RATIO, cfg.thumbnailAspectRatio)
            putFloat(K_CARD_RADIUS, cfg.cardCornerRadius.value)
            putFloat(K_LIST_THUMB_W, cfg.listThumbnailWidth.value)
            putFloat(K_LIST_THUMB_RADIUS, cfg.listThumbnailCornerRadius.value)
            putFloat(K_CARD_TITLE_TOP, cfg.cardTitleTopPadding.value)
            putFloat(K_CARD_META_SPACING, cfg.cardMetaSpacing.value)
            putFloat(K_LIST_ITEM_PADDING, cfg.listItemPadding.value)
            putFloat(K_LIST_ITEM_SPACING, cfg.listItemSpacing.value)
            putFloat(K_LIST_META_SPACING, cfg.listMetaSpacing.value)
            putFloat(K_CARD_TITLE_FS, cfg.cardTitleFontSize.value)
            putFloat(K_CARD_META_FS, cfg.cardMetaFontSize.value)
            putFloat(K_GRID_TITLE_FS, cfg.gridTitleFontSize.value)
            putFloat(K_GRID_TITLE_LH, cfg.gridTitleLineHeight.value)
            putFloat(K_GRID_META_FS, cfg.gridMetaFontSize.value)
            putFloat(K_LIST_TITLE_FS, cfg.listTitleFontSize.value)
            putFloat(K_LIST_META_FS, cfg.listMetaFontSize.value)
            putFloat(K_BADGE_FS, cfg.badgeFontSize.value)
            putFloat(K_EMOJI_FS, cfg.placeholderEmojiFontSize.value)
            putInt(K_COLS_COMPACT, cfg.gridColumnsCompact)
            putInt(K_COLS_MEDIUM, cfg.gridColumnsMedium)
            putInt(K_COLS_EXPANDED, cfg.gridColumnsExpanded)
            putFloat(K_GRID_TARGET_W, cfg.gridTargetWidth.value)
            putFloat(K_GRID_GAP, cfg.gridGap.value)
            putInt(K_GRID_MIN_COLS, cfg.gridMinColumns)
            putInt(K_GRID_MAX_COLS, cfg.gridMaxColumns)
            putString(K_THUMB_QUALITY, cfg.thumbnailQuality.name)
        }
    }

    private fun readQuality(default: ThumbnailQuality): ThumbnailQuality {
        val name = runCatching { sp.getString(K_THUMB_QUALITY, default.name) }.getOrNull()
        return ThumbnailQuality.entries.firstOrNull { it.name == name } ?: default
    }

    private fun readHomeSections(): HomeSectionConfig {
        val d = HomeSectionConfig.Default
        val stored = runCatching { sp.getInt(KEY_VERSION, CURRENT_VERSION) }
            .getOrDefault(CURRENT_VERSION)
        if (stored > CURRENT_VERSION) return d
        return HomeSectionConfig(
            visibleCount = readInt(K_SECTION_VISIBLE_COUNT, d.visibleCount),
            layouts = UiConfigCodec.decodeLayouts(readRaw(K_SECTION_LAYOUTS)),
            order = UiConfigCodec.decodeOrder(readRaw(K_SECTION_ORDER)),
        ).sanitized()
    }

    private fun writeHomeSections(cfg: HomeSectionConfig) {
        sp.edit {
            putInt(K_SECTION_VISIBLE_COUNT, cfg.visibleCount)
            putString(K_SECTION_LAYOUTS, UiConfigCodec.encodeLayouts(cfg.layouts))
            putString(K_SECTION_ORDER, UiConfigCodec.encodeOrder(cfg.order))
        }
    }

    private fun readRaw(key: String): String =
        runCatching { sp.getString(key, "") }.getOrNull().orEmpty()

    private fun readFloat(key: String, default: Float): Float =
        runCatching { sp.getFloat(key, default) }.getOrDefault(default)

    private fun readInt(key: String, default: Int): Int =
        runCatching { sp.getInt(key, default) }.getOrDefault(default)
}
