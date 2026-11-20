package app.amisles.hanime

import android.content.Context
import app.amisles.hanime.core.ui.theme.UiConfigStore

/**
 * 进程级设置的唯一初始化入口。
 *
 * 两套存储的分工：影响渲染的视觉参数（字号、间距、卡片样式、首页分区）归 [UiConfigStore]，
 * 行为 / 网络 / 权限类开关由 Hilt 注入的 SettingsStore 负责。新增设置项按此归属，不要两边各写一份。
 */
internal object AppSettingsInitializer {

    fun init(context: Context) {
        UiConfigStore.init(context)
    }
}
