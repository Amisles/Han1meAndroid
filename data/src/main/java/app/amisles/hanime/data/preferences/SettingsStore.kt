package app.amisles.hanime.data.preferences

import android.net.Uri
import app.amisles.hanime.core.common.util.LocaleHelper
import app.amisles.hanime.domain.model.ComicReadMode
import app.amisles.hanime.domain.model.SubtitleLang
import app.amisles.hanime.domain.preferences.PageLayoutKey
import app.amisles.hanime.domain.preferences.PageLayoutMode
import app.amisles.hanime.domain.preferences.PageLayoutModes
import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerResizeMode
import app.amisles.hanime.domain.preferences.RelatedLayoutMode
import app.amisles.hanime.domain.preferences.SearchLayoutMode
import app.amisles.hanime.domain.preferences.ThemeMode
import app.amisles.hanime.domain.preferences.UiHintStore
import app.amisles.hanime.domain.repository.AppLockResult
import app.amisles.hanime.domain.repository.AppLockSettings
import app.amisles.hanime.domain.repository.CrashSettings
import app.amisles.hanime.domain.repository.PersistenceHealth
import app.amisles.hanime.domain.repository.PlaylistSettings
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.domain.repository.UpdateSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 非敏感设置存储：语言、外观、播放器、下载、阅读、更新与一次性提示。
 *
 * 只通过 [PreferenceStorage] 读写，不再持有会话凭据；每个设置项由独立的 StateFlow 承载，
 * 写入失败不阻断界面，但会通过 [writeFailed] 暴露给设置页（凭据类写入另有
 * [app.amisles.hanime.domain.result.SessionSaveResult]）。
 */
@Singleton
class SettingsStore @Inject constructor(
    private val storage: PreferenceStorage
): SettingsRepository, UiHintStore, UpdateSettings, PlaylistSettings, CrashSettings, PersistenceHealth,
    AppLockSettings {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _maxDownloadConcurrentFlow = MutableStateFlow(3)
    override val maxDownloadConcurrentFlow: StateFlow<Int> = _maxDownloadConcurrentFlow.asStateFlow()

    private val _downloadRateLimitFlow = MutableStateFlow(0)
    override val downloadRateLimitFlow: StateFlow<Int> = _downloadRateLimitFlow.asStateFlow()

    private val _downloadWifiOnlyFlow = MutableStateFlow(false)
    override val downloadWifiOnlyFlow: StateFlow<Boolean> = _downloadWifiOnlyFlow.asStateFlow()

    private val _downloadChargingOnlyFlow = MutableStateFlow(false)
    override val downloadChargingOnlyFlow: StateFlow<Boolean> = _downloadChargingOnlyFlow.asStateFlow()

    private val _downloadNightOnlyFlow = MutableStateFlow(false)
    override val downloadNightOnlyFlow: StateFlow<Boolean> = _downloadNightOnlyFlow.asStateFlow()

    private val _subscriptionReminderFlow = MutableStateFlow(false)
    override val subscriptionReminderFlow: StateFlow<Boolean> = _subscriptionReminderFlow.asStateFlow()

    private val _watchStatsEnabledFlow = MutableStateFlow(true)
    override val watchStatsEnabledFlow: StateFlow<Boolean> = _watchStatsEnabledFlow.asStateFlow()

    private val _subscriptionsHideWatchedFlow = MutableStateFlow(false)
    override val subscriptionsHideWatchedFlow: StateFlow<Boolean> = _subscriptionsHideWatchedFlow.asStateFlow()

    private val _weeklyReportDismissedWeek = MutableStateFlow("")

    private val _baseUrlFlow = MutableStateFlow(SettingsRepository.DEFAULT_BASE_URL)
    override val baseUrlFlow: StateFlow<String> = _baseUrlFlow.asStateFlow()

    private val _appLanguageFlow = MutableStateFlow(LANGUAGE_ZH_CN)
    override val appLanguageFlow: StateFlow<String> = _appLanguageFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private val _dynamicColorFlow = MutableStateFlow(false)
    override val dynamicColorFlow: StateFlow<Boolean> = _dynamicColorFlow.asStateFlow()

    private val _privacyModeFlow = MutableStateFlow(false)
    override val privacyModeFlow: StateFlow<Boolean> = _privacyModeFlow.asStateFlow()

    private val _appLockEnabledFlow = MutableStateFlow(false)
    override val appLockEnabledFlow: StateFlow<Boolean> = _appLockEnabledFlow.asStateFlow()

    private val _playbackSpeedFlow = MutableStateFlow(1f)
    override val playbackSpeedFlow: StateFlow<Float> = _playbackSpeedFlow.asStateFlow()

    private val _preferredQualityFlow = MutableStateFlow("")
    override val preferredQualityFlow: StateFlow<String> = _preferredQualityFlow.asStateFlow()

    private val _subtitleLangFlow = MutableStateFlow(SubtitleLang.SIMPLIFIED)
    override val subtitleLangFlow: StateFlow<String> = _subtitleLangFlow.asStateFlow()

    private val _autoPlayNextFlow = MutableStateFlow(false)
    override val autoPlayNextFlow: StateFlow<Boolean> = _autoPlayNextFlow.asStateFlow()

    private val _autoPlayRelatedFlow = MutableStateFlow(false)
    override val autoPlayRelatedFlow: StateFlow<Boolean> = _autoPlayRelatedFlow.asStateFlow()

    private val _loopPlaybackFlow = MutableStateFlow(false)
    override val loopPlaybackFlow: StateFlow<Boolean> = _loopPlaybackFlow.asStateFlow()

    private val _realtimeTranslationFlow = MutableStateFlow(false)
    override val realtimeTranslationFlow: StateFlow<Boolean> = _realtimeTranslationFlow.asStateFlow()

    private val _longPressBoostSpeedFlow = MutableStateFlow(DEFAULT_LONG_PRESS_SPEED)
    override val longPressBoostSpeedFlow: StateFlow<Float> = _longPressBoostSpeedFlow.asStateFlow()

    private val _doubleTapSeekFlow = MutableStateFlow(true)
    override val doubleTapSeekFlow: StateFlow<Boolean> = _doubleTapSeekFlow.asStateFlow()

    private val _autoQualityFlow = MutableStateFlow(true)
    override val autoQualityFlow: StateFlow<Boolean> = _autoQualityFlow.asStateFlow()

    private val _autoRotateFullscreenFlow = MutableStateFlow(true)
    override val autoRotateFullscreenFlow: StateFlow<Boolean> = _autoRotateFullscreenFlow.asStateFlow()

    private val _playerResizeModeFlow = MutableStateFlow(PlayerResizeMode.FIT)
    override val playerResizeModeFlow: StateFlow<PlayerResizeMode> = _playerResizeModeFlow.asStateFlow()

    private val _orientationPinnedFlow = MutableStateFlow(false)
    override val orientationPinnedFlow: StateFlow<Boolean> = _orientationPinnedFlow.asStateFlow()

    private val _playerLayoutFlow = MutableStateFlow(PlayerLayout.DEFAULT)
    override val playerLayoutFlow: StateFlow<PlayerLayout> = _playerLayoutFlow.asStateFlow()

    private val _videoDetailCacheLimitFlow =
        MutableStateFlow(SettingsRepository.DEFAULT_VIDEO_DETAIL_CACHE_LIMIT)
    override val videoDetailCacheLimitFlow: StateFlow<Int> = _videoDetailCacheLimitFlow.asStateFlow()

    private val _downloadStoragePathFlow = MutableStateFlow("")
    override val downloadStoragePathFlow: StateFlow<String> = _downloadStoragePathFlow.asStateFlow()

    private val _searchLayoutModeFlow = MutableStateFlow<SearchLayoutMode?>(null)
    override val searchLayoutModeFlow: StateFlow<SearchLayoutMode?> = _searchLayoutModeFlow.asStateFlow()

    private val _pageLayoutModesFlow = MutableStateFlow(PageLayoutModes(search = null))
    override val pageLayoutModesFlow: StateFlow<PageLayoutModes> = _pageLayoutModesFlow.asStateFlow()

    private val _relatedLayoutModeFlow = MutableStateFlow<RelatedLayoutMode?>(null)
    override val relatedLayoutModeFlow: StateFlow<RelatedLayoutMode?> = _relatedLayoutModeFlow.asStateFlow()

    private val _comicReadModeFlow = MutableStateFlow<ComicReadMode?>(null)
    override val comicReadModeFlow: StateFlow<ComicReadMode?> = _comicReadModeFlow.asStateFlow()

    private val _comicReaderBlackFlow = MutableStateFlow(false)
    override val comicReaderBlackFlow: StateFlow<Boolean> = _comicReaderBlackFlow.asStateFlow()

    private val _skippedUpdateVersionFlow = MutableStateFlow("")
    override val skippedVersionFlow: StateFlow<String> = _skippedUpdateVersionFlow.asStateFlow()

    private val _autoCheckUpdateFlow = MutableStateFlow(true)
    override val autoCheckEnabledFlow: StateFlow<Boolean> = _autoCheckUpdateFlow.asStateFlow()

    override val loginSupportedFlow: StateFlow<Boolean> = _baseUrlFlow
        .map { isLoginSupportedHost(it) }
        .stateIn(scope, SharingStarted.Eagerly, isLoginSupportedHost(_baseUrlFlow.value))

    override val writeFailed: StateFlow<Boolean> = storage.writeFailed

    init {
        readPersistedValues()
    }

    private fun readPersistedValues() {
        _maxDownloadConcurrentFlow.value = storage.readInt(PreferenceKeys.MAX_DOWNLOAD_CONCURRENT, 3)
        _downloadRateLimitFlow.value = storage.readInt(PreferenceKeys.DOWNLOAD_RATE_LIMIT, 0)
        _downloadWifiOnlyFlow.value = storage.readBoolean(PreferenceKeys.DOWNLOAD_WIFI_ONLY, false)
        _downloadChargingOnlyFlow.value = storage.readBoolean(PreferenceKeys.DOWNLOAD_CHARGING_ONLY, false)
        _downloadNightOnlyFlow.value = storage.readBoolean(PreferenceKeys.DOWNLOAD_NIGHT_ONLY, false)
        _subscriptionReminderFlow.value = storage.readBoolean(PreferenceKeys.SUBSCRIPTION_REMINDER, false)
        _watchStatsEnabledFlow.value = storage.readBoolean(PreferenceKeys.WATCH_STATS_ENABLED, true)
        _subscriptionsHideWatchedFlow.value =
            storage.readBoolean(PreferenceKeys.SUBSCRIPTIONS_HIDE_WATCHED, false)
        _weeklyReportDismissedWeek.value =
            storage.readString(PreferenceKeys.WEEKLY_REPORT_DISMISSED, "").orEmpty()
        val rawBaseUrl = storage.readString(PreferenceKeys.BASE_URL, SettingsRepository.DEFAULT_BASE_URL)
            ?.ifBlank { SettingsRepository.DEFAULT_BASE_URL }
            ?: SettingsRepository.DEFAULT_BASE_URL
        val safeBaseUrl = sanitizeBaseUrl(rawBaseUrl) ?: SettingsRepository.DEFAULT_BASE_URL
        if (safeBaseUrl != rawBaseUrl) {
            storage.write { putString(PreferenceKeys.BASE_URL, safeBaseUrl) }
        }
        _baseUrlFlow.value = safeBaseUrl
        _appLanguageFlow.value = storage.readString(PreferenceKeys.APP_LANGUAGE, LANGUAGE_ZH_CN)
            ?: LANGUAGE_ZH_CN
        _themeModeFlow.value = ThemeMode.fromName(storage.readString(PreferenceKeys.THEME_MODE, null))
        _dynamicColorFlow.value = storage.readBoolean(PreferenceKeys.DYNAMIC_COLOR, false)
        _privacyModeFlow.value = storage.readBoolean(PreferenceKeys.PRIVACY_MODE, false)
        _appLockEnabledFlow.value = storage.readBoolean(PreferenceKeys.APP_LOCK_ENABLED, false)
        _playbackSpeedFlow.value = storage.readFloat(PreferenceKeys.PLAYBACK_SPEED, 1f)
        _preferredQualityFlow.value = storage.readString(PreferenceKeys.PREFERRED_QUALITY, "").orEmpty()
        _subtitleLangFlow.value = storage.readString(PreferenceKeys.SUBTITLE_LANG, null)
            ?.takeIf { it in SubtitleLang.SUPPORTED }
            ?: SubtitleLang.SIMPLIFIED
        _autoPlayNextFlow.value = storage.readBoolean(PreferenceKeys.AUTO_PLAY_NEXT, false)
        _autoPlayRelatedFlow.value = storage.readBoolean(PreferenceKeys.AUTO_PLAY_RELATED, false)
        _loopPlaybackFlow.value = storage.readBoolean(PreferenceKeys.LOOP_PLAYBACK, false)
        _realtimeTranslationFlow.value =
            storage.readBoolean(PreferenceKeys.REALTIME_TRANSLATION, false)
        _longPressBoostSpeedFlow.value = storage.readFloat(
            PreferenceKeys.LONG_PRESS_SPEED,
            DEFAULT_LONG_PRESS_SPEED
        ).coerceIn(MIN_LONG_PRESS_SPEED, MAX_LONG_PRESS_SPEED)
        _doubleTapSeekFlow.value = storage.readBoolean(PreferenceKeys.DOUBLE_TAP_SEEK, true)
        _autoQualityFlow.value = storage.readBoolean(PreferenceKeys.AUTO_QUALITY, true)
        _autoRotateFullscreenFlow.value = storage.readBoolean(PreferenceKeys.AUTO_ROTATE_FULLSCREEN, true)
        _playerResizeModeFlow.value = PlayerResizeMode.fromNameOrNull(
            storage.readString(PreferenceKeys.PLAYER_RESIZE_MODE, null)
        ) ?: PlayerResizeMode.FIT
        _orientationPinnedFlow.value = storage.readBoolean(PreferenceKeys.ORIENTATION_PINNED, false)
        _playerLayoutFlow.value = PlayerLayoutCodec.decode(storage.readString(PreferenceKeys.PLAYER_LAYOUT, null))
        _videoDetailCacheLimitFlow.value = storage.readInt(
            PreferenceKeys.VIDEO_DETAIL_CACHE_LIMIT,
            SettingsRepository.DEFAULT_VIDEO_DETAIL_CACHE_LIMIT
        ).coerceIn(
            SettingsRepository.MIN_VIDEO_DETAIL_CACHE_LIMIT,
            SettingsRepository.MAX_VIDEO_DETAIL_CACHE_LIMIT
        )
        _downloadStoragePathFlow.value = storage.readString(PreferenceKeys.DOWNLOAD_STORAGE_PATH, "").orEmpty()
        _searchLayoutModeFlow.value = SearchLayoutMode.fromNameOrNull(
            storage.readString(PreferenceKeys.SEARCH_LAYOUT_MODE, null)
        )
        _pageLayoutModesFlow.value = readPageLayoutModes()
        _relatedLayoutModeFlow.value = RelatedLayoutMode.fromNameOrNull(
            storage.readString(PreferenceKeys.RELATED_LAYOUT_MODE, null)
        )
        _comicReadModeFlow.value = ComicReadMode.entries
            .firstOrNull { it.name == storage.readString(PreferenceKeys.COMIC_READ_MODE, null) }
        _comicReaderBlackFlow.value = storage.readBoolean(PreferenceKeys.COMIC_READER_BLACK, false)
        _skippedUpdateVersionFlow.value =
            storage.readString(PreferenceKeys.SKIPPED_UPDATE_VERSION, "").orEmpty()
        _autoCheckUpdateFlow.value = storage.readBoolean(PreferenceKeys.AUTO_CHECK_UPDATE, true)
    }

    override val maxDownloadConcurrent: Int get() = _maxDownloadConcurrentFlow.value
    override val downloadRateLimit: Int get() = _downloadRateLimitFlow.value
    override val subscriptionReminder: Boolean get() = _subscriptionReminderFlow.value
    override val watchStatsEnabled: Boolean get() = _watchStatsEnabledFlow.value
    override val baseUrl: String get() = _baseUrlFlow.value
    override val isLoginSupported: Boolean get() = isLoginSupportedHost(baseUrl)
    override val appLanguage: String get() = _appLanguageFlow.value
    override val themeMode: ThemeMode get() = _themeModeFlow.value
    override val dynamicColor: Boolean get() = _dynamicColorFlow.value
    override val privacyMode: Boolean get() = _privacyModeFlow.value
    override val playbackSpeed: Float get() = _playbackSpeedFlow.value
    override val preferredQuality: String get() = _preferredQualityFlow.value
    override val subtitleLang: String get() = _subtitleLangFlow.value
    override val autoPlayNext: Boolean get() = _autoPlayNextFlow.value
    override val autoPlayRelated: Boolean get() = _autoPlayRelatedFlow.value
    override val loopPlayback: Boolean get() = _loopPlaybackFlow.value
    override val realtimeTranslation: Boolean get() = _realtimeTranslationFlow.value
    override val longPressBoostSpeed: Float get() = _longPressBoostSpeedFlow.value
    override val doubleTapSeek: Boolean get() = _doubleTapSeekFlow.value
    override val autoQuality: Boolean get() = _autoQualityFlow.value
    override val autoRotateFullscreen: Boolean get() = _autoRotateFullscreenFlow.value
    override val playerResizeMode: PlayerResizeMode get() = _playerResizeModeFlow.value
    override val orientationPinned: Boolean get() = _orientationPinnedFlow.value
    override val playerLayout: PlayerLayout get() = _playerLayoutFlow.value
    override val videoDetailCacheLimit: Int get() = _videoDetailCacheLimitFlow.value
    override val searchLayoutMode: SearchLayoutMode? get() = _searchLayoutModeFlow.value
    override val pageLayoutModes: PageLayoutModes get() = _pageLayoutModesFlow.value
    override val relatedLayoutMode: RelatedLayoutMode? get() = _relatedLayoutModeFlow.value
    override val comicReadMode: ComicReadMode? get() = _comicReadModeFlow.value
    override val comicReaderBlack: Boolean get() = _comicReaderBlackFlow.value
    override val downloadWifiOnly: Boolean get() = _downloadWifiOnlyFlow.value
    override val downloadChargingOnly: Boolean get() = _downloadChargingOnlyFlow.value
    override val downloadNightOnly: Boolean get() = _downloadNightOnlyFlow.value
    override val subscriptionsHideWatched: Boolean get() = _subscriptionsHideWatchedFlow.value
    override val skippedVersion: String get() = _skippedUpdateVersionFlow.value
    override val autoCheckEnabled: Boolean get() = _autoCheckUpdateFlow.value
    override val acknowledgedCrashFile: String
        get() = storage.readString(PreferenceKeys.ACKNOWLEDGED_CRASH, "").orEmpty()
    override val onboardingDone: Boolean
        get() = storage.readBoolean(PreferenceKeys.ONBOARDING_DONE, false)
    override val notificationPromptShown: Boolean
        get() = storage.readBoolean(PreferenceKeys.NOTIFICATION_PROMPT_SHOWN, false)
    override val homeLongPressHintDismissed: Boolean
        get() = storage.readBoolean(PreferenceKeys.HOME_LONG_PRESS_HINT_DISMISSED, false)
    override val downloadQuickHintDismissed: Boolean
        get() = storage.readBoolean(PreferenceKeys.DOWNLOAD_QUICK_HINT_DISMISSED, false)
    override val playerGestureIntroShown: Boolean
        get() = storage.readBoolean(PreferenceKeys.PLAYER_GESTURE_INTRO_SHOWN, false)
    override val subscriptionSeenVideoIds: Set<String>
        get() = storage.readString(PreferenceKeys.SUBSCRIPTION_SEEN_IDS, "")
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .toSet()
    override val downloadQueueOrder: List<Int>
        get() = runCatching {
            storage.readString(PreferenceKeys.DOWNLOAD_QUEUE_ORDER, "").orEmpty()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
        }.getOrDefault(emptyList())

    override var downloadStoragePath: String
        get() = _downloadStoragePathFlow.value
        set(value) {
            val safe = value.trim()
            storage.write { putString(PreferenceKeys.DOWNLOAD_STORAGE_PATH, safe) }
            _downloadStoragePathFlow.value = safe
        }

    override var playlistCsrfToken: String
        get() = storage.readString(PreferenceKeys.PLAYLIST_CSRF_TOKEN, "").orEmpty()
        set(value) {
            storage.write { putString(PreferenceKeys.PLAYLIST_CSRF_TOKEN, value) }
        }

    override var playlistMigrationDone: Boolean
        get() = storage.readBoolean(PreferenceKeys.PLAYLIST_MIGRATION_DONE, false)
        set(value) {
            storage.write { putBoolean(PreferenceKeys.PLAYLIST_MIGRATION_DONE, value) }
        }

    override fun setAppLanguage(lang: String) {
        storage.write { putString(PreferenceKeys.APP_LANGUAGE, lang) }
        _appLanguageFlow.value = lang
        LocaleHelper.rememberLanguage(lang)
    }

    override fun setThemeMode(mode: ThemeMode) {
        storage.write { putString(PreferenceKeys.THEME_MODE, mode.name) }
        _themeModeFlow.value = mode
    }

    override fun setDynamicColor(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.DYNAMIC_COLOR, enabled) }
        _dynamicColorFlow.value = enabled
    }

    override fun setPrivacyMode(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.PRIVACY_MODE, enabled) }
        _privacyModeFlow.value = enabled
    }

    override suspend fun setPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        awaitSecureStore()
        if (!storage.isSessionReady()) return@withContext false
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        if (hash.isEmpty()) return@withContext false
        val written = storage.writeBlocking {
            putBoolean(PreferenceKeys.APP_LOCK_ENABLED, true)
            putString(PreferenceKeys.APP_LOCK_PIN_SALT, salt)
            putString(PreferenceKeys.APP_LOCK_PIN_HASH, hash)
        }
        if (written) _appLockEnabledFlow.value = true
        written
    }

    override fun disableAppLock() {
        storage.write {
            putBoolean(PreferenceKeys.APP_LOCK_ENABLED, false)
            remove(PreferenceKeys.APP_LOCK_PIN_SALT)
            remove(PreferenceKeys.APP_LOCK_PIN_HASH)
        }
        _appLockEnabledFlow.value = false
    }

    override suspend fun verifyPin(pin: String): AppLockResult = withContext(Dispatchers.Default) {
        awaitSecureStore()
        if (!storage.isSessionReady()) return@withContext AppLockResult.UNAVAILABLE
        val salt = storage.readString(PreferenceKeys.APP_LOCK_PIN_SALT, null).orEmpty()
        val hash = storage.readString(PreferenceKeys.APP_LOCK_PIN_HASH, null).orEmpty()
        if (salt.isEmpty() || hash.isEmpty()) return@withContext AppLockResult.UNAVAILABLE
        if (PinHasher.matches(pin, salt, hash)) AppLockResult.UNLOCKED else AppLockResult.MISMATCH
    }

    /** 加密存储首次打开要经 Keystore，冷启动后要几十毫秒才就绪；不等它会把「还没打开」误判成校验值丢失。 */
    private suspend fun awaitSecureStore() {
        if (storage.isSessionReady() || storage.hasSessionFailed()) return
        withTimeoutOrNull(SECURE_STORE_WAIT_MS) { storage.sessionReady.first { it } }
    }

    override fun setBaseUrl(url: String): Boolean {
        val input = url.ifBlank { SettingsRepository.DEFAULT_BASE_URL }
        val safeUrl = sanitizeBaseUrl(input) ?: return false
        storage.write { putString(PreferenceKeys.BASE_URL, safeUrl) }
        _baseUrlFlow.value = safeUrl
        return true
    }

    override fun setMaxDownloadConcurrent(max: Int) {
        val safeMax = max.coerceIn(1, 5)
        storage.write { putInt(PreferenceKeys.MAX_DOWNLOAD_CONCURRENT, safeMax) }
        _maxDownloadConcurrentFlow.value = safeMax
    }

    override fun setDownloadRateLimit(megabytesPerSecond: Int) {
        val safe = megabytesPerSecond.coerceIn(0, 20)
        storage.write { putInt(PreferenceKeys.DOWNLOAD_RATE_LIMIT, safe) }
        _downloadRateLimitFlow.value = safe
    }

    override fun setDownloadWifiOnly(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.DOWNLOAD_WIFI_ONLY, enabled) }
        _downloadWifiOnlyFlow.value = enabled
    }

    override fun setDownloadChargingOnly(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.DOWNLOAD_CHARGING_ONLY, enabled) }
        _downloadChargingOnlyFlow.value = enabled
    }

    override fun setDownloadNightOnly(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.DOWNLOAD_NIGHT_ONLY, enabled) }
        _downloadNightOnlyFlow.value = enabled
    }

    override fun setDownloadQueueOrder(ids: List<Int>) {
        storage.write { putString(PreferenceKeys.DOWNLOAD_QUEUE_ORDER, ids.joinToString(",")) }
    }

    override fun setSubscriptionReminder(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.SUBSCRIPTION_REMINDER, enabled) }
        _subscriptionReminderFlow.value = enabled
    }

    override fun setWatchStatsEnabled(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.WATCH_STATS_ENABLED, enabled) }
        _watchStatsEnabledFlow.value = enabled
    }

    override fun setSubscriptionsHideWatched(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.SUBSCRIPTIONS_HIDE_WATCHED, enabled) }
        _subscriptionsHideWatchedFlow.value = enabled
    }

    override fun setSubscriptionSeenVideoIds(ids: Set<String>) {
        storage.write { putString(PreferenceKeys.SUBSCRIPTION_SEEN_IDS, ids.joinToString("\n")) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        storage.write { putFloat(PreferenceKeys.PLAYBACK_SPEED, safe) }
        _playbackSpeedFlow.value = safe
    }

    override fun setLongPressBoostSpeed(speed: Float) {
        val safe = speed.coerceIn(MIN_LONG_PRESS_SPEED, MAX_LONG_PRESS_SPEED)
        storage.write { putFloat(PreferenceKeys.LONG_PRESS_SPEED, safe) }
        _longPressBoostSpeedFlow.value = safe
    }

    override fun setDoubleTapSeek(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.DOUBLE_TAP_SEEK, enabled) }
        _doubleTapSeekFlow.value = enabled
    }

    override fun setAutoQuality(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.AUTO_QUALITY, enabled) }
        _autoQualityFlow.value = enabled
    }

    override fun setAutoRotateFullscreen(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.AUTO_ROTATE_FULLSCREEN, enabled) }
        _autoRotateFullscreenFlow.value = enabled
    }

    override fun setPlayerResizeMode(mode: PlayerResizeMode) {
        storage.write { putString(PreferenceKeys.PLAYER_RESIZE_MODE, mode.name) }
        _playerResizeModeFlow.value = mode
    }

    override fun setOrientationPinned(pinned: Boolean) {
        storage.write { putBoolean(PreferenceKeys.ORIENTATION_PINNED, pinned) }
        _orientationPinnedFlow.value = pinned
    }

    override fun setPlayerLayout(layout: PlayerLayout) {
        val safe = PlayerLayout.sanitize(layout)
        storage.write { putString(PreferenceKeys.PLAYER_LAYOUT, PlayerLayoutCodec.encode(safe)) }
        _playerLayoutFlow.value = safe
    }

    override fun setVideoDetailCacheLimit(limit: Int) {
        val safe = limit.coerceIn(
            SettingsRepository.MIN_VIDEO_DETAIL_CACHE_LIMIT,
            SettingsRepository.MAX_VIDEO_DETAIL_CACHE_LIMIT
        )
        storage.write { putInt(PreferenceKeys.VIDEO_DETAIL_CACHE_LIMIT, safe) }
        _videoDetailCacheLimitFlow.value = safe
    }

    override fun setPreferredQuality(resolution: String) {
        storage.write { putString(PreferenceKeys.PREFERRED_QUALITY, resolution) }
        _preferredQualityFlow.value = resolution
    }

    override fun setSubtitleLang(lang: String) {
        if (lang !in SubtitleLang.SUPPORTED) return
        storage.write { putString(PreferenceKeys.SUBTITLE_LANG, lang) }
        _subtitleLangFlow.value = lang
    }

    override fun setAutoPlayNext(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.AUTO_PLAY_NEXT, enabled) }
        _autoPlayNextFlow.value = enabled
    }

    override fun setAutoPlayRelated(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.AUTO_PLAY_RELATED, enabled) }
        _autoPlayRelatedFlow.value = enabled
    }

    override fun setLoopPlayback(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.LOOP_PLAYBACK, enabled) }
        _loopPlaybackFlow.value = enabled
    }

    override fun setRealtimeTranslation(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.REALTIME_TRANSLATION, enabled) }
        _realtimeTranslationFlow.value = enabled
    }

    override fun setSearchLayoutMode(mode: SearchLayoutMode) {
        storage.write { putString(PreferenceKeys.SEARCH_LAYOUT_MODE, mode.name) }
        _searchLayoutModeFlow.value = mode
    }

    override fun setPageLayoutMode(page: PageLayoutKey, mode: PageLayoutMode) {
        storage.write { putString(PreferenceKeys.PAGE_LAYOUT_PREFIX + page.name, mode.name) }
        _pageLayoutModesFlow.value = _pageLayoutModesFlow.value.copyOf(page, mode)
    }

    override fun clearPageLayoutMode(page: PageLayoutKey) {
        storage.write { remove(PreferenceKeys.PAGE_LAYOUT_PREFIX + page.name) }
        _pageLayoutModesFlow.value = _pageLayoutModesFlow.value.copyOf(page, null)
    }

    override fun setRelatedLayoutMode(mode: RelatedLayoutMode) {
        storage.write { putString(PreferenceKeys.RELATED_LAYOUT_MODE, mode.name) }
        _relatedLayoutModeFlow.value = mode
    }

    override fun setComicReadMode(mode: ComicReadMode) {
        storage.write { putString(PreferenceKeys.COMIC_READ_MODE, mode.name) }
        _comicReadModeFlow.value = mode
    }

    override fun setComicReaderBlack(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.COMIC_READER_BLACK, enabled) }
        _comicReaderBlackFlow.value = enabled
    }

    override fun clearRelatedLayoutMode() {
        storage.write { remove(PreferenceKeys.RELATED_LAYOUT_MODE) }
        _relatedLayoutModeFlow.value = null
    }

    override fun setSkippedVersion(versionName: String) {
        val clean = versionName.trim()
        storage.write { putString(PreferenceKeys.SKIPPED_UPDATE_VERSION, clean) }
        _skippedUpdateVersionFlow.value = clean
    }

    override fun setAutoCheckEnabled(enabled: Boolean) {
        storage.write { putBoolean(PreferenceKeys.AUTO_CHECK_UPDATE, enabled) }
        _autoCheckUpdateFlow.value = enabled
    }

    override fun setAcknowledgedCrashFile(name: String) {
        storage.write { putString(PreferenceKeys.ACKNOWLEDGED_CRASH, name) }
    }

    override fun markOnboardingDone() {
        storage.write { putBoolean(PreferenceKeys.ONBOARDING_DONE, true) }
    }

    override fun markNotificationPromptShown() {
        storage.write { putBoolean(PreferenceKeys.NOTIFICATION_PROMPT_SHOWN, true) }
    }

    override fun dismissHomeLongPressHint() {
        storage.write { putBoolean(PreferenceKeys.HOME_LONG_PRESS_HINT_DISMISSED, true) }
    }

    override fun dismissDownloadQuickHint() {
        storage.write { putBoolean(PreferenceKeys.DOWNLOAD_QUICK_HINT_DISMISSED, true) }
    }

    override fun markPlayerGestureIntroShown() {
        storage.write { putBoolean(PreferenceKeys.PLAYER_GESTURE_INTRO_SHOWN, true) }
    }

    override fun weeklyReportDismissedWeek(): String = _weeklyReportDismissedWeek.value

    override fun setWeeklyReportDismissedWeek(weekKey: String) {
        storage.write { putString(PreferenceKeys.WEEKLY_REPORT_DISMISSED, weekKey) }
        _weeklyReportDismissedWeek.value = weekKey
    }

    private fun readPageLayoutModes(): PageLayoutModes {
        var snapshot = PageLayoutModes()
        var changed = false
        PageLayoutKey.entries.forEach { key ->
            val stored = PageLayoutMode.fromNameOrNull(
                storage.readString(PreferenceKeys.PAGE_LAYOUT_PREFIX + key.name, null)
            )
            if (stored != null) {
                snapshot = snapshot.copyOf(key, stored)
                changed = true
            }
        }
        // 布局记忆 v2 之前只有搜索页的旧字段；把它迁移进来，其余页保持未选择
        if (!changed) {
            SearchLayoutMode.fromNameOrNull(
                storage.readString(PreferenceKeys.SEARCH_LAYOUT_MODE, null)
            )?.let { legacy ->
                snapshot = snapshot.copyOf(PageLayoutKey.SEARCH, PageLayoutMode.valueOf(legacy.name))
                storage.write {
                    putString(PreferenceKeys.PAGE_LAYOUT_PREFIX + PageLayoutKey.SEARCH.name, legacy.name)
                }
            }
        }
        return snapshot
    }

    private fun PageLayoutModes.copyOf(page: PageLayoutKey, mode: PageLayoutMode?): PageLayoutModes =
        when (page) {
            PageLayoutKey.HOME -> copy(home = mode)
            PageLayoutKey.SEARCH -> copy(search = mode)
            PageLayoutKey.FAVORITE -> copy(favorite = mode)
            PageLayoutKey.HISTORY -> copy(history = mode)
            PageLayoutKey.SUBSCRIPTIONS -> copy(subscriptions = mode)
            PageLayoutKey.AUTHOR -> copy(author = mode)
        }

    private fun isLoginSupportedHost(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return host in LOGIN_SUPPORTED_DOMAINS
    }

    /**
     * 规范化为「https:// + host + 可选端口」，去掉路径（如镜像站的 /enter 入口），
     * 确保后续拼接 /search、/watch 等路径时不会产生 /enter/search 这类无效 URL。
     * 只接受 https：该地址会承载登录 Cookie 与视频防盗链 Referer。
     */
    private fun sanitizeBaseUrl(url: String): String? {
        val trimmed = url.trim().trimEnd('/')
        val host = HTTPS_PREFIX.find(trimmed)?.groupValues?.get(1) ?: return null
        if (!HOST_PATTERN.matches(host)) return null
        return "https://$host"
    }

    companion object {
        const val DEFAULT_BASE_URL = SettingsRepository.DEFAULT_BASE_URL
        const val MIN_PLAYBACK_SPEED = 0.25f
        const val MAX_PLAYBACK_SPEED = 3f
        const val DEFAULT_LONG_PRESS_SPEED = 2f
        const val MIN_LONG_PRESS_SPEED = 1.5f
        const val MAX_LONG_PRESS_SPEED = 3f

        private const val SECURE_STORE_WAIT_MS = 3_000L

        const val LANGUAGE_ZH_CN = SettingsRepository.LANGUAGE_ZH_CN
        const val LANGUAGE_ZH_TW = SettingsRepository.LANGUAGE_ZH_TW
        const val LANGUAGE_EN = SettingsRepository.LANGUAGE_EN
        const val LANGUAGE_JA = SettingsRepository.LANGUAGE_JA
        val SUPPORTED_LANGUAGES = listOf(LANGUAGE_ZH_CN, LANGUAGE_ZH_TW, LANGUAGE_EN, LANGUAGE_JA)

        private val HTTPS_PREFIX = Regex("^https://([^/]+)", RegexOption.IGNORE_CASE)
        private val HOST_PATTERN = Regex(
            "^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*(:\\d{1,5})?$"
        )
        private val LOGIN_SUPPORTED_DOMAINS = setOf("hanime1.me", "hanimeone.me")
    }
}
