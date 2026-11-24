package app.amisles.hanime.data.preferences

/** 偏好键的唯一定义处：设置、会话与迁移共用，避免同一个字符串散落在多个存储实现里。 */
internal object PreferenceKeys {

    const val HOT_NAME = "hanime_hot_prefs"
    const val SECURE_NAME = "hanime_app_prefs"
    const val HOT_SPLIT_DONE = "hot_split_done"
    const val MIGRATED_TO_ENCRYPTED = "migrated_to_encrypted"

    const val ALREADY_LOGIN = "already_login"
    const val LOGIN_COOKIE = "login_cookie"
    const val LOGIN_COOKIE_DOMAIN = "login_cookie_domain"
    const val CF_COOKIE = "cloudflare_cookie"
    const val CF_COOKIE_DOMAIN = "cloudflare_cookie_domain"
    const val SAVED_USER_ID = "saved_user_id"
    const val SAVED_USER_NAME = "saved_user_name"

    const val MAX_DOWNLOAD_CONCURRENT = "max_download_concurrent"
    const val BASE_URL = "base_url"
    const val APP_LANGUAGE = "app_language"
    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val PRIVACY_MODE = "privacy_mode"
    const val PLAYBACK_SPEED = "playback_speed"
    const val PREFERRED_QUALITY = "preferred_quality"
    const val AUTO_PLAY_NEXT = "auto_play_next"
    const val AUTO_PLAY_RELATED = "auto_play_related"
    const val LOOP_PLAYBACK = "loop_playback"
    const val REALTIME_TRANSLATION = "realtime_translation"
    const val LONG_PRESS_SPEED = "long_press_boost_speed"
    const val DOUBLE_TAP_SEEK = "double_tap_seek"
    const val AUTO_QUALITY = "auto_quality"
    const val AUTO_ROTATE_FULLSCREEN = "auto_rotate_fullscreen"
    const val ORIENTATION_PINNED = "player_orientation_pinned"
    const val PLAYER_RESIZE_MODE = "player_resize_mode"
    const val PLAYER_LAYOUT = "player_layout"
    const val VIDEO_DETAIL_CACHE_LIMIT = "video_detail_cache_limit"
    const val PLAYLIST_CSRF_TOKEN = "playlist_csrf_token"
    const val PLAYLIST_MIGRATION_DONE = "playlist_migration_done"
    const val DOWNLOAD_STORAGE_PATH = "download_storage_path"
    const val DOWNLOAD_RATE_LIMIT = "download_rate_limit_mbps"
    const val DOWNLOAD_WIFI_ONLY = "download_wifi_only"
    const val DOWNLOAD_CHARGING_ONLY = "download_charging_only"
    const val DOWNLOAD_NIGHT_ONLY = "download_night_only"
    const val DOWNLOAD_QUEUE_ORDER = "download_queue_order"
    const val ACKNOWLEDGED_CRASH = "acknowledged_crash_file"
    const val NOTIFICATION_PROMPT_SHOWN = "notification_prompt_shown"
    const val SUBSCRIPTION_REMINDER = "subscription_reminder_enabled"
    const val SUBSCRIPTION_SEEN_IDS = "subscription_seen_video_ids"
    const val WATCH_STATS_ENABLED = "watch_stats_enabled"
    const val SUBSCRIPTIONS_HIDE_WATCHED = "subscriptions_hide_watched"
    const val WEEKLY_REPORT_DISMISSED = "weekly_report_dismissed_week"
    const val SEARCH_LAYOUT_MODE = "search_layout_mode"
    const val PAGE_LAYOUT_PREFIX = "page_layout_"
    const val RELATED_LAYOUT_MODE = "related_layout_mode"
    const val COMIC_READ_MODE = "comic_read_mode"
    const val COMIC_READER_BLACK = "comic_reader_black"
    const val SKIPPED_UPDATE_VERSION = "skipped_update_version"
    const val AUTO_CHECK_UPDATE = "auto_check_update"
    const val ONBOARDING_DONE = "onboarding_done"
    const val HOME_LONG_PRESS_HINT_DISMISSED = "home_long_press_hint_dismissed"
    const val DOWNLOAD_QUICK_HINT_DISMISSED = "download_quick_hint_dismissed"
    const val PLAYER_GESTURE_INTRO_SHOWN = "player_gesture_intro_shown"
    const val APP_LOCK_ENABLED = "app_lock_enabled"
    const val APP_LOCK_PIN_SALT = "app_lock_pin_salt"
    const val APP_LOCK_PIN_HASH = "app_lock_pin_hash"

    /** 只写入加密存储的键；其余键走热存储。 */
    val SESSION_KEYS = setOf(
        ALREADY_LOGIN,
        LOGIN_COOKIE,
        LOGIN_COOKIE_DOMAIN,
        CF_COOKIE,
        CF_COOKIE_DOMAIN,
        PLAYLIST_CSRF_TOKEN
    )

    /** 需要加密但不属于会话的键：PIN 校验值只存加盐哈希。 */
    val SECURE_KEYS = SESSION_KEYS + setOf(APP_LOCK_PIN_SALT, APP_LOCK_PIN_HASH)
}
