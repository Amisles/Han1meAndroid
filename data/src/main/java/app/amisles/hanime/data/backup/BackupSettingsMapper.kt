package app.amisles.hanime.data.backup

import app.amisles.hanime.data.preferences.PlayerLayoutCodec
import app.amisles.hanime.domain.model.BackupSettings
import app.amisles.hanime.domain.preferences.SearchLayoutMode
import app.amisles.hanime.domain.preferences.ThemeMode
import app.amisles.hanime.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSettingsMapper @Inject constructor(
    private val settings: SettingsRepository
) {
    fun read(): BackupSettings = BackupSettings(
        appLanguage = settings.appLanguage,
        themeMode = settings.themeMode.name,
        baseUrl = settings.baseUrl,
        dynamicColor = settings.dynamicColor,
        playbackSpeed = settings.playbackSpeed,
        preferredQuality = settings.preferredQuality,
        autoPlayNext = settings.autoPlayNext,
        loopPlayback = settings.loopPlayback,
        longPressBoostSpeed = settings.longPressBoostSpeed,
        doubleTapSeek = settings.doubleTapSeek,
        autoQuality = settings.autoQuality,
        autoRotateFullscreen = settings.autoRotateFullscreen,
        maxDownloadConcurrent = settings.maxDownloadConcurrent,
        videoDetailCacheLimit = settings.videoDetailCacheLimit,
        downloadRateLimit = settings.downloadRateLimit,
        subscriptionReminder = settings.subscriptionReminder,
        downloadStoragePath = settings.downloadStoragePathFlow.value,
        searchLayoutMode = settings.searchLayoutMode?.name,
        playerLayout = PlayerLayoutCodec.encode(settings.playerLayout)
    )

    fun apply(s: BackupSettings) {
        s.appLanguage?.let { settings.setAppLanguage(it) }
        s.themeMode?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }?.let { settings.setThemeMode(it) }
        }
        s.dynamicColor?.let { settings.setDynamicColor(it) }
        s.baseUrl?.let { settings.setBaseUrl(it) }
        s.playbackSpeed?.let { settings.setPlaybackSpeed(it) }
        s.preferredQuality?.let { settings.setPreferredQuality(it) }
        s.autoPlayNext?.let { settings.setAutoPlayNext(it) }
        s.loopPlayback?.let { settings.setLoopPlayback(it) }
        s.longPressBoostSpeed?.let { settings.setLongPressBoostSpeed(it) }
        s.doubleTapSeek?.let { settings.setDoubleTapSeek(it) }
        s.autoQuality?.let { settings.setAutoQuality(it) }
        s.autoRotateFullscreen?.let { settings.setAutoRotateFullscreen(it) }
        s.maxDownloadConcurrent?.let { settings.setMaxDownloadConcurrent(it) }
        s.videoDetailCacheLimit?.let { settings.setVideoDetailCacheLimit(it) }
        s.downloadRateLimit?.let { settings.setDownloadRateLimit(it) }
        s.subscriptionReminder?.let { settings.setSubscriptionReminder(it) }
        s.downloadStoragePath?.let { settings.downloadStoragePath = it }
        s.searchLayoutMode?.let { name ->
            SearchLayoutMode.entries.firstOrNull { it.name == name }
                ?.let { settings.setSearchLayoutMode(it) }
        }
        s.playerLayout?.let { settings.setPlayerLayout(PlayerLayoutCodec.decode(it)) }
    }
}
