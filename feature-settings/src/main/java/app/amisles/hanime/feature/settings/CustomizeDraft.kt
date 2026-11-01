package app.amisles.hanime.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.UiConfig
import app.amisles.hanime.domain.preferences.RelatedLayoutMode

@Stable
internal class CustomizeDraft(
    appliedConfig: UiConfig,
    appliedSections: HomeSectionConfig,
    appliedRelatedLayout: RelatedLayoutMode?
) {
    private var configState by mutableStateOf(appliedConfig)
    private var sectionsState by mutableStateOf(appliedSections)
    private var relatedLayoutState by mutableStateOf(appliedRelatedLayout)

    private var baselineConfig by mutableStateOf(appliedConfig)
    private var baselineSections by mutableStateOf(appliedSections)
    private var baselineRelatedLayout by mutableStateOf(appliedRelatedLayout)

    val config: UiConfig get() = configState
    val sections: HomeSectionConfig get() = sectionsState
    val relatedLayout: RelatedLayoutMode? get() = relatedLayoutState

    val dirty: Boolean
        get() = configState != baselineConfig ||
            sectionsState != baselineSections ||
            relatedLayoutState != baselineRelatedLayout

    fun updateConfig(transform: (UiConfig) -> UiConfig) {
        configState = transform(configState).sanitized()
    }

    fun updateSections(transform: (HomeSectionConfig) -> HomeSectionConfig) {
        sectionsState = transform(sectionsState).sanitized()
    }

    fun setRelatedLayout(mode: RelatedLayoutMode?) {
        relatedLayoutState = mode
    }

    fun replace(config: UiConfig, sections: HomeSectionConfig) {
        configState = config
        sectionsState = sections
        baselineConfig = config
        baselineSections = sections
    }

    fun commit() {
        baselineConfig = configState
        baselineSections = sectionsState
        baselineRelatedLayout = relatedLayoutState
    }

    fun revert() {
        configState = baselineConfig
        sectionsState = baselineSections
        relatedLayoutState = baselineRelatedLayout
    }
}

@Composable
internal fun rememberCustomizeDraft(
    appliedConfig: UiConfig,
    appliedSections: HomeSectionConfig,
    appliedRelatedLayout: RelatedLayoutMode?
): CustomizeDraft = remember { CustomizeDraft(appliedConfig, appliedSections, appliedRelatedLayout) }
