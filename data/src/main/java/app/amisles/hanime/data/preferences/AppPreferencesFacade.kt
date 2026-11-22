package app.amisles.hanime.data.preferences

import app.amisles.hanime.domain.preferences.AppPreferences
import app.amisles.hanime.domain.preferences.UiHintStore
import app.amisles.hanime.domain.repository.AppearanceSettings
import app.amisles.hanime.domain.repository.PlayerSettings
import app.amisles.hanime.domain.repository.ReadingSettings
import app.amisles.hanime.domain.repository.SiteSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/** Compose 层读取界面偏好的组合入口：会话流来自 [SessionStore]，其余设置来自 [SettingsStore]。 */
@Singleton
class AppPreferencesFacade @Inject constructor(
    settings: SettingsStore,
    session: SessionStore
) : AppPreferences,
    PlayerSettings by settings,
    ReadingSettings by settings,
    SiteSettings by settings,
    AppearanceSettings by settings {

    override val loginStateFlow: StateFlow<Boolean> = session.loginStateFlow
    override val activeLoginStateFlow: StateFlow<Boolean> = session.activeLoginStateFlow
    override val hints: UiHintStore = settings
}
