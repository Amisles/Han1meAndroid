package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.LauncherIcon

/** 桌面图标的当前值与切换。 */
interface AppIconRepository {

    /** 当前生效的图标；组件状态缺省时视为默认图标。 */
    fun current(): LauncherIcon

    /** 切换到 [icon]；返回 false 表示系统拒绝修改组件状态。 */
    fun select(icon: LauncherIcon): Boolean
}
