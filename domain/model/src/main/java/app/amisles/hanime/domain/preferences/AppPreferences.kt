package app.amisles.hanime.domain.preferences

import app.amisles.hanime.domain.remote.VideoAntiHotlink
import app.amisles.hanime.domain.repository.AppearanceSettings
import app.amisles.hanime.domain.repository.PlayerSettings
import app.amisles.hanime.domain.repository.ReadingSettings
import app.amisles.hanime.domain.repository.SiteSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Compose 层读取的界面偏好组合入口。
 *
 * 字段直接复用按域拆分的设置接口，避免同一批 StateFlow 在宽接口里再声明一遍；
 * 会话状态来自独立的 [app.amisles.hanime.domain.repository.SessionRepository]，
 * 因此仍由实现类组合，而不是把凭据塞进设置存储。
 */
interface AppPreferences : PlayerSettings, ReadingSettings, SiteSettings, AppearanceSettings {

    val loginStateFlow: StateFlow<Boolean>
    val activeLoginStateFlow: StateFlow<Boolean>

    /**
     * 当前站点应注入的防盗链 Referer（结尾带 /）。
     *
     * 必须是 getter：站点切换后旧域名会让 CDN 判为盗链。播放器与取帧预览都依赖它。
     */
    val antiHotlinkReferer: String
        get() = VideoAntiHotlink.refererOf(baseUrlFlow.value)

    /** 一次性提示的展示标记；界面只读它决定是否首次弹出。 */
    val hints: UiHintStore
}
