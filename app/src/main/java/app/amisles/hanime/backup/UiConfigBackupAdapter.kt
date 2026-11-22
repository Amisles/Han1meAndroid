package app.amisles.hanime.backup

import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.domain.preferences.UiConfigBackup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiConfigBackupAdapter @Inject constructor() : UiConfigBackup {
    override fun read(): String = UiConfigStore.exportToText()

    override fun apply(raw: String): Boolean = UiConfigStore.importFromTextSync(raw)
}
