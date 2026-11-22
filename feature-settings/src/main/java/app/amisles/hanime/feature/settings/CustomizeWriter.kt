package app.amisles.hanime.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.UiConfig
import app.amisles.hanime.core.ui.theme.UiConfigImport
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.domain.preferences.AppPreferences
import app.amisles.hanime.domain.preferences.RelatedLayoutMode

@Stable
internal class CustomizeWriter(private val preferences: AppPreferences) {

    fun apply(draft: CustomizeDraft) {
        UiConfigStore.set(draft.config)
        UiConfigStore.setHomeSections(draft.sections)
        writeRelatedLayout(draft.relatedLayout)
        draft.commit()
    }

    fun reset(draft: CustomizeDraft) {
        UiConfigStore.reset()
        draft.replace(UiConfig.Default, HomeSectionConfig.Default)
    }

    fun import(raw: String, draft: CustomizeDraft): UiConfigImport {
        val result = UiConfigStore.importFromText(raw)
        if (result is UiConfigImport.Success) {
            draft.replace(UiConfigStore.config.value, UiConfigStore.homeSections.value)
        }
        return result
    }

    private fun writeRelatedLayout(target: RelatedLayoutMode?) {
        if (target == preferences.relatedLayoutMode) return
        if (target == null) preferences.clearRelatedLayoutMode() else preferences.setRelatedLayoutMode(target)
    }
}

@Composable
internal fun rememberCustomizeWriter(): CustomizeWriter {
    val preferences = LocalAppPreferences.current
    return remember(preferences) { CustomizeWriter(preferences) }
}
