package app.amisles.hanime.data.backup

import app.amisles.hanime.domain.repository.BackupRepository

internal object BackupFormat {
    const val SCHEMA_VERSION = 1
    const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 48L * 1024 * 1024

    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_FAVORITES = "favorites.json"
    const val ENTRY_GROUPS = "favorite_groups.json"
    const val ENTRY_HISTORY = "watch_history.json"
    const val ENTRY_DOWNLOADS = "downloads.json"
    const val ENTRY_SEARCH = "search_history.json"
    const val ENTRY_SETTINGS = "settings.json"
    const val ENTRY_UI_CONFIG = BackupRepository.ENTRY_UI_CONFIG
}
