package app.amisles.hanime.core.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

sealed interface UiConfigImport {
    data class Success(val config: UiConfig, val homeSections: HomeSectionConfig) : UiConfigImport
    data object InvalidFormat : UiConfigImport
    data object UnsupportedVersion : UiConfigImport
}

object UiConfigCodec {

    const val FORMAT_VERSION = 1

    private const val MARKER = "hanime-ui-config"
    private const val SEP = "\u0001"

    fun export(config: UiConfig, homeSections: HomeSectionConfig): String {
        val c = JSONObject()
            .putF("fontScale", config.fontScale)
            .putF("thumbnailAspectRatio", config.thumbnailAspectRatio)
            .putF("cardCornerRadius", config.cardCornerRadius.value)
            .putF("listThumbnailWidth", config.listThumbnailWidth.value)
            .putF("listThumbnailCornerRadius", config.listThumbnailCornerRadius.value)
            .putF("cardTitleTopPadding", config.cardTitleTopPadding.value)
            .putF("cardMetaSpacing", config.cardMetaSpacing.value)
            .putF("listItemPadding", config.listItemPadding.value)
            .putF("listItemSpacing", config.listItemSpacing.value)
            .putF("listMetaSpacing", config.listMetaSpacing.value)
            .putF("cardTitleFontSize", config.cardTitleFontSize.value)
            .putF("cardMetaFontSize", config.cardMetaFontSize.value)
            .putF("gridTitleFontSize", config.gridTitleFontSize.value)
            .putF("gridTitleLineHeight", config.gridTitleLineHeight.value)
            .putF("gridMetaFontSize", config.gridMetaFontSize.value)
            .putF("listTitleFontSize", config.listTitleFontSize.value)
            .putF("listMetaFontSize", config.listMetaFontSize.value)
            .putF("badgeFontSize", config.badgeFontSize.value)
            .putF("placeholderEmojiFontSize", config.placeholderEmojiFontSize.value)
            .put("gridColumnsCompact", config.gridColumnsCompact)
            .put("gridColumnsMedium", config.gridColumnsMedium)
            .put("gridColumnsExpanded", config.gridColumnsExpanded)
            .putF("gridTargetWidth", config.gridTargetWidth.value)
            .putF("gridGap", config.gridGap.value)
            .put("gridMinColumns", config.gridMinColumns)
            .put("gridMaxColumns", config.gridMaxColumns)
            .put("thumbnailQuality", config.thumbnailQuality.name)

        val s = JSONObject()
            .put("visibleCount", homeSections.visibleCount)
            .put("layouts", encodeLayouts(homeSections.layouts))
            .put("order", encodeOrder(homeSections.order))

        return JSONObject()
            .put("marker", MARKER)
            .put("version", FORMAT_VERSION)
            .put("config", c)
            .put("homeSections", s)
            .toString()
    }

    fun decode(raw: String): UiConfigImport {
        val root = runCatching { JSONObject(raw.trim()) }.getOrNull()
            ?: return UiConfigImport.InvalidFormat
        if (root.optString("marker") != MARKER) return UiConfigImport.InvalidFormat
        if (root.optInt("version", 0) != FORMAT_VERSION) return UiConfigImport.UnsupportedVersion
        val c = root.optJSONObject("config") ?: return UiConfigImport.InvalidFormat

        val d = UiConfig.Default
        val config = UiConfig(
            fontScale = c.optF("fontScale", d.fontScale),
            thumbnailAspectRatio = c.optF("thumbnailAspectRatio", d.thumbnailAspectRatio),
            cardCornerRadius = c.optF("cardCornerRadius", d.cardCornerRadius.value).dp,
            listThumbnailWidth = c.optF("listThumbnailWidth", d.listThumbnailWidth.value).dp,
            listThumbnailCornerRadius = c.optF("listThumbnailCornerRadius", d.listThumbnailCornerRadius.value).dp,
            cardTitleTopPadding = c.optF("cardTitleTopPadding", d.cardTitleTopPadding.value).dp,
            cardMetaSpacing = c.optF("cardMetaSpacing", d.cardMetaSpacing.value).dp,
            listItemPadding = c.optF("listItemPadding", d.listItemPadding.value).dp,
            listItemSpacing = c.optF("listItemSpacing", d.listItemSpacing.value).dp,
            listMetaSpacing = c.optF("listMetaSpacing", d.listMetaSpacing.value).dp,
            cardTitleFontSize = c.optF("cardTitleFontSize", d.cardTitleFontSize.value).sp,
            cardMetaFontSize = c.optF("cardMetaFontSize", d.cardMetaFontSize.value).sp,
            gridTitleFontSize = c.optF("gridTitleFontSize", d.gridTitleFontSize.value).sp,
            gridTitleLineHeight = c.optF("gridTitleLineHeight", d.gridTitleLineHeight.value).sp,
            gridMetaFontSize = c.optF("gridMetaFontSize", d.gridMetaFontSize.value).sp,
            listTitleFontSize = c.optF("listTitleFontSize", d.listTitleFontSize.value).sp,
            listMetaFontSize = c.optF("listMetaFontSize", d.listMetaFontSize.value).sp,
            badgeFontSize = c.optF("badgeFontSize", d.badgeFontSize.value).sp,
            placeholderEmojiFontSize = c.optF("placeholderEmojiFontSize", d.placeholderEmojiFontSize.value).sp,
            gridColumnsCompact = c.optInt("gridColumnsCompact", d.gridColumnsCompact),
            gridColumnsMedium = c.optInt("gridColumnsMedium", d.gridColumnsMedium),
            gridColumnsExpanded = c.optInt("gridColumnsExpanded", d.gridColumnsExpanded),
            gridTargetWidth = c.optF("gridTargetWidth", d.gridTargetWidth.value).dp,
            gridGap = c.optF("gridGap", d.gridGap.value).dp,
            gridMinColumns = c.optInt("gridMinColumns", d.gridMinColumns),
            gridMaxColumns = c.optInt("gridMaxColumns", d.gridMaxColumns),
            thumbnailQuality = ThumbnailQuality.entries
                .firstOrNull { it.name == c.optString("thumbnailQuality") }
                ?: d.thumbnailQuality,
        ).sanitized()

        val hd = HomeSectionConfig.Default
        val s = root.optJSONObject("homeSections")
        val homeSections = HomeSectionConfig(
            visibleCount = s?.optInt("visibleCount", hd.visibleCount) ?: hd.visibleCount,
            layouts = decodeLayouts(s?.optString("layouts").orEmpty()),
            order = decodeOrder(s?.optString("order").orEmpty()),
        ).sanitized()

        return UiConfigImport.Success(config, homeSections)
    }

    fun encodeLayouts(layouts: Map<String, HomeSectionLayout>): String =
        layouts.entries.joinToString(SEP) { "${it.key}=${it.value.name}" }

    fun decodeLayouts(raw: String): Map<String, HomeSectionLayout> {
        if (raw.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, HomeSectionLayout>()
        raw.split(SEP).forEach { entry ->
            val at = entry.lastIndexOf('=')
            if (at <= 0) return@forEach
            val title = entry.substring(0, at)
            val mode = HomeSectionLayout.entries.firstOrNull { it.name == entry.substring(at + 1) }
            if (mode != null && title.isNotEmpty()) result[title] = mode
        }
        return result
    }

    fun encodeOrder(order: List<String>): String = order.joinToString(SEP)

    fun decodeOrder(raw: String): List<String> =
        if (raw.isEmpty()) emptyList() else raw.split(SEP).filter { it.isNotEmpty() }

    private fun JSONObject.putF(key: String, value: Float): JSONObject = put(key, value.toDouble())

    private fun JSONObject.optF(key: String, default: Float): Float =
        optDouble(key, default.toDouble()).toFloat()
}
