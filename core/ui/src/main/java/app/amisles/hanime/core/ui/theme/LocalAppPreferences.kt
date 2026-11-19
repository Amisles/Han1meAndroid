package app.amisles.hanime.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import app.amisles.hanime.domain.preferences.AppPreferences

/**
 * 由 :app 注入；未注入时读取即抛错，便于尽早暴露装配遗漏。
 *
 * 使用 [staticCompositionLocalOf]：该值在应用生命周期内不变，读取方无需追踪其变化。
 */
val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    error("LocalAppPreferences 未注入：请在 HanimeApp 外层提供 AppPreferences 实现")
}