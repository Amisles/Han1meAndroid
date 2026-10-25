package app.amisles.hanime.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import app.amisles.hanime.domain.preferences.RelatedLayoutMode
import app.amisles.hanime.domain.preferences.SearchLayoutMode
import app.amisles.hanime.domain.remote.VideoAntiHotlink
import kotlinx.coroutines.flow.StateFlow

/**
 * Compose 层读取的「界面相关偏好」最小集合。
 *
 * ## 为什么需要它
 *
 * 详情页、播放器、搜索页等 Composable 直接读了 `Preferences.xxxFlow`，使
 * `:feature-*` 与 `:core:ui` 无法脱离 `:data` 单独存在，也让 UI 预览必须准备
 * 真实的加密存储。
 *
 * 这里只暴露**界面真正需要的**几个流，由 `:app` 把 `:data` 的实现适配进来；
 * `:core:ui` 本身不依赖 `:data`（UI 层不应反向依赖数据层）。
 *
 * 刻意不包含任何写入操作：写入一律经由 ViewModel 或注入的 SettingsRepository，
 * 避免 Composable 直接改持久化状态。
 */
interface AppPreferences {
    val loginStateFlow: StateFlow<Boolean>
    val loginSupportedFlow: StateFlow<Boolean>
    val playbackSpeedFlow: StateFlow<Float>
    val preferredQualityFlow: StateFlow<String>
    val autoPlayNextFlow: StateFlow<Boolean>
    val loopPlaybackFlow: StateFlow<Boolean>
    val searchLayoutModeFlow: StateFlow<SearchLayoutMode?>
    val relatedLayoutModeFlow: StateFlow<RelatedLayoutMode?>
    val longPressBoostSpeedFlow: StateFlow<Float>
    val doubleTapSeekFlow: StateFlow<Boolean>
    val autoQualityFlow: StateFlow<Boolean>
    val baseUrlFlow: StateFlow<String>

    // ── 写入 ────────────────────────────────────────────────────────────────
    // 播放器与搜索页需要就地改设置（例如倍速菜单、布局切换）。若把这些留在 Composable
    // 里直接调 Preferences，:feature-* 就仍要 import :data；因此写入也走本接口，
    // 由 :app 的适配层转发到 SettingsRepository。
    fun setPlaybackSpeed(speed: Float)
    fun setPreferredQuality(resolution: String)
    fun setAutoPlayNext(enabled: Boolean)
    fun setLoopPlayback(enabled: Boolean)
    fun setSearchLayoutMode(mode: SearchLayoutMode)
    fun setRelatedLayoutMode(mode: RelatedLayoutMode)

    /** 清除用户选择，恢复按推荐卡片形态取默认值。 */
    fun clearRelatedLayoutMode()
    fun setLongPressBoostSpeed(speed: Float)
    fun setDoubleTapSeek(enabled: Boolean)
    fun setAutoQuality(enabled: Boolean)

    /**
     * 当前站点应注入的防盗链 Referer（结尾带 /）。
     *
     * 由播放器在构建时取一次：直链请求必须带它，否则 CDN 会返回 403。
     * 放在这里而不是让播放器直接读设置 —— 播放器是无依赖的工具 object。
     */
    val antiHotlinkReferer: String
}

/**
 * 由 `:app` 在 `HanimeApp` 外层注入；未注入时读取即抛错，便于尽早暴露装配遗漏。
 *
 * 使用 [staticCompositionLocalOf]：该值在应用生命周期内不变，读取方无需追踪其变化。
 */
val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    error("LocalAppPreferences 未注入：请在 HanimeApp 外层提供 AppPreferences 实现")
}
